'use strict';

state.templateFilter={anio:new Date().getFullYear(),semestre:2};
registerPage('plantillas','Requisitos y períodos',['COORDINADOR'],async()=>{
  const f=state.templateFilter;f.asignaturaId=f.asignaturaId||state.asignaturas[0]?.id;
  state.templates=f.asignaturaId?await api('/plantillas?'+new URLSearchParams(f)):[];
  const edit=state.templates.find(t=>Number(t.id)===Number(state.editTemplate));
  return heading('Requisitos por asignatura y período','Los cambios se aplican a nuevas prácticas; las existentes conservan su lista.')+
    card('Seleccionar período',`<form id="template-filter" class="tools">${selectField('Asignatura','asignaturaId',options(state.asignaturas,a=>a.nombre),true,f.asignaturaId)}${field('Año','anio','number',true,4,f.anio)}${selectField('Semestre','semestre',[[1,'Primero'],[2,'Segundo']],true,f.semestre)}${button('Consultar','type="submit"')}</form>`)+
    `<div class="split">${card(edit?'Editar requisito':'Agregar requisito',`<form id="template-form">${field('Código único (letras, números, guion)','codigo','text',true,60,edit?.codigo)}${field('Nombre','nombre','text',true,180,edit?.nombre)}${field('Fecha límite opcional','fechaLimite','date',false,10,edit?.fecha_limite)}${button('Guardar requisito','type="submit"')}${edit?button('Cancelar','type="button" data-action="cancel-template"',true):''}</form>`)}${card('Plantilla actual',state.templates.length?`<div class="scroll"><table><thead><tr><th>REQUISITO</th><th>PLAZO</th><th>ACCIÓN</th></tr></thead><tbody>${state.templates.map(t=>`<tr><td>${e(t.nombre)}<small>${e(t.codigo)}</small></td><td>${e(t.fecha_limite||'Sin plazo')}</td><td>${button('Editar',`data-action="edit-template" data-id="${t.id}"`,true)}</td></tr>`).join('')}</tbody></table></div>`:empty('Sin requisitos','Agrega el primero para este período.'))}</div>`;
},()=>{
  onForm('#template-filter',async f=>{state.templateFilter=formData(f);state.editTemplate=null;await go('plantillas');});
  onForm('#template-form',async f=>{const d={...state.templateFilter,...formData(f)};for(const key of ['asignaturaId','anio','semestre'])d[key]=Number(d[key]);d.fechaLimite=d.fechaLimite||null;await send('/plantillas'+(state.editTemplate?'/'+state.editTemplate:''),d,state.editTemplate?'PUT':'POST');state.editTemplate=null;await saved('plantillas');});
});
actions['edit-template']=async t=>{state.editTemplate=t.dataset.id;await go('plantillas');};
actions['cancel-template']=async()=>{state.editTemplate=null;await go('plantillas');};
actions['configure-practice']=async t=>{await send(`/practicas/${t.dataset.id}/requisitos/configurar`,{});await saved('detalle',t.dataset.id);};

requirementTable=data=>card('Requisitos y plazos',data.configurado?`<p class="hint">Fechas de entrega según el calendario de Chile (America/Santiago).</p><div class="scroll"><table><thead><tr><th>REQUISITO</th><th>PLAZO</th><th>ESTADO</th></tr></thead><tbody>${data.requisitos.map(r=>`<tr><td>${e(r.nombre)}</td><td>${e(r.fecha_limite||'Sin plazo')}</td><td><span class="pill">${e(r.estado)}</span>${r.vencido?' <span class="pill danger">Vencido</span>':''}</td></tr>`).join('')}</tbody></table></div>`:empty('Sin requisitos definidos','Configura una plantilla y luego inicializa esta práctica.')+(coord()?button('Aplicar plantilla a esta práctica',`data-action="configure-practice" data-id="${data.practica.id}"`):''));
detailSections.push(data=>{
  if(!role('COORDINADOR','PROFESOR_UBB') || !data.configurado)return '';
  return card('Actualizar un plazo',`<form id="deadline-form" class="tools">${selectField('Requisito','requisitoId',options(data.requisitos,r=>r.nombre))}${field('Fecha límite (vacío elimina el plazo)','fechaLimite','date',false,10)}${button('Guardar plazo','type="submit"')}</form>`);
});
detailBindings.push(id=>{
  const form=$('#deadline-form');if(!form)return;
  form.elements.requisitoId.addEventListener('change',()=>{form.elements.fechaLimite.value=state.detail.requisitos.find(r=>Number(r.id)===Number(form.elements.requisitoId.value))?.fecha_limite||'';});
  onForm('#deadline-form',async f=>{await send(`/requisitos/${f.elements.requisitoId.value}/plazo`,{fechaLimite:f.elements.fechaLimite.value||null},'PUT');await saved('detalle',id);});
});
