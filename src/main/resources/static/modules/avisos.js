'use strict';

// S3-25: campanita de avisos en el encabezado. Muestra lo que cada persona tiene pendiente y lleva
// directo a la práctica y pestaña donde se resuelve. Se actualiza al cambiar de página.
iconPaths.bell='<path d="M6 16V11a6 6 0 0 1 12 0v5l2 2H4z"/><path d="M10 20a2 2 0 0 0 4 0"/>';
const tonosAviso={danger:['alert','danger'],warn:['clock','warn'],info:['inbox','info']};
function asegurarCampanita() {
  if($('#bell'))return;
  $('#help-button')?.insertAdjacentHTML('beforebegin',`<div class="bell" id="bell" hidden><button type="button" class="bell-button" id="bell-button" aria-haspopup="true" aria-expanded="false" aria-controls="bell-menu" aria-label="Avisos">${icon('bell',20)}<b class="bell-badge" id="bell-badge" hidden></b></button><div class="bell-menu" id="bell-menu" role="region" aria-label="Avisos" hidden></div></div>`);
}
async function actualizarAvisos() {
  asegurarCampanita();
  if(!state.usuario || document.body.classList.contains('signed-out')){$('#bell').hidden=true;cerrarAvisos();return;}
  $('#bell').hidden=false;
  let data;try{data=await api('/notificaciones');}catch{return;}
  const badge=$('#bell-badge');badge.hidden=!data.total;badge.textContent=data.total>9?'9+':data.total;
  $('#bell-button').setAttribute('aria-label',data.total?`Avisos: ${data.total} pendiente(s)`:'Avisos: no hay pendientes');
  $('#bell-menu').innerHTML=`<div class="bell-head"><strong>Avisos</strong><small>${data.total?`${data.total} pendiente(s)`:'Todo al día'}</small></div>`+
    (data.avisos.length?`<ul class="bell-list">${data.avisos.map(a=>{const [ic,c]=tonosAviso[a.tono]||tonosAviso.info;return `<li><button type="button" class="bell-item" data-detail="${a.practica_id}" data-tab="${e(a.pestana)}"><span class="attention-icon ${c}">${icon(ic,18)}</span><span><strong>${e(a.texto)}</strong><small>${e(a.detalle)}</small></span></button></li>`;}).join('')}</ul>`
      :`<div class="bell-empty">${icon('check',28)}<p>No tienes nada pendiente.</p></div>`);
}
function cerrarAvisos() { const m=$('#bell-menu');if(m && !m.hidden){m.hidden=true;$('#bell-button').setAttribute('aria-expanded','false');} }
pageHooks.push(()=>actualizarAvisos());
sessionHooks.push(()=>{asegurarCampanita();if(!state.usuario){$('#bell').hidden=true;cerrarAvisos();}});
document.addEventListener('click',ev=>{
  if(ev.target.closest('#bell-button')){const m=$('#bell-menu');m.hidden=!m.hidden;$('#bell-button').setAttribute('aria-expanded',String(!m.hidden));if(!m.hidden)m.querySelector('button')?.focus();return;}
  if(!ev.target.closest('#bell-menu') || ev.target.closest('.bell-item'))cerrarAvisos();
});
document.addEventListener('keydown',ev=>{if(ev.key==='Escape' && $('#bell-menu') && !$('#bell-menu').hidden){cerrarAvisos();$('#bell-button').focus();}});
