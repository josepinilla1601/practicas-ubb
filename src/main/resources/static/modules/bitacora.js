'use strict';

// S3-01 a S3-03: bitácora de práctica (diario de campo).
// El estudiante registra cada jornada; el profesor de colegio la visa u observa; el profesor UBB deja retroalimentación.
const tiposJornada={OBSERVACION:'Observación',AYUDANTIA:'Ayudantía',CLASE:'Clase realizada',REUNION:'Reunión',OTRA:'Otra'};
const estadosJornada={REGISTRADA:['Por visar','review'],VISADA:['Visada','ok'],OBSERVADA:['Observada','danger']};
const horaCorta=h=>String(h??'').substring(0,5);
const horasTexto=minutos=>{const h=Number(minutos)/60;return `${h.toLocaleString('es-CL',{maximumFractionDigits:1})} h`;};

function resumenBitacora(r) {
  return `<div class="stats">${statTile('notebook',r.jornadas,'Jornadas registradas','Cada vez que fuiste al centro y la registraste.')}${statTile('clock',String(r.horas).replace('.',','),'Horas registradas','Suma de las horas de todas las jornadas.')}${statTile('check',String(r.horas_visadas).replace('.',','),'Horas visadas','Horas que el profesor de colegio ya confirmó.')}${statTile('alert',r.pendientes+r.observadas,'Por visar u observadas','Jornadas que esperan visado o que debes corregir.')}</div>`;
}
function tarjetaJornada(j,{conEstudiante=false}={}) {
  const [label,css]=estadosJornada[j.estado]||['',''];
  const propia=role('ESTUDIANTE','COORDINADOR') && j.estado!=='VISADA';
  const acciones=[];
  if(propia)acciones.push(button('Editar',`type="button" data-action="edit-jornada" data-id="${j.id}"`,true),button('Eliminar',`type="button" data-action="delete-jornada" data-id="${j.id}"`,true));
  if(role('PROFESOR_COLEGIO','COORDINADOR') && j.estado==='REGISTRADA')acciones.push(button('Visar',`type="button" data-action="visar-jornada" data-id="${j.id}"`),button('Observar',`type="button" data-action="observar-jornada" data-id="${j.id}"`,true));
  if(role('PROFESOR_UBB','COORDINADOR'))acciones.push(button(j.comentario_ubb?'Editar retroalimentación':'Retroalimentar',`type="button" data-action="comentar-jornada" data-id="${j.id}"`,true));
  return `<article class="jornada ${j.estado.toLowerCase()}">
    <header><div><strong>${e(fechaCorta(j.fecha))}</strong> · ${e(horaCorta(j.hora_inicio))}–${e(horaCorta(j.hora_fin))} <span class="muted">(${horasTexto(j.minutos)})</span>${conEstudiante?`<small>${e(j.estudiante)} · ${e(j.asignatura)}</small>`:''}</div><span class="pill ${css}">${label}</span></header>
    <p class="jornada-meta"><span class="pill">${e(tiposJornada[j.tipo]||j.tipo)}</span> ${e(j.curso)}</p>
    <p><strong>Actividades:</strong> ${e(j.actividades)}</p>
    ${j.reflexion?`<p><strong>Reflexión:</strong> ${e(j.reflexion)}</p>`:''}
    ${j.comentario_colegio?`<p class="jornada-nota colegio">${icon('school',16)} <span><strong>${e(j.visador||'Profesor de colegio')}:</strong> ${e(j.comentario_colegio)}</span></p>`:''}
    ${j.comentario_ubb?`<p class="jornada-nota ubb">${icon('chat',16)} <span><strong>${e(j.comentador||'Profesor UBB')}:</strong> ${e(j.comentario_ubb)}</span></p>`:''}
    ${acciones.length?`<div class="table-actions">${acciones.join('')}</div>`:''}
  </article>`;
}
function formularioJornada(j={}) {
  const hoy=new Date();const iso=`${hoy.getFullYear()}-${String(hoy.getMonth()+1).padStart(2,'0')}-${String(hoy.getDate()).padStart(2,'0')}`;
  return `<form id="jornada-form">${j.id?`<input type="hidden" name="id" value="${j.id}">`:''}
    <div class="row">${field('Fecha','fecha','date',true,10,String(j.fecha||iso).substring(0,10))}${field('Curso','curso','text',true,60,j.curso||'')}</div>
    <div class="row">${field('Hora de inicio','horaInicio','time',true,5,horaCorta(j.hora_inicio||'08:00'))}${field('Hora de término','horaFin','time',true,5,horaCorta(j.hora_fin||'10:00'))}</div>
    ${selectField('Tipo de actividad','tipo',Object.entries(tiposJornada),true,j.tipo||'CLASE')}
    <label class="field">¿Qué hiciste?${req}<textarea name="actividades" required maxlength="3000" placeholder="Ejemplo: realicé la clase de ecuaciones de primer grado con trabajo en parejas.">${e(j.actividades||'')}</textarea></label>
    <label class="field">Reflexión pedagógica<textarea name="reflexion" maxlength="3000" placeholder="¿Qué funcionó? ¿Qué cambiarías? ¿Qué aprendiste?">${e(j.reflexion||'')}</textarea></label>
    ${button(j.id?'Guardar cambios':'Registrar jornada','type="submit"')}${j.id?button('Cancelar','type="button" data-action="cancel-jornada"',true):''}
    <p class="hint">Puedes dictar con el micrófono. Tu profesor de colegio visará la jornada; mientras no la vise, puedes editarla.</p></form>`;
}

registerPage('bitacora','Bitácora',allRoles,async()=>{
  if(role('ESTUDIANTE')) {
    const practicas=state.practicas;
    if(!practicas.length)return heading('Mi bitácora','Registra cada jornada en tu centro de práctica.')+card('Bitácora',empty('Todavía no tienes prácticas registradas','La coordinación debe registrar tu práctica.'));
    state.bitacoraPractica=state.bitacoraPractica && practicas.some(p=>Number(p.id)===Number(state.bitacoraPractica))?state.bitacoraPractica:practicas[0].id;
    const data=await api(`/practicas/${state.bitacoraPractica}/bitacora`);state.bitacora=data.jornadas;
    const edit=data.jornadas.find(j=>Number(j.id)===Number(state.editJornada));
    const selector=practicas.length>1?`<form id="bitacora-practica" class="tools">${selectField('Práctica','practica',practicas.map(p=>[p.id,`${p.asignatura} · ${p.anio}-${p.semestre}`]),true,state.bitacoraPractica)}</form>`:'';
    return heading('Mi bitácora','Tu diario de campo: registra cada jornada en el centro, lo que hiciste y lo que aprendiste.')+selector+resumenBitacora(data.resumen)+
      `<div class="split">${card(edit?'Editar jornada':'Registrar una jornada',formularioJornada(edit))}${card('Mis jornadas',data.jornadas.length?`<div class="jornadas">${data.jornadas.map(j=>tarjetaJornada(j)).join('')}</div>`:empty('Todavía no registras jornadas','Registra tu primera jornada con el formulario.'))}</div>`;
  }
  const data=await api(`/bitacora${state.bitacoraPendientes?'?pendientes=true':''}`);state.bitacora=data.jornadas;
  const filtro=`<div class="chips" role="group" aria-label="Mostrar"><button type="button" class="chip-filter" data-action="bitacora-filtro" data-valor="" aria-pressed="${!state.bitacoraPendientes}">Todas</button><button type="button" class="chip-filter" data-action="bitacora-filtro" data-valor="1" aria-pressed="${!!state.bitacoraPendientes}">Solo por visar</button></div>`;
  return heading('Bitácoras de práctica',role('PROFESOR_COLEGIO')?'Revisa y visa las jornadas que registran tus practicantes.':'Jornadas registradas por los practicantes que supervisas. Puedes dejar retroalimentación.')+
    resumenBitacora(data.resumen)+card('Jornadas',filtro+(data.jornadas.length?`<div class="jornadas">${data.jornadas.map(j=>tarjetaJornada(j,{conEstudiante:true})).join('')}</div>`:empty(state.bitacoraPendientes?'No hay jornadas por visar':'Todavía no hay jornadas registradas','Cuando tus practicantes registren jornadas aparecerán aquí.')));
},()=>{
  $('#bitacora-practica')?.elements.practica.addEventListener('change',async ev=>{state.bitacoraPractica=ev.target.value;state.editJornada=null;await go('bitacora');});
  onForm('#jornada-form',async f=>{
    const d=formData(f);const id=d.id;delete d.id;
    const r=id?await send(`/bitacora/${id}`,d,'PUT'):await send(`/practicas/${state.bitacoraPractica}/bitacora`,d);
    state.editJornada=null;await go('bitacora');notice(r.mensaje);
  },'Guardando jornada…');
});
actions['bitacora-filtro']=async t=>{state.bitacoraPendientes=!!t.dataset.valor;await go('bitacora');};
actions['edit-jornada']=async t=>{state.editJornada=t.dataset.id;await go('bitacora');$('#jornada-form')?.scrollIntoView({block:'center'});};
actions['cancel-jornada']=async()=>{state.editJornada=null;await go('bitacora');};
actions['delete-jornada']=async t=>{
  if(!await confirmar({titulo:'¿Eliminar esta jornada?',mensaje:'Se quitará de tu bitácora y de tus horas registradas.',aceptar:'Eliminar jornada',peligro:true}))return;
  const r=await api(`/bitacora/${t.dataset.id}`,{method:'DELETE'});const x=r.eliminada;
  await go(state.page,state.detail?.practica?.id);
  notice(r.mensaje,false,{texto:'Deshacer',fn:async()=>{
    await send(`/practicas/${x.practica_id}/bitacora`,{fecha:String(x.fecha).substring(0,10),horaInicio:horaCorta(x.hora_inicio),horaFin:horaCorta(x.hora_fin),curso:x.curso,tipo:x.tipo,actividades:x.actividades,reflexion:x.reflexion});
    await go(state.page,state.detail?.practica?.id);notice('Jornada restaurada.');
  }});
};
actions['visar-jornada']=async t=>{
  if(!await confirmar({titulo:'¿Visar esta jornada?',mensaje:'Confirmas que la jornada ocurrió como está registrada. Ya no se podrá modificar.',aceptar:'Visar jornada'}))return;
  const r=await send(`/bitacora/${t.dataset.id}/visado`,{decision:'VISADA'});await go(state.page,state.detail?.practica?.id);notice(r.mensaje);
};
actions['observar-jornada']=async t=>{
  const comentario=await pedirTexto({titulo:'Observar jornada',mensaje:'El estudiante verá tu comentario y deberá corregir la jornada.',etiqueta:'¿Qué debe corregir?',aceptar:'Enviar observación'});
  if(comentario===null)return;
  const r=await send(`/bitacora/${t.dataset.id}/visado`,{decision:'OBSERVADA',comentario});await go(state.page,state.detail?.practica?.id);notice(r.mensaje);
};
actions['comentar-jornada']=async t=>{
  const j=(state.bitacora||[]).find(x=>Number(x.id)===Number(t.dataset.id));
  const comentario=await pedirTexto({titulo:'Retroalimentación',mensaje:'Comenta la reflexión o las actividades de esta jornada.',etiqueta:'Tu retroalimentación',aceptar:'Guardar',valor:j?.comentario_ubb||''});
  if(comentario===null)return;
  const r=await send(`/bitacora/${t.dataset.id}/comentario`,{comentario});await go(state.page,state.detail?.practica?.id);notice(r.mensaje);
};

// En el seguimiento de una práctica: resumen de la bitácora y sus jornadas.
detailSections.push(Object.assign(async data=>{
  const b=await api(`/practicas/${data.practica.id}/bitacora`);state.bitacora=b.jornadas;
  const r=b.resumen;
  const cuerpo=`<div class="grade-summary"><div><span>Jornadas</span><strong>${r.jornadas}</strong></div><div><span>Horas registradas / visadas</span><strong>${String(r.horas).replace('.',',')} / ${String(r.horas_visadas).replace('.',',')}</strong></div><div><span>Por visar u observadas</span><strong>${r.pendientes+r.observadas}</strong></div></div>`+
    (b.jornadas.length?`<div class="jornadas">${b.jornadas.slice(0,5).map(j=>tarjetaJornada(j)).join('')}</div>${b.jornadas.length>5?`<p class="hint">Se muestran las 5 más recientes.</p>`:''}`:empty('Sin jornadas registradas',role('ESTUDIANTE')?'Registra tu primera jornada desde "Bitácora" en el menú.':'El practicante aún no registra jornadas.'))+
    (role('ESTUDIANTE')?`<p>${button('Ir a mi bitácora','type="button" data-page="bitacora"',true)}</p>`:'');
  return card('Bitácora de práctica',cuerpo);
},{orden:40}));
