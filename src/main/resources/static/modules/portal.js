'use strict';

// S2-04: panel personal del estudiante. Es su página de inicio al ingresar.
homePage=()=>role('ESTUDIANTE')?'mipanel':'resumen';

function deadlineText(r) {
  if(!r.fecha_limite)return 'Sin plazo';
  return r.vencido?`${e(r.fecha_limite)} <span class="pill danger">Vencido</span>`:e(r.fecha_limite);
}
registerPage('mipanel','Mi panel',['ESTUDIANTE'],async()=>{
  const data=await api('/mi-panel');const s=data.estudiante;
  const practicas=data.practicas.length?data.practicas.map(p=>card(`${p.asignatura} · ${p.anio}-${p.semestre}`,
    `<div class="meta"><div>Centro<strong>${e(p.centro||'Actividad universitaria')}</strong></div><div>Profesor responsable<strong>${e(p.profesor_responsable)}</strong></div></div>${progress(p)}`+
    (p.requisitos.length?`<div class="scroll"><table><thead><tr><th>REQUISITO</th><th>PLAZO</th><th>ESTADO</th></tr></thead><tbody>${p.requisitos.map(r=>`<tr><td>${e(r.nombre)}</td><td>${deadlineText(r)}</td><td>${typeof statusPill==='function'?statusPill(r.estado):e(r.estado)}</td></tr>`).join('')}</tbody></table></div>`:empty('Sin requisitos definidos','La coordinación aún no configura los requisitos de esta práctica.'))+
    button('Ver detalle y entregar documentos',`data-detail="${p.id}"`))).join(''):card('Mis prácticas',empty('Todavía no tienes prácticas registradas','La coordinación debe registrar tu práctica.'));
  const plazos=data.plazos.length?`<div class="scroll"><table><thead><tr><th>REQUISITO</th><th>ASIGNATURA</th><th>PLAZO</th></tr></thead><tbody>${data.plazos.map(r=>`<tr><td>${e(r.nombre)}</td><td>${e(r.asignatura)}</td><td>${deadlineText(r)}</td></tr>`).join('')}</tbody></table></div>`:empty('No tienes plazos pendientes','Revisa igualmente los requisitos sin fecha límite.');
  return heading(`Hola, ${s.nombres}`,'Aquí ves solo tus prácticas, tus documentos y tus plazos.')+card('Próximos plazos y atrasos',plazos)+practicas;
});
