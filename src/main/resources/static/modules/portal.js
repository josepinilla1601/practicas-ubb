'use strict';

// S2-04: panel personal del estudiante. Es su página de inicio al ingresar.
homePage=()=>role('ESTUDIANTE')?'mipanel':'resumen';

function deadlineText(r) {
  return plazoTexto(r.fecha_limite,r.vencido,r.estado==='PENDIENTE' || r.estado==='OBSERVADO');
}
// Orden de urgencia: atrasados, correcciones, pendientes por fecha, en revisión y aprobados al final.
const urgencia=r=>r.vencido?0:r.estado==='OBSERVADO'?1:r.estado==='PENDIENTE'?2:r.estado==='EN_REVISION'?3:4;
// Abre el seguimiento de la práctica con el formulario de entrega enfocado.
actions['go-upload']=async t=>{state.focusUpload=t.dataset.requisito||true;await go('detalle',t.dataset.id);};

registerPage('mipanel','Mi panel',['ESTUDIANTE'],async()=>{
  const data=await api('/mi-panel');const s=data.estudiante;
  const todos=data.practicas.flatMap(p=>p.requisitos.map(r=>({...r,practica:p})));
  const cuenta=estado=>todos.filter(r=>r.estado===estado).length;
  const vencidos=todos.filter(r=>r.vencido);
  const abierta=data.practicas.find(p=>p.requisitos.some(r=>r.estado!=='APROBADO'))||data.practicas[0];
  const entregar=abierta?button('Entregar documento',`type="button" data-action="go-upload" data-id="${abierta.id}"`):'';

  const aviso=vencidos.length?`<section class="attention-card"><div class="attention-head">${icon('alert',20)}<h2>Tienes ${vencidos.length} ${vencidos.length===1?'requisito fuera de plazo':'requisitos fuera de plazo'}</h2></div><ul class="attention-list">${vencidos.map(r=>`<li class="attention-item danger"><span class="attention-icon">${icon('clock',20)}</span><div class="attention-text"><strong>${e(r.nombre)}</strong><small>${r.estado==='OBSERVADO'?'Te pidieron una corrección':'Sin entregar'} · el plazo era el ${e(fechaCorta(r.fecha_limite))}</small></div><button type="button" class="button small" data-action="go-upload" data-id="${r.practica.id}" data-requisito="${r.id}">Entregar ahora ${icon('arrow',14)}</button></li>`).join('')}</ul><p class="attention-note">Si tuviste un problema para entregar a tiempo, conversa con tu profesor.</p></section>`:'';

  const resumen=`<div class="stats">${statTile('clipboard',cuenta('PENDIENTE'),'Por entregar','Requisitos que todavía no tienen ninguna entrega.')}${statTile('edit',cuenta('OBSERVADO'),'Correcciones solicitadas','Tu profesor revisó la entrega y te pidió cambios: sube una versión corregida.')}${statTile('inbox',cuenta('EN_REVISION'),'En revisión','Entregas que tu profesor todavía no revisa.')}${statTile('check',cuenta('APROBADO'),'Aprobados','Requisitos aprobados: ya no necesitas entregar nada más en ellos.')}</div>`;

  const atajos=quickActions([
    ['send','Entregar documento',abierta?`data-action="go-upload" data-id="${abierta.id}"`:'disabled'],
    ['chat','Preguntar al asistente','data-open-chat','A'],
    ['book','¿Qué tengo pendiente?','data-open-chat data-question="¿Qué tengo pendiente?"'],
    ['shield','Ayuda de esta página','data-open-help','?'],
  ]);

  const practicas=data.practicas.length?data.practicas.map(p=>card(`${p.asignatura} · ${p.anio}-${p.semestre}`,
    `<div class="practice-summary"><div class="meta"><div>Centro<strong>${e(p.centro||'Actividad universitaria')}</strong></div><div>Profesor responsable<strong>${e(p.profesor_responsable)}</strong></div></div><div class="practice-progress">${progress(p)}</div>${button('Ver detalle y entregar',`data-detail="${p.id}"`,true)}</div>`)).join(''):card('Mis prácticas',empty('Todavía no tienes prácticas registradas','La coordinación debe registrar tu práctica. Si crees que es un error, escríbele.'));

  const filas=todos.slice().sort((a,b)=>urgencia(a)-urgencia(b) || String(a.fecha_limite||'9999').localeCompare(String(b.fecha_limite||'9999')));
  const tabla=filas.length?`<div class="scroll"><table><thead><tr><th>REQUISITO</th><th>PLAZO</th><th>ESTADO</th><th></th></tr></thead><tbody>${filas.map(r=>`<tr class="${r.vencido?'row-alert':''}"><td>${e(r.nombre)}${data.practicas.length>1?`<small>${e(r.practica.asignatura)}</small>`:''}</td><td>${deadlineText(r)}</td><td>${typeof statusPill==='function'?statusPill(r.estado):e(r.estado)}</td><td>${r.estado==='APROBADO' || r.estado==='EN_REVISION'?'':button(r.estado==='OBSERVADO'?'Subir corrección':'Entregar',`type="button" data-action="go-upload" data-id="${r.practica.id}" data-requisito="${r.id}"`,true)}</td></tr>`).join('')}</tbody></table></div>`:empty('Sin requisitos definidos','La coordinación aún no configura los requisitos de tu práctica.');

  return heading(`Hola, ${s.nombres}`,'Aquí ves solo tus prácticas, tus documentos y tus plazos.',entregar)+aviso+resumen+atajos+practicas+card('Mis requisitos y plazos',tabla);
});

// Al llegar desde "Entregar", enfoca el formulario y deja elegido el requisito.
pageHooks.push(page=>{
  if(page!=='detalle' || !state.focusUpload)return;
  const form=$('#upload-form');const pedido=state.focusUpload;state.focusUpload=null;if(!form)return;
  if(pedido!==true && form.elements.requisitoId.querySelector(`option[value="${pedido}"]`))form.elements.requisitoId.value=pedido;
  form.scrollIntoView({behavior:'smooth',block:'center'});form.classList.add('highlight');
  (pedido===true?form.elements.requisitoId:form.elements.archivo).focus({preventScroll:true});
});
