'use strict';

pages.estudiantes.render=()=>{
  const edit=state.estudiantes.find(s=>Number(s.id)===Number(state.editStudent));
  return heading('Estudiantes','Edita antecedentes sin perder las prácticas ni las entregas.')+
    `<div class="split">${card(edit?'Editar estudiante':'Registrar estudiante',`<form id="student-form">${studentFields(edit)}${button(edit?'Guardar cambios':'Registrar','type="submit"')}${edit?button('Cancelar','type="button" data-action="cancel-student"',true):''}<p class="hint">El correo de contacto no cambia el correo de acceso de la cuenta.</p></form>`)}${card('Estudiantes registrados',`<div class="scroll"><table><thead><tr><th>ESTUDIANTE</th><th>IDENTIFICADOR</th><th>ACCIÓN</th></tr></thead><tbody>${state.estudiantes.map(s=>`<tr><td>${e(s.nombres)} ${e(s.apellidos)}<small>${e(s.correo)}</small></td><td>${e(s.identificador)}</td><td>${button('Editar',`data-action="edit-student" data-id="${s.id}"`,true)}</td></tr>`).join('')}</tbody></table></div>`)}</div>`;
};
pages.estudiantes.bind=()=>onForm('#student-form',async f=>{await send('/estudiantes'+(state.editStudent?'/'+state.editStudent:''),formData(f),state.editStudent?'PUT':'POST');state.editStudent=null;await saved('estudiantes');});
actions['edit-student']=async t=>{state.editStudent=t.dataset.id;await go('estudiantes');$('#student-form input').focus();};
actions['cancel-student']=async()=>{state.editStudent=null;await go('estudiantes');};

pages.centros.render=()=>{
  const edit=state.centros.find(c=>Number(c.id)===Number(state.editCenter));
  return heading('Centros de práctica','Mantén sus antecedentes y contactos al día.')+
    `<div class="split">${card(edit?'Editar centro':'Registrar centro',`<form id="center-form">${centerFields(edit)}${button(edit?'Guardar cambios':'Registrar','type="submit"')}${edit?button('Cancelar','type="button" data-action="cancel-center"',true):''}</form>`)}${card('Centros registrados',`<div class="scroll"><table><thead><tr><th>CENTRO</th><th>CONTACTO</th><th>ACCIÓN</th></tr></thead><tbody>${state.centros.map(c=>`<tr><td>${e(c.nombre)}<small>${e(c.comuna)} · ${e(c.direccion)}</small></td><td>${e(c.contacto)}<small>${e(c.correo)} ${e(c.telefono)}</small></td><td>${button('Editar',`data-action="edit-center" data-id="${c.id}"`,true)}</td></tr>`).join('')}</tbody></table></div>`)}</div>`;
};
pages.centros.bind=()=>onForm('#center-form',async f=>{await send('/centros'+(state.editCenter?'/'+state.editCenter:''),formData(f),state.editCenter?'PUT':'POST');state.editCenter=null;await saved('centros');});
actions['edit-center']=async t=>{state.editCenter=t.dataset.id;await go('centros');$('#center-form input').focus();};
actions['cancel-center']=async()=>{state.editCenter=null;await go('centros');};

state.filters={};
pages.practicas.render=async()=>{
  const f=state.filters;const query=new URLSearchParams(Object.entries(f).filter(([,v])=>v!==''));
  const practicas=await api('/practicas?'+query);
  return heading('Prácticas y documentos','Combina filtros para localizar una práctica.')+
    card('Buscar prácticas',`<form id="filter-form" class="tools">${field('Estudiante o identificador','q','search',false,150,f.q)}${selectField('Asignatura','asignaturaId',options(state.asignaturas,a=>a.nombre),false,f.asignaturaId)}${field('Año','anio','number',false,4,f.anio)}${selectField('Semestre','semestre',[[1,'Primero'],[2,'Segundo']],false,f.semestre)}${button('Buscar','type="submit"')}${button('Limpiar','type="button" data-action="clear-filters"',true)}</form>${practicesTable(practicas)}`)+
    (coord()?practiceForm():'');
};
pages.practicas.bind=()=>{bindPracticeForm();onForm('#filter-form',async f=>{state.filters=formData(f);await go('practicas');});};
actions['clear-filters']=async()=>{state.filters={};await go('practicas');};
