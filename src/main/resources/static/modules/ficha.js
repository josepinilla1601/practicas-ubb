'use strict';

// S3-04 y S3-05: ficha de inscripción. Estudiante la completa y envía → profesor de colegio confirma → coordinación aprueba.
const estadosFicha={SIN_FICHA:['Sin completar','pending'],BORRADOR:['Borrador','pending'],ENVIADA:['Esperando al colegio','review'],CONFIRMADA:['Esperando a coordinación','review'],APROBADA:['Aprobada','ok'],DEVUELTA:['Devuelta para corregir','danger']};
function pasosFicha(f) {
  const orden=['ENVIADA','CONFIRMADA','APROBADA'];const actual=f.estado==='DEVUELTA'?-1:orden.indexOf(f.estado);
  const paso=(i,titulo,quien,fecha)=>`<li class="${actual>=i?'hecho':actual===i-1?'actual':''}"><span class="paso-num">${actual>=i?icon('check',14):i+1}</span><div><strong>${titulo}</strong><small>${e(quien)}${fecha?` · ${e(dateText(fecha))}`:''}</small></div></li>`;
  return `<ol class="pasos">${paso(0,'Completa y envía','Estudiante',f.enviada_en)}${paso(1,'Confirma','Profesor de colegio',f.confirmada_en)}${paso(2,'Aprueba','Coordinación',f.aprobada_en)}</ol>`;
}
detailSections.push(Object.assign(async data=>{
  const id=data.practica.id;const f=await api(`/practicas/${id}/ficha`);state.ficha=f;
  const [label,css]=estadosFicha[f.estado]||['',''];
  const editable=role('ESTUDIANTE','COORDINADOR') && ['SIN_FICHA','BORRADOR','DEVUELTA'].includes(f.estado);
  const datos=f.estado==='SIN_FICHA'?'':`<div class="meta ficha-datos"><div>Curso<strong>${e(f.curso)} · ${e(f.nivel)}</strong></div><div>Días y horario<strong>${e(f.dias)} · ${e(f.horario)}</strong></div><div>Período en el centro<strong>${e(fechaCorta(f.fecha_inicio))} al ${e(fechaCorta(f.fecha_termino))}</strong></div><div>Horas semanales<strong>${e(f.horas_semanales)} h</strong></div></div>${f.observaciones?`<p class="hint">${e(f.observaciones)}</p>`:''}`;
  const aviso=f.estado==='DEVUELTA'&&f.comentario?`<p class="jornada-nota colegio">${icon('alert',16)} <span><strong>Devuelta:</strong> ${e(f.comentario)}</span></p>`:'';
  const formulario=editable?`<form id="ficha-form">
      <div class="row">${field('Curso (por ejemplo, 7° básico A)','curso','text',true,60,f.curso||'')}${field('Nivel','nivel','text',true,60,f.nivel||'')}</div>
      <div class="row">${field('Días en el centro','dias','text',true,120,f.dias||'')}${field('Horario','horario','text',true,120,f.horario||'')}</div>
      <div class="row">${field('Fecha de inicio','fechaInicio','date',true,10,String(f.fecha_inicio||'').substring(0,10))}${field('Fecha de término','fechaTermino','date',true,10,String(f.fecha_termino||'').substring(0,10))}</div>
      <label class="field">Horas semanales${req}<input name="horasSemanales" type="number" min="1" max="45" required value="${e(f.horas_semanales||'')}"></label>
      <label class="field">Observaciones<textarea name="observaciones" maxlength="1000">${e(f.observaciones||'')}</textarea></label>
      ${button('Guardar borrador','type="submit"')}${f.estado!=='SIN_FICHA'?button('Enviar ficha',`type="button" data-action="enviar-ficha" data-id="${id}"`):''}
      <p class="hint">Guarda la ficha y luego envíala. Primero la confirma tu profesor de colegio y después la aprueba la coordinación.</p></form>`:'';
  const acciones=[];
  if(role('PROFESOR_COLEGIO','COORDINADOR') && f.estado==='ENVIADA')acciones.push(button('Confirmar ficha',`type="button" data-action="ficha-decision" data-accion="CONFIRMAR" data-id="${id}"`));
  if(coord() && f.estado==='CONFIRMADA')acciones.push(button('Aprobar ficha',`type="button" data-action="ficha-decision" data-accion="APROBAR" data-id="${id}"`));
  if((role('PROFESOR_COLEGIO') && f.estado==='ENVIADA') || (coord() && ['ENVIADA','CONFIRMADA'].includes(f.estado)))acciones.push(button('Devolver con comentario',`type="button" data-action="ficha-decision" data-accion="DEVOLVER" data-id="${id}"`,true));
  return card('Ficha de inscripción',`<div class="ficha-head"><span class="pill ${css}">${label}</span></div>${pasosFicha(f)}${aviso}${editable?'':datos}${formulario}${acciones.length?`<div class="table-actions">${acciones.join('')}</div>`:''}`);
},{orden:10,pestana:'inscripcion'}));
detailBindings.push(id=>onForm('#ficha-form',async f=>{
  const d=formData(f);d.horasSemanales=Number(d.horasSemanales);
  const r=await send(`/practicas/${id}/ficha`,d,'PUT');await go('detalle',id);notice(r.mensaje);
}));
actions['enviar-ficha']=async t=>{
  if(!await confirmar({titulo:'¿Enviar la ficha?',mensaje:'Ya no podrás modificarla mientras esté en revisión. Si hay que corregir algo, te la devolverán con un comentario.',aceptar:'Enviar ficha'}))return;
  const r=await send(`/practicas/${t.dataset.id}/ficha/enviar`,{});await go('detalle',t.dataset.id);notice(r.mensaje);
};
actions['ficha-decision']=async t=>{
  const accion=t.dataset.accion;let comentario='';
  if(accion==='DEVOLVER'){comentario=await pedirTexto({titulo:'Devolver la ficha',mensaje:'El estudiante verá tu comentario y podrá corregir la ficha.',etiqueta:'¿Qué debe corregir?',aceptar:'Devolver ficha'});if(comentario===null)return;}
  else if(!await confirmar({titulo:accion==='APROBAR'?'¿Aprobar la ficha?':'¿Confirmar la ficha?',mensaje:accion==='APROBAR'?'La práctica quedará inscrita con estos datos.':'Confirmas que el curso, los días y el horario son correctos. Luego la revisará la coordinación.',aceptar:accion==='APROBAR'?'Aprobar ficha':'Confirmar ficha'}))return;
  const r=await send(`/practicas/${t.dataset.id}/ficha/decision`,{accion,comentario});await go('detalle',t.dataset.id);notice(r.mensaje);
};
// En "Mi panel": aviso si la ficha está pendiente o fue devuelta.
pageHooks.push(async page=>{
  if(page!=='mipanel' || !role('ESTUDIANTE'))return;
  for(const p of state.practicas) {
    let f;try{f=await api(`/practicas/${p.id}/ficha`);}catch{continue;}
    if(!['SIN_FICHA','BORRADOR','DEVUELTA'].includes(f.estado) || state.page!=='mipanel')continue;
    const texto=f.estado==='DEVUELTA'?'Tu ficha de inscripción fue devuelta: revisa el comentario y corrígela.':'Completa y envía tu ficha de inscripción (curso, días, horario y fechas en el centro).';
    $('#app .heading')?.insertAdjacentHTML('afterend',`<section class="attention-card"><div class="attention-head">${icon('file',20)}<h2>Ficha de inscripción · ${e(p.asignatura)}</h2></div><ul class="attention-list"><li class="attention-item warn"><span class="attention-icon">${icon('edit',20)}</span><div class="attention-text"><strong>${e(texto)}</strong></div><button type="button" class="button small" data-detail="${p.id}" data-tab="inscripcion">Completar ficha ${icon('arrow',14)}</button></li></ul></section>`);
  }
});
