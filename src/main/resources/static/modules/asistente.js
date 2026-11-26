'use strict';

// S2-18 a S2-20: asistente con respuestas basadas en material publicado, plazos personales e historial.
// Con la clave de Gemini configurada, la IA redacta las respuestas y retroalimenta los trabajos de los estudiantes:
// primero hace preguntas de reflexión y, cuando el estudiante responde (u omite), entrega la retroalimentación.
const answerLabels={IA:['Respuesta con IA','ok'],MATERIAL:['Respuesta con fuentes','ok'],PLAZOS:['Tu seguimiento','review'],SIN_RESPALDO:['Sin respaldo suficiente','pending'],ARCHIVO:['Retroalimentación de tu trabajo','ok'],REFLEXION:['Preguntas para reflexionar','review'],AYUDA:['Cómo usar el asistente','review'],MATEMATICA:['Apoyo matemático con IA','ok'],PRACTICA:['Apoyo pedagógico con IA','ok'],FUERA_DE_TEMA:['Fuera de los temas de la práctica','pending']};
const conIA=tipo=>['IA','ARCHIVO','REFLEXION','MATEMATICA','PRACTICA'].includes(tipo);
const formatosExplicables='.pdf,.docx,.txt,.md';
const avisoPrivacidad='El documento se envía a Google Gemini solo para generar la retroalimentación y no se guarda en el sistema. Evita subir documentos con datos personales sensibles.';
let iaActiva=null;
// Trabajo que espera las respuestas de reflexión del estudiante (el archivo se vuelve a enviar para la retroalimentación).
let reflexion=null;
async function pedirRetroalimentacion(archivo,{pregunta='',etapa='PREGUNTAS',respuestas=''}={}) {
  if(archivo.size>10*1024*1024)throw new Error('El archivo supera el máximo de 10 MB.');
  const body=new FormData();body.append('archivo',archivo);body.append('etapa',etapa);
  if(pregunta)body.append('pregunta',pregunta);
  if(respuestas)body.append('respuestas',respuestas);
  const r=await api('/asistente/explicar',{method:'POST',body});
  reflexion=r.tipo==='REFLEXION'?{archivo,pregunta,preguntas:r.respuesta}:null;
  return r;
}
async function estadoIA() { if(iaActiva===null){try{iaActiva=(await api('/asistente/estado')).ia;}catch{iaActiva=false;}}return iaActiva; }
sessionHooks.push(()=>{if(!state.usuario){iaActiva=null;reflexion=null;}});

function fuentesTexto(m,completo) {
  if(!(m.fuentes||[]).length)return '';
  const fuente=f=>f.tipo==='WEB'&&f.url?`<a href="${e(f.url)}" target="_blank" rel="noopener noreferrer" title="Fuente de internet">🌐 ${e(f.titulo)}</a>`
    :f.numero?`[${f.numero}] ${e(f.titulo)}${completo&&f.fragmento?` (fragmento ${f.fragmento})`:''}`:e(f.titulo);
  return `<div class="sources">${completo?'<strong>Fuentes:</strong> ':'Fuentes: '}${m.fuentes.map(fuente).join(' · ')}</div>`;
}
function chatMessage(m) {
  const [label,css]=answerLabels[m.tipo]||['Respuesta',''];
  return `<div class="message"><strong>Tú</strong>${m.creado_en?` <small>${e(dateText(m.creado_en))}</small>`:''}\n${e(m.pregunta)}</div><div class="message answer"><span class="pill ${css}">${conIA(m.tipo)?icon('spark',12)+' ':''}${label}</span>\n${e(m.respuesta)}${fuentesTexto(m,true)}</div>`;
}
const sugerencias=()=>role('ESTUDIANTE')?['¿Qué tengo pendiente?','¿Cuál es la asistencia mínima?','Resuelve 2x + 5 = 17 paso a paso']:['¿Cuál es la asistencia mínima?','¿Cómo enseño fracciones equivalentes?'];

registerPage('asistente','Asistente',allRoles,async()=>{
  const [history,ia]=await Promise.all([api('/asistente/historial'),estadoIA()]);
  const recent=history.slice().reverse().slice(0,10);
  const explicar=!ia
    ?empty('Retroalimentación con IA no activada','Cuando la coordinación configure la clave de Gemini podrás subir tu trabajo y el asistente te dará retroalimentación.')
    :reflexion
    ?`<form id="reflection-form"><p class="muted">📄 <strong>${e(reflexion.archivo.name)}</strong></p><div class="note reflection-questions">${e(reflexion.preguntas)}</div><label class="field">Tus respuestas<textarea name="respuestas" maxlength="3000" rows="6" placeholder="Responde con tus palabras. Puedes dictar con el micrófono."></textarea><small>Con tus respuestas, la retroalimentación será más precisa.</small></label><div class="form-actions">${button('Enviar respuestas y ver retroalimentación','type="submit"')}${button('Omitir preguntas','type="button" data-action="skip-reflection"',true)}${button('Cancelar','type="button" data-action="cancel-reflection"',true)}</div></form>`
    :`<form id="explain-form"><p class="hint" style="margin-top:0">Sube un trabajo tuyo (planificación, informe, reflexión…). Primero te haré tres preguntas para que reflexiones sobre él y luego te daré la retroalimentación.</p><label class="field">Tu trabajo<input name="archivo" type="file" accept="${formatosExplicables}" required><small>PDF, Word (DOCX), TXT o MD. Máximo 10 MB.</small></label><label class="field">¿Algo en particular que quieras revisar? (opcional)<textarea name="pregunta" maxlength="1000" placeholder="Ejemplo: ¿Mi inicio de clase activa los conocimientos previos?"></textarea></label>${button('Comenzar revisión','type="submit"')}<p class="hint">${e(avisoPrivacidad)}</p></form>`;
  return heading('Asistente de prácticas',ia?'Responde con IA sobre tu práctica, la asignatura y la matemática, usando el material publicado por tus profesores e indicando las fuentes. También retroalimenta tus trabajos.':'Responde con el material publicado por tus profesores. Si no encuentra respaldo, te lo dice.',ia?`<span class="pill ok ai-badge">${icon('spark',14)} IA activada</span>`:'')+
    `<div class="chat-layout"><div class="stack">${card('Preguntas sugeridas',`<div class="stack">${sugerencias().map(s=>button(s,`type="button" data-action="ask-suggestion" data-question="${e(s)}"`,true)).join('')}</div>`)}${card('Tus consultas recientes',recent.length?`<div class="stack">${recent.map(m=>`<small>${e(m.pregunta)}</small>`).join('')}</div>`:empty('Aún no tienes consultas'))}</div>`+
    `<div>${card('Conversación',`<div class="messages" id="chat-messages">${history.length?history.map(chatMessage).join(''):empty('Haz tu primera pregunta','Solo tú puedes ver tu historial.')}</div><form id="chat-form">${field('Escribe tu pregunta','pregunta','text',true,1000)}${button('Preguntar','type="submit"')}<p class="hint">${ia?'Las respuestas se redactan con IA a partir del material publicado.':'Asistente por palabras clave: no inventa respuestas.'} Verifica siempre la información importante con tu profesor.</p></form>`)}${card('Retroalimentación de tu trabajo',explicar)}</div></div>`;
},()=>{
  const box=$('#chat-messages');if(box)box.scrollTop=box.scrollHeight;
  onForm('#chat-form',async f=>{await send('/asistente',{pregunta:f.elements.pregunta.value});await go('asistente');$('#chat-form input').focus();},'Consultando…');
  onForm('#explain-form',async f=>{
    const archivo=f.elements.archivo.files[0];
    if(!archivo){marcarCampo(f.elements.archivo,'Sube el trabajo que quieres que revise.');throw new Error('Falta el trabajo que quieres que revise.');}
    if(archivo.size>10*1024*1024){marcarCampo(f.elements.archivo,'El archivo pesa más de 10 MB.');throw new Error('El archivo supera el máximo de 10 MB.');}
    const r=await pedirRetroalimentacion(archivo,{pregunta:f.elements.pregunta.value.trim()});
    await go('asistente');
    if(r.tipo==='REFLEXION'){notice('Responde las preguntas para recibir tu retroalimentación.');$('#reflection-form textarea')?.focus();}
  },'Leyendo tu trabajo…');
  onForm('#reflection-form',async f=>{
    const respuestas=f.elements.respuestas.value.trim();
    if(!respuestas){marcarCampo(f.elements.respuestas,'Escribe tus respuestas o usa "Omitir preguntas".');throw new Error('Faltan tus respuestas.');}
    await enviarReflexion(respuestas);
  },'Preparando tu retroalimentación…');
});
async function enviarReflexion(respuestas) {
  const p=reflexion;if(!p)return;
  await pedirRetroalimentacion(p.archivo,{pregunta:p.pregunta,etapa:'RETROALIMENTACION',respuestas});
  reflexion=null;await go('asistente');notice('Retroalimentación lista: la verás al final de la conversación.');
}
actions['skip-reflection']=async()=>{
  const ok=await confirmar({titulo:'¿Omitir las preguntas?',mensaje:'Responderlas te ayuda a reflexionar y hace que la retroalimentación sea más precisa.',aceptar:'Omitir y ver retroalimentación'});
  if(ok)await enviarReflexion('');
};
actions['cancel-reflection']=async()=>{reflexion=null;await go('asistente');};
actions['ask-suggestion']=async t=>{await send('/asistente',{pregunta:t.dataset.question});await go('asistente');};

// Asistente flotante: disponible en todas las páginas sin salir de la que estás usando.
function widgetMessage(m) {
  const [label,css]=answerLabels[m.tipo]||['Respuesta',''];
  return `<div class="bubble mine">${e(m.pregunta)}</div><div class="bubble"><span class="pill ${css}">${conIA(m.tipo)?icon('spark',12)+' ':''}${label}</span>${e(m.respuesta)}${fuentesTexto(m,false)}</div>`;
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
    input.value='';
    if(reflexion){await reflexionWidget(pregunta);input.focus();return;}
    await askWidget(pregunta);input.focus();
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
    $('#widget-attach').hidden=!ia;$('#widget-sub').textContent=ia?'Con IA · solo temas de tu práctica':'Responde con el material publicado';
    box.innerHTML=`<div class="bubble">Hola, ${e(firstNameOf(state.usuario?.nombre))}. Pregúntame por requisitos, plazos, el material de tus profesores o dudas de matemática y de tu práctica.${ia?' Con el clip puedes adjuntar un trabajo tuyo y te daré retroalimentación.':''}</div>`+history.slice(-6).map(widgetMessage).join('');
    if(reflexion)modoReflexion(true);
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
  box.insertAdjacentHTML('beforeend',`<div class="bubble mine">📄 ${e(archivo.name)}${pregunta?` — ${e(pregunta)}`:''}</div><div class="bubble typing" id="widget-typing">Leyendo tu trabajo…</div>`);scrollWidget();
  try {
    const r=await pedirRetroalimentacion(archivo,{pregunta});
    $('#widget-typing').remove();box.lastElementChild.remove();box.insertAdjacentHTML('beforeend',widgetMessage(r));
    modoReflexion(r.tipo==='REFLEXION');
  } catch(err) { $('#widget-typing').textContent=err.message; }
  scrollWidget();
}
// Mientras hay preguntas de reflexión pendientes, lo que se escribe en el asistente son las respuestas.
function modoReflexion(activo) {
  const input=$('#widget-form').elements.pregunta;
  input.placeholder=activo?'Escribe tus respuestas a las preguntas…':'Escribe tu pregunta…';
  $('#widget-suggestions').innerHTML=activo
    ?'<button type="button" class="chip" data-widget-skip>Omitir preguntas y ver retroalimentación</button><button type="button" class="chip" data-widget-cancel>Cancelar revisión</button>'
    :sugerencias().map(s=>`<button type="button" class="chip" data-widget-question="${e(s)}">${e(s)}</button>`).join('');
}
async function reflexionWidget(respuestas) {
  const p=reflexion;if(!p)return;
  const box=$('#widget-messages');
  box.insertAdjacentHTML('beforeend',`<div class="bubble mine">${respuestas?e(respuestas):'<em>Omití las preguntas.</em>'}</div><div class="bubble typing" id="widget-typing">Preparando tu retroalimentación…</div>`);scrollWidget();
  try {
    const r=await pedirRetroalimentacion(p.archivo,{pregunta:p.pregunta,etapa:'RETROALIMENTACION',respuestas});
    const [label,css]=answerLabels[r.tipo]||['Respuesta',''];
    $('#widget-typing').outerHTML=`<div class="bubble"><span class="pill ${css}">${conIA(r.tipo)?icon('spark',12)+' ':''}${label}</span>${e(r.respuesta)}${fuentesTexto(r,false)}</div>`;
    modoReflexion(false);
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
  if(t?.hasAttribute('data-widget-skip')){await reflexionWidget('');return;}
  if(t?.hasAttribute('data-widget-cancel')){reflexion=null;modoReflexion(false);$('#widget-messages').insertAdjacentHTML('beforeend','<div class="bubble">Listo, cancelé la revisión. Puedes adjuntar otro trabajo cuando quieras.</div>');scrollWidget();return;}
  if(t?.dataset.page==='asistente')closeWidget();
});
document.addEventListener('keydown',event=>{if(event.key==='Escape' && $('#chat-widget') && !$('#chat-widget').hidden){closeWidget();$('#chat-fab').focus();}});
