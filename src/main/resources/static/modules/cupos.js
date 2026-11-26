'use strict';

// Asignación de centros por cupos.
// Estudiante: en la pestaña Inscripción de su práctica marca hasta tres centros en orden de preferencia.
// Coordinación: en "Asignación de centros" define los cupos de cada centro por período y asigna (a mano o automáticamente).

// ---------- Preferencias del estudiante (seguimiento de la práctica) ----------
detailSections.push(Object.assign(async data=>{
  const p=data.practica;
  const r=await api(`/practicas/${p.id}/preferencias`);
  if(!r.requiereCentro)return '';
  if(r.centroAsignado)return card('Centro de práctica',`<p class="estado-centro ok">${icon('check',18)} <span>Centro asignado: <strong>${e(r.centroAsignado)}</strong></span></p>`+
    (coord()?`<p>${button('Deshacer asignación',`type="button" data-action="quitar-centro" data-id="${p.id}"`,true)}</p>`:''));
  const puedeElegir=role('ESTUDIANTE','COORDINADOR');
  if(!r.centros.length)return card('Centro de práctica',`<p class="estado-centro">${icon('clock',18)} <span>Tu centro está <strong>por asignar</strong>. La coordinación todavía no publica los cupos de los colegios para este período.</span></p>`);
  const opciones=r.centros.map(c=>[c.id,`${c.nombre} · ${c.comuna} (${c.libres} de ${c.cupos} cupos libres)`]);
  const elegido=n=>r.preferencias.find(x=>Number(x.prioridad)===n)?.centro_id||'';
  const resumen=r.preferencias.length?`<p class="estado-centro">${icon('clock',18)} <span>Tus preferencias están guardadas. Tu centro está <strong>por asignar</strong>; la coordinación lo asignará según los cupos.</span></p>`
    :`<p class="estado-centro pendiente">${icon('alert',18)} <span>Elige hasta tres colegios en orden de preferencia. La coordinación asigna según los cupos disponibles.</span></p>`;
  return card('Centro de práctica',resumen+(puedeElegir?`<form id="preferencias-form">${[1,2,3].map(n=>selectField(`${n}ª preferencia`,`pref${n}`,opciones,n===1,elegido(n))).join('')}${button(r.preferencias.length?'Actualizar preferencias':'Guardar preferencias','type="submit"')}<p class="hint">Mientras no tengas centro asignado puedes cambiar tus preferencias.</p></form>`:''));
},{orden:5,pestana:'inscripcion'}));
detailBindings.push(id=>onForm('#preferencias-form',async f=>{
  const centros=[1,2,3].map(n=>f.elements[`pref${n}`].value).filter(Boolean).map(Number);
  if(new Set(centros).size!==centros.length){marcarCampo(f.elements.pref2,'Elige colegios distintos en cada preferencia.');throw new Error('No repitas un colegio en tus preferencias.');}
  const r=await send(`/practicas/${id}/preferencias`,{centros},'PUT');
  await go('detalle',id);notice(r.mensaje);
},'Guardando preferencias…'));
actions['quitar-centro']=async t=>{
  if(!await confirmar({titulo:'¿Deshacer la asignación?',mensaje:'La práctica volverá a quedar "por asignar" y el cupo quedará libre.',aceptar:'Deshacer asignación'}))return;
  await api(`/practicas/${t.dataset.id}/centro`,{method:'DELETE'});await load();await go('detalle',t.dataset.id);notice('Asignación deshecha.');
};

// ---------- Página de la coordinación ----------
const periodoAsignacion=()=>{
  const elegido=state.asignacionPeriodo||state.periodo||(state.periodos?.[0]?`${state.periodos[0].anio}-${state.periodos[0].semestre}`:`${new Date().getFullYear()}-${new Date().getMonth()<6?1:2}`);
  const [anio,semestre]=elegido.split('-').map(Number);return {texto:elegido,anio,semestre};
};
registerPage('asignacion','Asignación de centros',['COORDINADOR'],async()=>{
  const per=periodoAsignacion();
  const data=await api(`/asignacion?anio=${per.anio}&semestre=${per.semestre}`);state.asignacion=data;
  const periodos=[...new Set([per.texto,...(state.periodos||[]).map(p=>`${p.anio}-${p.semestre}`)])].sort().reverse();
  const selector=`<form id="asignacion-periodo" class="tools">${selectField('Período','periodo',periodos.map(p=>[p,`Semestre ${p.split('-')[1]} · ${p.split('-')[0]}`]),true,per.texto)}</form>`;
  const libresTotal=data.centros.reduce((s,c)=>s+Number(c.libres),0);
  const conPref=data.pendientes.filter(p=>p.preferencias.length).length;
  const stats=`<div class="stats">${statTile('school',data.centros.length,'Centros con cupos','Colegios que ofrecen cupos en este período.')}${statTile('check',libresTotal,'Cupos libres','Cupos que aún no se asignan.')}${statTile('clock',data.pendientes.length,'Practicantes por asignar','Prácticas que requieren centro y aún no lo tienen.')}${statTile('alert',data.pendientes.length-conPref,'Sin preferencias','Estudiantes que todavía no eligen sus colegios.')}</div>`;
  const tablaCupos=`<div class="scroll"><table><thead><tr><th>CENTRO</th><th>CUPOS</th><th>OCUPADOS</th><th>LIBRES</th></tr></thead><tbody>${data.centros.map(c=>`<tr><td><strong>${e(c.nombre)}</strong><small>${e(c.comuna)}</small></td><td><form class="cupos-form" data-centro="${c.id}"><input type="number" name="cupos" min="${c.ocupados}" max="200" value="${c.cupos}" aria-label="Cupos de ${e(c.nombre)}">${button('Guardar','type="submit"',true)}</form></td><td>${c.ocupados}</td><td><span class="pill ${Number(c.libres)?'ok':'danger'}">${c.libres}</span></td></tr>`).join('')}</tbody></table></div>`;
  const sinCupos=data.todosLosCentros.filter(c=>!data.centros.some(x=>Number(x.id)===Number(c.id)));
  const agregar=sinCupos.length?`<form id="agregar-cupos" class="tools agregar-cupos">${selectField('Agregar centro','centroId',sinCupos.map(c=>[c.id,`${c.nombre} · ${c.comuna}`]))}${field('Cupos','cupos','number',true,3,'1')}${button('Agregar','type="submit"')}</form>`:'';
  const prefTexto=p=>p.preferencias.length?p.preferencias.map(x=>{const c=data.centros.find(y=>Number(y.id)===Number(x.centro_id));return `<span class="pref ${c&&Number(c.libres)?'':'llena'}">${x.prioridad}. ${e(x.nombre)}${c&&!Number(c.libres)?' (sin cupos)':''}</span>`;}).join(''):'<span class="muted">Aún no elige</span>';
  const opcionesAsignar=p=>{const pref=p.preferencias.map(x=>Number(x.centro_id));const libres=data.centros.filter(c=>Number(c.libres)>0);
    libres.sort((a,b)=>(pref.indexOf(Number(a.id))+1||9)-(pref.indexOf(Number(b.id))+1||9));
    return libres.map(c=>`<option value="${c.id}">${pref.includes(Number(c.id))?`${pref.indexOf(Number(c.id))+1}ª · `:''}${e(c.nombre)} (${c.libres} libres)</option>`).join('');};
  const tablaPendientes=data.pendientes.length?`<div class="scroll"><table><thead><tr><th>ESTUDIANTE</th><th>PREFERENCIAS</th><th>ASIGNAR</th></tr></thead><tbody>${data.pendientes.map(p=>`<tr><td><strong>${e(p.estudiante)}</strong><small>${e(p.asignatura)}</small></td><td><div class="prefs">${prefTexto(p)}</div></td><td>${libresTotal?`<form class="asignar-form" data-practica="${p.id}"><select name="centroId" aria-label="Centro para ${e(p.estudiante)}">${opcionesAsignar(p)}</select>${button('Asignar','type="submit"',true)}</form>`:'<span class="muted">No quedan cupos</span>'}</td></tr>`).join('')}</tbody></table></div>`
    :empty('Todos los practicantes tienen centro','No hay prácticas de este período esperando asignación.');
  const auto=data.pendientes.some(p=>p.preferencias.length)&&libresTotal?button('Asignar automáticamente','type="button" data-action="asignar-auto"'):'';
  return heading('Asignación de centros','Define cuántos cupos ofrece cada colegio y asigna a los practicantes según sus preferencias.')+selector+stats+
    card('Practicantes por asignar',`${auto?`<div class="asignar-auto">${auto}<p class="hint">Asigna a cada estudiante su primera preferencia con cupo libre, en el orden en que enviaron sus preferencias. Después puedes ajustar a mano.</p></div>`:''}${tablaPendientes}`)+card('Cupos por centro',(data.centros.length?tablaCupos:empty('Sin cupos definidos','Agrega los colegios que reciben practicantes este período.'))+agregar);
},()=>{
  $('#asignacion-periodo')?.elements.periodo.addEventListener('change',async ev=>{state.asignacionPeriodo=ev.target.value;await go('asignacion');});
  const per=periodoAsignacion();
  document.querySelectorAll('.cupos-form').forEach(f=>f.addEventListener('submit',async ev=>{
    ev.preventDefault();
    try { const r=await send('/asignacion/cupos',{centroId:Number(f.dataset.centro),anio:per.anio,semestre:per.semestre,cupos:Number(f.elements.cupos.value)},'PUT');await go('asignacion');notice(r.mensaje); }
    catch(err) { notice(err.message,true); }
  }));
  onForm('#agregar-cupos',async f=>{const r=await send('/asignacion/cupos',{centroId:Number(f.elements.centroId.value),anio:per.anio,semestre:per.semestre,cupos:Number(f.elements.cupos.value)},'PUT');await go('asignacion');notice(r.mensaje);});
  document.querySelectorAll('.asignar-form').forEach(f=>f.addEventListener('submit',async ev=>{
    ev.preventDefault();const practica=f.dataset.practica;
    try {
      const r=await send(`/practicas/${practica}/centro`,{centroId:Number(f.elements.centroId.value)});
      await load();await go('asignacion');
      notice(`${r.centro} asignado.`,false,{texto:'Deshacer',fn:async()=>{await api(`/practicas/${practica}/centro`,{method:'DELETE'});await load();await go('asignacion');notice('Asignación deshecha.');}});
    } catch(err) { notice(err.message,true); }
  }));
});
actions['asignar-auto']=async()=>{
  const per=periodoAsignacion();
  if(!await confirmar({titulo:'¿Asignar automáticamente?',mensaje:`Cada estudiante del período ${per.texto} recibirá su primera preferencia con cupo libre, en el orden en que enviaron sus preferencias. Después puedes ajustar a mano.`,aceptar:'Asignar'}))return;
  const r=await send('/asignacion/automatica',{anio:per.anio,semestre:per.semestre});
  await load();await go('asignacion');
  notice(`Se asignaron ${r.asignadas} practicante(s).${r.sinCupo?` ${r.sinCupo} no alcanzaron cupo en sus preferencias: asígnalos a mano.`:''}${r.sinPreferencias?` ${r.sinPreferencias} aún no eligen sus colegios.`:''}`);
};
