'use strict';

// Dictado por voz: escribe lo que dices en los campos de texto largos (observaciones, comentarios, evaluaciones y preguntas).
// Usa el reconocimiento de voz del navegador (Chrome y Edge), sin costo. Si el navegador no lo ofrece, el botón no aparece.
const Reconocimiento=window.SpeechRecognition || window.webkitSpeechRecognition;
let dictado=null; // {rec, campo, boton, estado}

// Comandos de puntuación que se pueden decir en voz alta.
function puntuar(texto) {
  return texto
    .replace(/\s*\bpunto y aparte\b\s*/gi,'.\n').replace(/\s*\bnueva l[ií]nea\b\s*/gi,'\n')
    .replace(/\s*\bpunto y seguido\b/gi,'.').replace(/\s*\bpunto final\b/gi,'.')
    .replace(/\s*\bpunto y coma\b/gi,';').replace(/\s*\bdos puntos\b/gi,':').replace(/\s*\bcoma\b/gi,',')
    .replace(/\s*\bsigno de interrogaci[oó]n\b/gi,'?')
    .replace(/([.?!:;,])(?=[^\s\n])/g,'$1 ')
    .replace(/([.?!]\s+|\n)([a-záéíóúñ])/g,(m,a,b)=>a+b.toUpperCase());
}
function componer(base,dicho) {
  let texto=puntuar(dicho.trim());if(!texto)return base;
  if(!base.trim() || /[.?!\n]\s*$/.test(base))texto=texto.charAt(0).toUpperCase()+texto.slice(1);
  const unido=base+(base && !/\s$/.test(base)?' ':'')+texto;
  return unido;
}
function limitar(campo,valor) { const max=campo.maxLength>0?campo.maxLength:Infinity;return valor.length>max?valor.slice(0,max):valor; }

function detenerDictado() {
  if(!dictado)return;
  const actual=dictado;dictado=null;
  try { actual.rec.stop(); } catch { /* ya detenido */ }
  actual.boton.classList.remove('grabando');actual.boton.setAttribute('aria-pressed','false');
  actual.boton.setAttribute('aria-label','Dictar con la voz');actual.boton.title='Dictar con la voz';
  actual.estado.hidden=true;
}
function iniciarDictado(campo,boton) {
  detenerDictado();
  const rec=new Reconocimiento();
  rec.lang='es-CL';rec.continuous=true;rec.interimResults=true;
  const base=campo.value;let final='';
  const estado=boton.parentElement.querySelector('.dictado-estado');
  dictado={rec,campo,boton,estado};
  rec.onresult=ev=>{
    let provisional='';
    for(let i=ev.resultIndex;i<ev.results.length;i++){const r=ev.results[i];if(r.isFinal)final+=r[0].transcript+' ';else provisional+=r[0].transcript;}
    campo.value=limitar(campo,componer(base,final+provisional));
    campo.dispatchEvent(new Event('input',{bubbles:true}));
    estado.textContent=provisional?`Escuchando… «${provisional.trim()}»`:'Escuchando… habla con normalidad';
  };
  rec.onerror=ev=>{
    const mensajes={
      'not-allowed':'No hay permiso para usar el micrófono. Haz clic en el candado de la barra de direcciones, permite el micrófono y vuelve a intentarlo.',
      'service-not-allowed':'Tu navegador no permite el dictado en esta página. Prueba con Chrome o Edge.',
      'no-speech':'No escuché nada. Revisa que el micrófono esté conectado y vuelve a pulsar el botón.',
      'audio-capture':'No se encontró un micrófono. Conéctalo y vuelve a intentarlo.',
      'network':'El dictado necesita conexión a internet. Revisa tu conexión e inténtalo de nuevo.',
    };
    if(mensajes[ev.error])notice(mensajes[ev.error],true);
    detenerDictado();
  };
  rec.onend=()=>{ if(dictado?.rec===rec){campo.value=limitar(campo,componer(base,final));campo.dispatchEvent(new Event('input',{bubbles:true}));detenerDictado();} };
  try { rec.start(); } catch { notice('No se pudo iniciar el dictado. Inténtalo de nuevo.',true);dictado=null;return; }
  boton.classList.add('grabando');boton.setAttribute('aria-pressed','true');
  boton.setAttribute('aria-label','Detener dictado');boton.title='Detener dictado (Esc)';
  estado.textContent='Escuchando… habla con normalidad';estado.hidden=false;campo.focus();
}

// Agrega el botón de micrófono a los campos largos y a las preguntas del asistente.
function agregarMicrofonos(raiz) {
  if(!Reconocimiento || !raiz)return;
  raiz.querySelectorAll('textarea, input[name="pregunta"]').forEach(campo=>{
    if(campo.dataset.dictado)return;campo.dataset.dictado='1';
    const envoltura=document.createElement('span');envoltura.className=`dictado ${campo.tagName==='TEXTAREA'?'area':'linea'}`;
    campo.replaceWith(envoltura);envoltura.appendChild(campo);
    envoltura.insertAdjacentHTML('beforeend',`<button type="button" class="mic" aria-pressed="false" aria-label="Dictar con la voz" title="Dictar con la voz">${icon('mic',16)}</button><span class="dictado-estado" role="status" hidden></span>`);
  });
}
pageHooks.push(()=>{ if(dictado && !dictado.campo.isConnected)detenerDictado(); agregarMicrofonos($('#app')); });
sessionHooks.push(()=>{ if(!state.usuario)detenerDictado(); agregarMicrofonos($('#chat-widget')); });

document.addEventListener('click',ev=>{
  const b=ev.target.closest('button.mic');if(!b)return;
  ev.preventDefault();
  const campo=b.parentElement.querySelector('textarea, input');
  if(dictado?.boton===b)detenerDictado();else iniciarDictado(campo,b);
});
document.addEventListener('keydown',ev=>{ if(ev.key==='Escape' && dictado){ev.stopPropagation();detenerDictado();} },true);
// Si se envía el formulario mientras se dicta, se detiene primero.
document.addEventListener('submit',()=>detenerDictado(),true);
