'use strict';

// S3-25: campanita de avisos en el encabezado. Muestra lo que cada persona tiene pendiente y lleva
// directo a la práctica y pestaña donde se resuelve. Se actualiza al cambiar de página.
iconPaths.bell='<path d="M6 16V11a6 6 0 0 1 12 0v5l2 2H4z"/><path d="M10 20a2 2 0 0 0 4 0"/>';
const tonosAviso={danger:['alert','danger'],warn:['clock','warn'],info:['inbox','info']};
// El número rojo cuenta solo los avisos nuevos: al abrir la campanita se marcan como vistos (en este navegador).
// La lista sigue mostrando todo lo pendiente hasta que se resuelva; si aparece algo nuevo, el número vuelve.
let avisosActuales=[];
const claveVistos=()=>`avisosVistos:${state.usuario?.id||''}`;
const idAviso=a=>`${a.practica_id}|${a.pestana}|${a.texto}`;
function avisosVistos() { try { return new Set(JSON.parse(localStorage.getItem(claveVistos())||'[]')); } catch { return new Set(); } }
function marcarAvisosVistos() {
  try { localStorage.setItem(claveVistos(),JSON.stringify(avisosActuales.map(idAviso))); } catch { /* sin almacenamiento */ }
  pintarNumeroAvisos(false);
}
function pintarNumeroAvisos(marcarNuevos=true) {
  const vistos=avisosVistos();const nuevos=avisosActuales.filter(a=>!vistos.has(idAviso(a))).length;
  const badge=$('#bell-badge');badge.hidden=!nuevos;badge.textContent=nuevos>9?'9+':nuevos;
  $('#bell-button').setAttribute('aria-label',avisosActuales.length?`Avisos: ${avisosActuales.length} pendiente(s)${nuevos?`, ${nuevos} nuevo(s)`:''}`:'Avisos: no hay pendientes');
  if(marcarNuevos)document.querySelectorAll('#bell-menu .bell-item').forEach(b=>b.classList.toggle('nuevo',!vistos.has(b.dataset.aviso)));
}
function asegurarCampanita() {
  if($('#bell'))return;
  $('#help-button')?.insertAdjacentHTML('beforebegin',`<div class="bell" id="bell" hidden><button type="button" class="bell-button" id="bell-button" aria-haspopup="true" aria-expanded="false" aria-controls="bell-menu" aria-label="Avisos">${icon('bell',20)}<b class="bell-badge" id="bell-badge" hidden></b></button><div class="bell-menu" id="bell-menu" role="region" aria-label="Avisos" hidden></div></div>`);
}
async function actualizarAvisos() {
  asegurarCampanita();
  if(!state.usuario || document.body.classList.contains('signed-out')){$('#bell').hidden=true;cerrarAvisos();return;}
  $('#bell').hidden=false;
  let data;try{data=await api('/notificaciones');}catch{return;}
  avisosActuales=data.avisos;
  $('#bell-menu').innerHTML=`<div class="bell-head"><strong>Avisos</strong><small>${data.total?`${data.total} pendiente(s)`:'Todo al día'}</small></div>`+
    (data.avisos.length?`<ul class="bell-list">${data.avisos.map(a=>{const [ic,c]=tonosAviso[a.tono]||tonosAviso.info;return `<li><button type="button" class="bell-item" data-detail="${a.practica_id}" data-tab="${e(a.pestana)}" data-aviso="${e(idAviso(a))}"><span class="attention-icon ${c}">${icon(ic,18)}</span><span><strong>${e(a.texto)}</strong><small>${e(a.detalle)}</small></span></button></li>`;}).join('')}</ul>`
      :`<div class="bell-empty">${icon('check',28)}<p>No tienes nada pendiente.</p></div>`);
  pintarNumeroAvisos();
  // Si la campanita está abierta mientras se actualiza, lo que se ve queda como visto.
  if(!$('#bell-menu').hidden)marcarAvisosVistos();
}
function cerrarAvisos() { const m=$('#bell-menu');if(m && !m.hidden){m.hidden=true;$('#bell-button').setAttribute('aria-expanded','false');} }
pageHooks.push(()=>actualizarAvisos());
sessionHooks.push(()=>{asegurarCampanita();if(!state.usuario){$('#bell').hidden=true;cerrarAvisos();}});
document.addEventListener('click',ev=>{
  if(ev.target.closest('#bell-button')){const m=$('#bell-menu');m.hidden=!m.hidden;$('#bell-button').setAttribute('aria-expanded',String(!m.hidden));if(!m.hidden){m.querySelector('button')?.focus();marcarAvisosVistos();}return;}
  if(!ev.target.closest('#bell-menu') || ev.target.closest('.bell-item'))cerrarAvisos();
});
document.addEventListener('keydown',ev=>{if(ev.key==='Escape' && $('#bell-menu') && !$('#bell-menu').hidden){cerrarAvisos();$('#bell-button').focus();}});
