'use strict';

// S2-18 a S2-20: asistente con respuestas basadas en material publicado, plazos personales e historial.
const answerLabels={MATERIAL:['Respuesta con fuentes','ok'],PLAZOS:['Tu seguimiento','review'],SIN_RESPALDO:['Sin respaldo suficiente','pending']};
function chatMessage(m) {
  const [label,css]=answerLabels[m.tipo]||['Respuesta',''];
  const sources=(m.fuentes||[]).length?`<div class="sources"><strong>Fuentes:</strong> ${m.fuentes.map(f=>f.numero?`[${f.numero}] ${e(f.titulo)} (fragmento ${f.fragmento})`:e(f.titulo)).join(' · ')}</div>`:'';
  return `<div class="message"><strong>Tú</strong>${m.creado_en?` <small>${e(dateText(m.creado_en))}</small>`:''}\n${e(m.pregunta)}</div><div class="message answer"><span class="pill ${css}">${label}</span>\n${e(m.respuesta)}${sources}</div>`;
}
registerPage('asistente','Asistente',allRoles,async()=>{
  const history=await api('/asistente/historial');
  const suggestions=role('ESTUDIANTE')?['¿Qué tengo pendiente?','¿Cuáles son mis plazos?','¿Cuál es la asistencia mínima?']:['¿Cuál es la asistencia mínima?','¿Cómo se entrega el informe final?'];
  const recent=history.slice().reverse().slice(0,10);
  return heading('Asistente de prácticas','Responde con el material publicado por tus profesores. Si no encuentra respaldo, te lo dice.')+
    `<div class="chat-layout"><div class="stack">${card('Preguntas sugeridas',`<div class="stack">${suggestions.map(s=>button(s,`type="button" data-action="ask-suggestion" data-question="${e(s)}"`,true)).join('')}</div>`)}${card('Tus consultas recientes',recent.length?`<div class="stack">${recent.map(m=>`<small>${e(m.pregunta)}</small>`).join('')}</div>`:empty('Aún no tienes consultas'))}</div>`+
    `<div>${card('Conversación',`<div class="messages" id="chat-messages">${history.length?history.map(chatMessage).join(''):empty('Haz tu primera pregunta','Solo tú puedes ver tu historial.')}</div><form id="chat-form">${field('Escribe tu pregunta','pregunta','text',true,1000)}${button('Preguntar','type="submit"')}<p class="hint">Asistente básico por palabras clave: no inventa respuestas. Verifica siempre la información importante con tu profesor.</p></form>`)}</div></div>`;
},()=>{
  const box=$('#chat-messages');if(box)box.scrollTop=box.scrollHeight;
  onForm('#chat-form',async f=>{await send('/asistente',{pregunta:f.elements.pregunta.value});await go('asistente');$('#chat-form input').focus();});
});
actions['ask-suggestion']=async t=>{await send('/asistente',{pregunta:t.dataset.question});await go('asistente');};

// Asistente flotante: disponible en todas las páginas sin salir de la que estás usando.
const widgetSuggestions=()=>role('ESTUDIANTE')?['¿Qué tengo pendiente?','¿Cuáles son mis plazos?','¿Cuál es la asistencia mínima?']:['¿Cuál es la asistencia mínima?','¿Cómo se entrega el informe final?'];
function widgetMessage(m) {
  const [label,css]=answerLabels[m.tipo]||['Respuesta',''];
  const sources=(m.fuentes||[]).length?`<div class="sources">Fuentes: ${m.fuentes.map(f=>f.numero?`[${f.numero}] ${e(f.titulo)}`:e(f.titulo)).join(' · ')}</div>`:'';
  return `<div class="bubble mine">${e(m.pregunta)}</div><div class="bubble"><span class="pill ${css}">${label}</span>${e(m.respuesta)}${sources}</div>`;
}
function ensureWidget() {
  if($('#chat-fab'))return;
  document.body.insertAdjacentHTML('beforeend',`<button type="button" id="chat-fab" class="chat-fab" aria-label="Abrir asistente" aria-expanded="false" aria-controls="chat-widget" hidden>${icon('chat',24)}</button>
  <section id="chat-widget" class="chat-widget" role="dialog" aria-label="Asistente de prácticas" hidden>
    <header class="chat-widget-head"><span class="chat-widget-icon">${icon('chat',20)}</span><div><strong>Asistente de prácticas</strong><small>Responde con el material publicado</small></div><button type="button" class="chat-close" data-widget-close aria-label="Cerrar asistente">${icon('close')}</button></header>
    <div class="chat-widget-body" id="widget-messages" aria-live="polite"></div>
    <div class="chat-widget-suggestions" id="widget-suggestions"></div>
    <form id="widget-form" class="chat-widget-form"><input name="pregunta" maxlength="1000" autocomplete="off" placeholder="Escribe tu pregunta…" aria-label="Escribe tu pregunta" required><button type="submit" class="chat-send" aria-label="Enviar pregunta">${icon('send')}</button></form>
    <button type="button" class="chat-widget-link" data-page="asistente">Ver historial completo ${icon('arrow',14)}</button>
  </section>`);
  $('#widget-form').addEventListener('submit',async event=>{
    event.preventDefault();const input=event.target.elements.pregunta;const pregunta=input.value.trim();if(!pregunta)return;
    input.value='';await askWidget(pregunta);input.focus();
  });
}
function scrollWidget() { const box=$('#widget-messages');box.scrollTop=box.scrollHeight; }
async function openWidget() {
  $('#chat-widget').hidden=false;$('#chat-fab').setAttribute('aria-expanded','true');$('#chat-fab').classList.add('open');
  $('#widget-suggestions').innerHTML=widgetSuggestions().map(s=>`<button type="button" class="chip" data-widget-question="${e(s)}">${e(s)}</button>`).join('');
  const box=$('#widget-messages');box.innerHTML='<div class="bubble">Cargando tus consultas…</div>';
  try {
    const history=(await api('/asistente/historial')).slice(-6);
    box.innerHTML=`<div class="bubble">Hola, ${e(firstNameOf(state.usuario?.nombre))}. Pregúntame por requisitos, plazos o el material publicado por tus profesores.</div>`+history.map(widgetMessage).join('');
  } catch(err) { box.innerHTML=`<div class="bubble">${e(err.message)}</div>`; }
  scrollWidget();$('#widget-form').elements.pregunta.focus();
}
function closeWidget() {
  const w=$('#chat-widget');if(!w || w.hidden)return;
  w.hidden=true;$('#chat-fab').setAttribute('aria-expanded','false');$('#chat-fab').classList.remove('open');
}
async function askWidget(pregunta) {
  const box=$('#widget-messages');
  box.insertAdjacentHTML('beforeend',`<div class="bubble mine">${e(pregunta)}</div><div class="bubble typing" id="widget-typing">Buscando en el material…</div>`);scrollWidget();
  try { const r=await send('/asistente',{pregunta});$('#widget-typing').remove();box.lastElementChild.remove();box.insertAdjacentHTML('beforeend',widgetMessage(r)); }
  catch(err) { $('#widget-typing').textContent=err.message; }
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
