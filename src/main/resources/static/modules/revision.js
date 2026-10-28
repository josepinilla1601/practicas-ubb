'use strict';

// S2-11 a S2-13: revisión de entregas, observaciones e historial de versiones.
const statusLabels={PENDIENTE:'Pendiente',EN_REVISION:'En revisión',OBSERVADO:'Corrección solicitada',APROBADO:'Aprobado'};
const statusClass={PENDIENTE:'pending',EN_REVISION:'review',OBSERVADO:'danger',APROBADO:'ok'};
const statusPill=estado=>`<span class="pill ${statusClass[estado]||''}">${e(statusLabels[estado]||estado)}</span>`;

requirementTable=data=>card('Requisitos y plazos',data.configurado?`<p class="hint">Fechas según el calendario de Chile (America/Santiago). Recibido no equivale a aprobado.</p><div class="scroll"><table><thead><tr><th>REQUISITO</th><th>PLAZO</th><th>ESTADO</th></tr></thead><tbody>${data.requisitos.map(r=>`<tr><td>${e(r.nombre)}<small>${r.archivos} versión(es)</small></td><td>${e(r.fecha_limite||'Sin plazo')}</td><td>${statusPill(r.estado)}${r.vencido?' <span class="pill danger">Vencido</span>':''}</td></tr>`).join('')}</tbody></table></div>`:empty('Sin requisitos definidos','Configura una plantilla y luego inicializa esta práctica.')+(coord()?button('Aplicar plantilla a esta práctica',`data-action="configure-practice" data-id="${data.practica.id}"`):''));

documentsTable=data=>card('Historial de entregas y revisiones',data.documentos.length?`<div class="scroll"><table><thead><tr><th>ARCHIVO</th><th>REQUISITO</th><th>ENTREGADO</th><th>REVISIÓN</th></tr></thead><tbody>${data.documentos.map(d=>`<tr><td><a href="/api/documentos/${d.id}/descarga">${e(d.nombre_original)}</a><small>Versión ${d.version}${d.ultima?' · más reciente':''}</small></td><td>${e(d.requisito)}</td><td>${e(dateText(d.cargado_en))}</td><td>${d.decision?`${statusPill(d.decision)}<small>${e(d.revisor)} · ${e(dateText(d.revisado_en))}</small>${d.comentario?`<p class="hint">${e(d.comentario)}</p>`:''}`:statusPill('EN_REVISION')}</td></tr>`).join('')}</tbody></table></div>`:empty('Todavía no hay archivos'));

detailSections.push(data=>{
  if(!role('COORDINADOR','PROFESOR_UBB'))return '';
  const porRevisar=data.documentos.filter(d=>d.ultima && !d.decision);
  return card('Revisar entregas',porRevisar.length?`<form id="review-form">${selectField('Versión por revisar','documentoId',porRevisar.map(d=>[d.id,`${d.requisito} · versión ${d.version} · ${d.nombre_original}`]))}${selectField('Decisión','decision',[['APROBADO','Aprobar'],['OBSERVADO','Solicitar corrección']])}<label class="field">Comentario (obligatorio si solicitas corrección)<textarea name="comentario" maxlength="2000"></textarea></label>${button('Registrar revisión','type="submit"')}</form>`:empty('No hay entregas por revisar','Las versiones más recientes ya tienen revisión.'));
});
detailBindings.push(id=>onForm('#review-form',async f=>{const d=formData(f);await send(`/documentos/${d.documentoId}/revision`,{decision:d.decision,comentario:d.comentario});await saved('detalle',id);}));

// Un requisito aprobado ya no admite nuevas versiones.
uploadForm=data=>{
  if(!role('COORDINADOR','ESTUDIANTE'))return '';
  const abiertos=data.requisitos.filter(r=>r.estado!=='APROBADO');
  if(!data.configurado)return card('Adjuntar documento',empty('Sin requisitos definidos','La coordinación debe preparar los requisitos de la práctica.'));
  if(!abiertos.length)return card('Adjuntar documento',empty('Todos los requisitos están aprobados'));
  return card('Adjuntar documento o versión corregida',`<form id="upload-form">${selectField('Requisito','requisitoId',abiertos.map(r=>[r.id,`${r.nombre} · ${statusLabels[r.estado]}`]))}<label class="field">Archivo *<input name="archivo" type="file" accept=".pdf,.docx,.xlsx" required><small>PDF, DOCX o XLSX. Máximo 10 MB.</small></label>${button('Guardar entrega','type="submit"')}<p class="hint">Si te solicitaron una corrección, sube la nueva versión aquí. Las versiones anteriores se conservan.</p></form>`);
};
