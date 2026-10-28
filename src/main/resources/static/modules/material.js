'use strict';

// S2-16 y S2-17: material de consulta para el asistente.
const materialStates={BORRADOR:['Borrador','pending'],PUBLICADO:['Publicado','ok'],RETIRADO:['Retirado','danger']};
state.materialFilter={anio:new Date().getFullYear(),semestre:2};
registerPage('materiales','Material de consulta',['COORDINADOR','PROFESOR_UBB'],async()=>{
  const f=state.materialFilter;
  const items=await api('/materiales?'+new URLSearchParams(Object.entries(f).filter(([,v])=>v!=='' && v!=null)));
  const preview=state.materialPreview?await api(`/materiales/${state.materialPreview}`):null;
  const rows=items.map(m=>{
    const [label,css]=materialStates[m.estado];
    const toggle=m.estado!=='PUBLICADO'?button('Publicar',`data-action="material-state" data-id="${m.id}" data-state="PUBLICADO"`,true):button('Retirar',`data-action="material-state" data-id="${m.id}" data-state="RETIRADO"`,true);
    return `<tr><td><strong>${e(m.titulo)}</strong><small><a href="/api/materiales/${m.id}/descarga">${e(m.nombre_original)}</a> · ${m.fragmentos} fragmentos</small></td><td>${e(m.asignatura)}<small>${m.anio}-${m.semestre}</small></td><td><span class="pill ${css}">${label}</span></td><td><div class="table-actions">${toggle}${button('Ver texto',`data-action="material-preview" data-id="${m.id}"`,true)}</div></td></tr>`;
  }).join('');
  const upload=`<form id="material-form">${field('Título','titulo','text',true,180)}${selectField('Asignatura','asignaturaId',options(state.asignaturas,a=>a.nombre),true,f.asignaturaId)}<div class="row">${field('Año','anio','number',true,4,f.anio||new Date().getFullYear())}${selectField('Semestre','semestre',[[1,'Primero'],[2,'Segundo']],true,f.semestre||2)}</div><label class="field">Archivo *<input name="archivo" type="file" accept=".pdf,.txt,.md" required><small>PDF con texto seleccionable, TXT o MD. Máximo 10 MB.</small></label>${button('Cargar y procesar','type="submit"')}<p class="hint">Queda como borrador: revisa el texto extraído antes de publicarlo.</p></form>`;
  const filter=`<form id="material-filter" class="tools">${selectField('Asignatura','asignaturaId',options(state.asignaturas,a=>a.nombre),false,f.asignaturaId)}${field('Año','anio','number',false,4,f.anio)}${selectField('Semestre','semestre',[[1,'Primero'],[2,'Segundo']],false,f.semestre)}${button('Filtrar','type="submit"')}</form>`;
  return heading('Material de consulta','El asistente solo usa material publicado, de la asignatura y período de cada estudiante.')+
    `<div class="split">${card('Cargar material',upload)}${card('Filtrar material',filter)}</div>`+
    card('Material cargado',items.length?`<div class="scroll"><table><thead><tr><th>MATERIAL</th><th>ASIGNATURA</th><th>ESTADO</th><th>ACCIONES</th></tr></thead><tbody>${rows}</tbody></table></div>`:empty('Sin material para este filtro'))+
    (preview?card(`Texto extraído: ${preview.titulo}`,`<div class="document-text">${e(preview.vista_previa)}</div>`):'');
},()=>{
  onForm('#material-form',async f=>{
    const file=f.elements.archivo.files[0];if(!file || file.size>10*1024*1024)throw new Error('Selecciona un archivo de hasta 10 MB.');
    const r=await api('/materiales',{method:'POST',body:new FormData(f)});state.materialPreview=r.id;await go('materiales');notice(r.mensaje);
  });
  onForm('#material-filter',async f=>{state.materialFilter=formData(f);state.materialPreview=null;await go('materiales');});
});
actions['material-state']=async t=>{const r=await send(`/materiales/${t.dataset.id}/estado`,{estado:t.dataset.state},'PATCH');await go('materiales');notice(r.mensaje);};
actions['material-preview']=async t=>{state.materialPreview=t.dataset.id;await go('materiales');};
