'use strict';

// S2-11 a S2-13: revisión de entregas, observaciones e historial de versiones.
const statusLabels={PENDIENTE:'Pendiente',EN_REVISION:'En revisión',OBSERVADO:'Corrección solicitada',APROBADO:'Aprobado'};
const statusClass={PENDIENTE:'pending',EN_REVISION:'review',OBSERVADO:'danger',APROBADO:'ok'};
const statusPill=estado=>`<span class="pill ${statusClass[estado]||''}">${e(statusLabels[estado]||estado)}</span>`;
const esPendiente=estado=>estado==='PENDIENTE' || estado==='OBSERVADO';

requirementTable=data=>card('Requisitos y plazos',data.configurado?`<p class="hint">Fechas según el calendario de Chile. "Recibido" no significa "aprobado": cada entrega espera la revisión del profesor.</p><div class="scroll"><table><thead><tr><th>REQUISITO</th><th>PLAZO</th><th>ESTADO</th></tr></thead><tbody>${data.requisitos.map(r=>`<tr><td>${e(r.nombre)}<small>${r.archivos} ${Number(r.archivos)===1?'versión':'versiones'}</small></td><td>${plazoTexto(r.fecha_limite,r.vencido,esPendiente(r.estado))}</td><td>${statusPill(r.estado)}</td></tr>`).join('')}</tbody></table></div>`:empty('Sin requisitos definidos','Configura una plantilla y luego inicialízala en esta práctica.')+(coord()?button('Aplicar plantilla a esta práctica',`data-action="configure-practice" data-id="${data.practica.id}"`):''));

// Acciones para corregir errores (heurística 3): eliminar una entrega aún no revisada o deshacer una revisión.
function accionesVersion(d) {
  if(!d.ultima)return '';
  if(!d.decision && role('COORDINADOR','ESTUDIANTE'))return button('Eliminar entrega',`type="button" data-action="delete-document" data-id="${d.id}" data-name="${e(d.nombre_original)}"`,true);
  const autor=Number(d.revisor_id)===Number(state.usuario?.id);
  if(d.decision && (coord() || (role('PROFESOR_UBB') && autor)))return button('Deshacer revisión',`type="button" data-action="undo-review" data-id="${d.id}"`,true);
  return '';
}
documentsTable=data=>card('Historial de entregas y revisiones',data.documentos.length?`<div class="scroll"><table><thead><tr><th>ARCHIVO</th><th>REQUISITO</th><th>ENTREGADO</th><th>REVISIÓN</th><th>ACCIONES</th></tr></thead><tbody>${data.documentos.map(d=>`<tr><td><a href="api/documentos/${d.id}/descarga" title="Descargar ${e(d.nombre_original)}">${e(d.nombre_original)}</a><small>Versión ${d.version}${d.ultima?' · más reciente':''}</small></td><td>${e(d.requisito)}</td><td data-sort="${e(d.cargado_en)}">${e(dateText(d.cargado_en))}</td><td>${d.decision?`${statusPill(d.decision)}<small>${e(d.revisor)} · ${e(dateText(d.revisado_en))}</small>${d.comentario?`<p class="hint">${e(d.comentario)}</p>`:''}`:statusPill('EN_REVISION')}</td><td>${accionesVersion(d)||'<span class="muted">—</span>'}</td></tr>`).join('')}</tbody></table></div><p class="hint">Puedes eliminar la versión más reciente mientras no esté revisada. Una revisión se puede deshacer hasta que el estudiante suba una versión nueva.</p>`:empty('Todavía no hay archivos','Cuando el estudiante entregue un documento aparecerá aquí con su revisión.'));

actions['delete-document']=async t=>{
  const ok=await confirmar({titulo:'¿Eliminar esta entrega?',mensaje:`Se eliminará "${t.dataset.name}". Podrás subir otro archivo para el mismo requisito.`,aceptar:'Eliminar entrega',peligro:true});
  if(!ok)return;
  const r=await api(`/documentos/${t.dataset.id}`,{method:'DELETE'});
  await load();await go('detalle',state.detail.practica.id);notice(r.mensaje);
};
actions['undo-review']=async t=>{
  const ok=await confirmar({titulo:'¿Deshacer la revisión?',mensaje:'La entrega volverá a quedar "En revisión" y podrás revisarla otra vez.',aceptar:'Deshacer revisión'});
  if(!ok)return;
  const r=await api(`/documentos/${t.dataset.id}/revision`,{method:'DELETE'});
  await load();await go('detalle',state.detail.practica.id);notice(r.mensaje);
};

detailSections.push(data=>{
  if(!role('COORDINADOR','PROFESOR_UBB'))return '';
  const porRevisar=data.documentos.filter(d=>d.ultima && !d.decision);
  return card('Revisar entregas',porRevisar.length?`<form id="review-form">${selectField('Versión por revisar','documentoId',porRevisar.map(d=>[d.id,`${d.requisito} · versión ${d.version} · ${d.nombre_original}`]))}${selectField('Decisión','decision',[['APROBADO','Aprobar'],['OBSERVADO','Solicitar corrección']])}<label class="field">Comentario para el estudiante<span class="req-when" id="comment-req" hidden> * (obligatorio al solicitar una corrección)</span><textarea name="comentario" maxlength="2000" placeholder="Ejemplo: Falta la bibliografía y la firma del profesor colaborador."></textarea></label>${button('Registrar revisión','type="submit"')}<p class="hint">Antes de guardar te pediremos confirmar. Si te equivocas, podrás deshacer la revisión.</p></form>`:empty('No hay entregas por revisar','Las versiones más recientes ya tienen revisión.'));
});
detailBindings.push(id=>{
  const form=$('#review-form');if(!form)return;
  // El comentario pasa a ser obligatorio solo al solicitar una corrección.
  const actualizar=()=>{const obs=form.elements.decision.value==='OBSERVADO';form.elements.comentario.required=obs;$('#comment-req').hidden=!obs;};
  form.elements.decision.addEventListener('change',actualizar);actualizar();
  onForm('#review-form',async f=>{
    const d=formData(f);
    const r=await send(`/documentos/${d.documentoId}/revision`,{decision:d.decision,comentario:d.comentario});
    await load();await go('detalle',id);
    notice(r.mensaje,false,{texto:'Deshacer',fn:async()=>{
      const x=await api(`/documentos/${d.documentoId}/revision`,{method:'DELETE'});await load();await go('detalle',id);notice(x.mensaje);
    }});
  },'Registrando revisión…',f=>{
    const d=formData(f);const doc=state.detail.documentos.find(x=>Number(x.id)===Number(d.documentoId));
    const aprobar=d.decision==='APROBADO';
    return confirmar({
      titulo:aprobar?'¿Aprobar esta entrega?':'¿Solicitar una corrección?',
      mensaje:aprobar?`"${doc?.requisito}" quedará aprobado y el estudiante ya no podrá subir más versiones.`:`El estudiante verá tu comentario y deberá subir una versión corregida de "${doc?.requisito}".`,
      aceptar:aprobar?'Aprobar entrega':'Solicitar corrección'});
  });
});

// Un requisito aprobado ya no admite nuevas versiones.
uploadForm=data=>{
  if(!role('COORDINADOR','ESTUDIANTE'))return '';
  const abiertos=data.requisitos.filter(r=>r.estado!=='APROBADO');
  if(!data.configurado)return card('Entregar documento',empty('Sin requisitos definidos','La coordinación debe preparar los requisitos de la práctica.'));
  if(!abiertos.length)return card('Entregar documento',empty('Todos los requisitos están aprobados','¡Buen trabajo! No tienes entregas pendientes en esta práctica.'));
  return card('Entregar documento o versión corregida',`<form id="upload-form">${selectField('Requisito','requisitoId',abiertos.map(r=>[r.id,`${r.nombre} · ${statusLabels[r.estado]}${r.fecha_limite?` · plazo ${fechaCorta(r.fecha_limite)}`:''}`]))}<label class="field">Archivo${req}<input name="archivo" type="file" accept=".pdf,.docx,.xlsx" required><small>PDF, DOCX o XLSX. Máximo 10 MB.</small></label>${button('Entregar documento','type="submit"')}<p class="hint">Si te pidieron una corrección, sube aquí la nueva versión. Las versiones anteriores se conservan, y si te equivocas de archivo podrás eliminarlo mientras no esté revisado.</p></form>`);
};
