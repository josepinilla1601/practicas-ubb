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
