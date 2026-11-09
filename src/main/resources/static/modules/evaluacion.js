'use strict';

// S2-14: evaluación cualitativa del profesor de colegio, visible para el profesor UBB y la coordinación.
detailSections.push(async data=>{
  if(!role('COORDINADOR','PROFESOR_UBB','PROFESOR_COLEGIO'))return '';
  const items=await api(`/practicas/${data.practica.id}/evaluaciones`);
  const list=items.length?`<div class="messages">${items.map(x=>`<div class="message"><strong>${e(x.autor)} · ${e(dateText(x.creado_en))}</strong>\n\nFortalezas: ${e(x.fortalezas)}\n\nAspectos por mejorar: ${e(x.aspectos_mejora)}${x.recomendaciones?`\n\nRecomendaciones: ${e(x.recomendaciones)}`:''}</div>`).join('')}</div>`:empty('Sin evaluaciones del colegio','El profesor de colegio asignado aún no registra observaciones.');
  const form=role('PROFESOR_COLEGIO')?`<form id="evaluation-form"><label class="field">Fortalezas observadas${req}<textarea name="fortalezas" required maxlength="3000" placeholder="Ejemplo: planifica con claridad y mantiene un buen clima en el aula."></textarea></label><label class="field">Aspectos por mejorar${req}<textarea name="aspectosMejora" required maxlength="3000" placeholder="Ejemplo: ajustar los tiempos del cierre de la clase."></textarea></label><label class="field">Recomendaciones<textarea name="recomendaciones" maxlength="3000"></textarea></label>${button('Registrar evaluación','type="submit"')}<p class="hint">Evaluación cualitativa: no reemplaza la calificación oficial. Las evaluaciones anteriores se conservan.</p></form>`:'';
  return card('Evaluación del profesor de colegio',form+list);
});
detailBindings.push(id=>onForm('#evaluation-form',async f=>{await send(`/practicas/${id}/evaluaciones`,formData(f));await saved('detalle',id);}));
