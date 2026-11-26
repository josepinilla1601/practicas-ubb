'use strict';

// Selector de período (año y semestre) en el encabezado.
// Al elegir uno, las listas, indicadores, calendario, bitácora y exportaciones muestran solo ese semestre.
// La elección se recuerda en este navegador; "Todos los períodos" muestra todo, como antes.
state.periodo='';
state.periodos=[];
const clavePeriodo=()=>`periodo:${state.usuario?.id||''}`;
const textoPeriodo=p=>`${p.anio}-${p.semestre}`;

async function cargarPeriodos() {
  try { state.periodos=await api('/periodos/visibles'); } catch { state.periodos=[]; }
  let guardado='';
  try { guardado=localStorage.getItem(clavePeriodo())||''; } catch { guardado=''; }
  // Si el período guardado ya no tiene prácticas visibles, se vuelve a "Todos".
  state.periodo=state.periodos.some(p=>textoPeriodo(p)===guardado)?guardado:'';
  pintarSelectorPeriodo();
}

function pintarSelectorPeriodo() {
  let caja=$('#periodo-selector');
  const herramientas=document.querySelector('header .session-tools');
  if(!herramientas)return;
  if(!caja) {
    herramientas.insertAdjacentHTML('afterbegin',`<label id="periodo-selector" class="periodo-selector" title="Muestra solo las prácticas del semestre elegido">${icon('calendar',16)}<span class="sr-only">Período</span><select id="periodo-select" aria-label="Período que se muestra"></select></label>`);
    caja=$('#periodo-selector');
    $('#periodo-select').addEventListener('change',async ev=>{
      state.periodo=ev.target.value;
      try { state.periodo?localStorage.setItem(clavePeriodo(),state.periodo):localStorage.removeItem(clavePeriodo()); } catch { /* sin almacenamiento */ }
      caja.classList.toggle('activo',!!state.periodo);
      await load();
      // El seguimiento de una práctica no depende del período: se vuelve a la lista correspondiente.
      await go(state.page==='detalle' || state.page==='vistaprevia'?(role('ESTUDIANTE')?'mipanel':'practicas'):state.page);
      notice(state.periodo?`Mostrando solo el período ${state.periodo}.`:'Mostrando todos los períodos.');
    });
  }
  // Con un solo período no hace falta elegir: el selector se oculta.
  caja.hidden=!state.usuario || state.periodos.length<2;
  caja.classList.toggle('activo',!!state.periodo);
  $('#periodo-select').innerHTML=`<option value="">Todos los períodos</option>`+state.periodos.map(p=>{const v=textoPeriodo(p);return `<option value="${v}" ${v===state.periodo?'selected':''}>Semestre ${p.semestre} · ${p.anio}</option>`;}).join('');
}
sessionHooks.push(()=>{ if(!state.usuario){state.periodo='';state.periodos=[];} const caja=$('#periodo-selector'); if(caja)caja.hidden=!state.usuario || state.periodos.length<2; });
