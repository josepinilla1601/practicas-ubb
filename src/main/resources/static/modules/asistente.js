'use strict';

// S2-18 a S2-20: asistente con respuestas basadas en material publicado, plazos personales e historial.
// Con la clave de Gemini configurada, la IA redacta las respuestas y explica documentos.
const answerLabels={IA:['Respuesta con IA','ok'],MATERIAL:['Respuesta con fuentes','ok'],PLAZOS:['Tu seguimiento','review'],SIN_RESPALDO:['Sin respaldo suficiente','pending'],ARCHIVO:['Explicación del documento','review'],AYUDA:['Cómo usar el asistente','review']};
const formatosExplicables='.pdf,.docx,.txt,.md';
const avisoPrivacidad='El documento se envía a Google Gemini solo para generar la explicación y no se guarda en el sistema. Evita subir documentos con datos personales sensibles.';
let iaActiva=null;
async function estadoIA() { if(iaActiva===null){try{iaActiva=(await api('/asistente/estado')).ia;}catch{iaActiva=false;}}return iaActiva; }
sessionHooks.push(()=>{if(!state.usuario)iaActiva=null;});

function fuentesTexto(m,completo) {
  if(!(m.fuentes||[]).length)return '';
  return `<div class="sources">${completo?'<strong>Fuentes:</strong> ':'Fuentes: '}${m.fuentes.map(f=>f.numero?`[${f.numero}] ${e(f.titulo)}${completo&&f.fragmento?` (fragmento ${f.fragmento})`:''}`:e(f.titulo)).join(' · ')}</div>`;
}
function chatMessage(m) {
  const [label,css]=answerLabels[m.tipo]||['Respuesta',''];
  return `<div class="message"><strong>Tú</strong>${m.creado_en?` <small>${e(dateText(m.creado_en))}</small>`:''}\n${e(m.pregunta)}</div><div class="message answer"><span class="pill ${css}">${m.tipo==='IA'||m.tipo==='ARCHIVO'?icon('spark',12)+' ':''}${label}</span>\n${e(m.respuesta)}${fuentesTexto(m,true)}</div>`;
}
const sugerencias=()=>role('ESTUDIANTE')?['¿Qué tengo pendiente?','¿Cuáles son mis plazos?','¿Cuál es la asistencia mínima?']:['¿Cuál es la asistencia mínima?','¿Cómo se entrega el informe final?'];

registerPage('asistente','Asistente',allRoles,async()=>{
  const [history,ia]=await Promise.all([api('/asistente/historial'),estadoIA()]);
  const materiales=ia?await api('/asistente/materiales'):[];
  const recent=history.slice().reverse().slice(0,10);
  const explicar=ia
    ?`<form id="explain-form">${selectField('Material publicado (opcional)','materialId',materiales.map(m=>[m.id,`${m.titulo} · ${m.asignatura}`]),false)}<label class="field">O sube tu propio archivo<input name="archivo" type="file" accept="${formatosExplicables}"><small>PDF, Word (DOCX), TXT o MD. Máximo 10 MB.</small></label><label class="field">¿Qué quieres saber? (opcional)<textarea name="pregunta" maxlength="1000" placeholder="Ejemplo: ¿Qué tengo que entregar y cuándo?"></textarea></label>${button('Explicar con IA','type="submit"')}<p class="hint">${e(avisoPrivacidad)}</p></form>`
    :empty('Explicación con IA no activada','Cuando la coordinación configure la clave de Gemini podrás subir un documento y el asistente te lo explicará.');
  return heading('Asistente de prácticas',ia?'Responde con IA usando el material publicado por tus profesores, y siempre indica la fuente. Si no encuentra respaldo, te lo dice.':'Responde con el material publicado por tus profesores. Si no encuentra respaldo, te lo dice.',ia?`<span class="pill ok ai-badge">${icon('spark',14)} IA activada</span>`:'')+
    `<div class="chat-layout"><div class="stack">${card('Preguntas sugeridas',`<div class="stack">${sugerencias().map(s=>button(s,`type="button" data-action="ask-suggestion" data-question="${e(s)}"`,true)).join('')}</div>`)}${card('Tus consultas recientes',recent.length?`<div class="stack">${recent.map(m=>`<small>${e(m.pregunta)}</small>`).join('')}</div>`:empty('Aún no tienes consultas'))}</div>`+
    `<div>${card('Conversación',`<div class="messages" id="chat-messages">${history.length?history.map(chatMessage).join(''):empty('Haz tu primera pregunta','Solo tú puedes ver tu historial.')}</div><form id="chat-form">${field('Escribe tu pregunta','pregunta','text',true,1000)}${button('Preguntar','type="submit"')}<p class="hint">${ia?'Las respuestas se redactan con IA a partir del material publicado.':'Asistente por palabras clave: no inventa respuestas.'} Verifica siempre la información importante con tu profesor.</p></form>`)}${card('Explicar un documento',explicar)}</div></div>`;
},()=>{
  const box=$('#chat-messages');if(box)box.scrollTop=box.scrollHeight;
  onForm('#chat-form',async f=>{await send('/asistente',{pregunta:f.elements.pregunta.value});await go('asistente');$('#chat-form input').focus();},'Consultando…');
  onForm('#explain-form',async f=>{
    const archivo=f.elements.archivo.files[0];
    if(!archivo && !f.elements.materialId.value){marcarCampo(f.elements.archivo,'Elige un material de la lista o sube un archivo.');throw new Error('Falta el documento que quieres que te explique.');}
    if(archivo && archivo.size>10*1024*1024){marcarCampo(f.elements.archivo,'El archivo pesa más de 10 MB.');throw new Error('El archivo supera el máximo de 10 MB.');}
    const body=new FormData();
    if(archivo)body.append('archivo',archivo);else body.append('materialId',f.elements.materialId.value);
    if(f.elements.pregunta.value.trim())body.append('pregunta',f.elements.pregunta.value.trim());
    await api('/asistente/explicar',{method:'POST',body});await go('asistente');notice('Explicación lista: la verás al final de la conversación.');
  },'Leyendo el documento…');
});
actions['ask-suggestion']=async t=>{await send('/asistente',{pregunta:t.dataset.question});await go('asistente');};

// Asistente flotante: disponible en todas las páginas sin salir de la que estás usando.
function widgetMessage(m) {
  const [label,css]=answerLabels[m.tipo]||['Respuesta',''];
  return `<div class="bubble mine">${e(m.pregunta)}</div><div class="bubble"><span class="pill ${css}">${m.tipo==='IA'||m.tipo==='ARCHIVO'?icon('spark',12)+' ':''}${label}</span>${e(m.respuesta)}${fuentesTexto(m,false)}</div>`;
}
function ensureWidget() {
  if($('#chat-fab'))return;
  document.body.insertAdjacentHTML('beforeend',`<button type="button" id="chat-fab" class="chat-fab" aria-label="Abrir asistente (tecla A)" title="Asistente (tecla A)" aria-expanded="false" aria-controls="chat-widget" hidden>${icon('chat',24)}</button>
  <section id="chat-widget" class="chat-widget" role="dialog" aria-label="Asistente de prácticas" hidden>
    <header class="chat-widget-head"><span class="chat-widget-icon">${icon('chat',20)}</span><div><strong>Asistente de prácticas</strong><small id="widget-sub">Responde con el material publicado</small></div><button type="button" class="chat-close" data-widget-close aria-label="Cerrar asistente">${icon('close')}</button></header>
    <div class="chat-widget-body" id="widget-messages" aria-live="polite"></div>
    <div class="chat-widget-suggestions" id="widget-suggestions"></div>
    <form id="widget-form" class="chat-widget-form"><button type="button" class="chat-attach" id="widget-attach" hidden aria-label="Adjuntar un documento para que la IA lo explique" title="Adjuntar documento (PDF, Word, TXT o MD)">${icon('clip')}</button><input type="file" id="widget-file" accept="${formatosExplicables}" hidden><input name="pregunta" maxlength="1000" autocomplete="off" placeholder="Escribe tu pregunta…" aria-label="Escribe tu pregunta" required><button type="submit" class="chat-send" aria-label="Enviar pregunta">${icon('send')}</button></form>
    <button type="button" class="chat-widget-link" data-page="asistente">Ver historial completo ${icon('arrow',14)}</button>
  </section>`);
  $('#widget-form').addEventListener('submit',async event=>{
    event.preventDefault();const input=event.target.elements.pregunta;const pregunta=input.value.trim();
    if(!pregunta){input.focus();return;}
    input.value='';await askWidget(pregunta);input.focus();
  });
  $('#widget-attach').addEventListener('click',()=>$('#widget-file').click());
  $('#widget-file').addEventListener('change',async ev=>{
    const archivo=ev.target.files[0];ev.target.value='';if(!archivo)return;
    const input=$('#widget-form').elements.pregunta;const pregunta=input.value.trim();input.value='';
    await explainWidget(archivo,pregunta);
  });
}
function scrollWidget() { const box=$('#widget-messages');box.scrollTop=box.scrollHeight; }
async function openWidget() {
  $('#chat-widget').hidden=false;$('#chat-fab').setAttribute('aria-expanded','true');$('#chat-fab').classList.add('open');
  $('#widget-suggestions').innerHTML=sugerencias().map(s=>`<button type="button" class="chip" data-widget-question="${e(s)}">${e(s)}</button>`).join('');
  const box=$('#widget-messages');box.innerHTML='<div class="bubble typing">Cargando tus consultas…</div>';
  try {
    const [history,ia]=await Promise.all([api('/asistente/historial'),estadoIA()]);
    $('#widget-attach').hidden=!ia;$('#widget-sub').textContent=ia?'Con IA · responde con el material publicado':'Responde con el material publicado';
    box.innerHTML=`<div class="bubble">Hola, ${e(firstNameOf(state.usuario?.nombre))}. Pregúntame por requisitos, plazos o el material publicado por tus profesores.${ia?' También puedes adjuntar un documento con el clip y te lo explico.':''}</div>`+history.slice(-6).map(widgetMessage).join('');
  } catch(err) { box.innerHTML=`<div class="bubble">${e(err.message)}</div>`; }
  scrollWidget();$('#widget-form').elements.pregunta.focus();
}
function closeWidget() {
  const w=$('#chat-widget');if(!w || w.hidden)return;
  w.hidden=true;$('#chat-fab').setAttribute('aria-expanded','false');$('#chat-fab').classList.remove('open');
}
async function askWidget(pregunta) {
  const box=$('#widget-messages');
  box.insertAdjacentHTML('beforeend',`<div class="bubble mine">${e(pregunta)}</div><div class="bubble typing" id="widget-typing">${iaActiva?'Pensando la respuesta…':'Buscando en el material…'}</div>`);scrollWidget();
  try { const r=await send('/asistente',{pregunta});$('#widget-typing').remove();box.lastElementChild.remove();box.insertAdjacentHTML('beforeend',widgetMessage(r)); }
  catch(err) { $('#widget-typing').textContent=err.message; }
  scrollWidget();
}
async function explainWidget(archivo,pregunta) {
  const box=$('#widget-messages');
  box.insertAdjacentHTML('beforeend',`<div class="bubble mine">📄 ${e(archivo.name)}${pregunta?` — ${e(pregunta)}`:''}</div><div class="bubble typing" id="widget-typing">Leyendo el documento…</div>`);scrollWidget();
  try {
    if(archivo.size>10*1024*1024)throw new Error('El archivo supera el máximo de 10 MB.');
    const body=new FormData();body.append('archivo',archivo);if(pregunta)body.append('pregunta',pregunta);
    const r=await api('/asistente/explicar',{method:'POST',body});
    $('#widget-typing').remove();box.lastElementChild.remove();box.insertAdjacentHTML('beforeend',widgetMessage(r));
  } catch(err) { $('#widget-typing').textContent=err.message; }
  scrollWidget();
}
const firstNameOf=name=>String(name||'').trim().split(/\s+/)[0]||'';
// Se muestra con sesión iniciada, salvo en la página completa del asistente.
sessionHooks.push(()=>{
  ensureWidget();
  const visible=!!state.usuario && state.page!=='asistente' && !document.body.classList.contains('signed-out');
  $('#chat-fab').hidden=!visible;
  if(!visible)closeWidget();
});
document.addEventListener('click',async event=>{
  const t=event.target.closest('button');
  if(t?.id==='chat-fab'){$('#chat-widget').hidden?await openWidget():closeWidget();return;}
  if(t?.hasAttribute('data-widget-close')){closeWidget();$('#chat-fab').focus();return;}
  if(t?.dataset.widgetQuestion){await askWidget(t.dataset.widgetQuestion);return;}
  if(t?.dataset.page==='asistente')closeWidget();
});
document.addEventListener('keydown',event=>{if(event.key==='Escape' && $('#chat-widget') && !$('#chat-widget').hidden){closeWidget();$('#chat-fab').focus();}});
