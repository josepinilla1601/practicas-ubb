'use strict';

// Página de inicio con avisos accionables según el rol. Usa los mismos datos que "Indicadores".
function attentionItem(iconName,tone,count,title,detail,actionLabel,attrs) {
  return `<li class="attention-item ${tone}"><span class="attention-icon">${icon(iconName,20)}</span><div class="attention-text"><strong>${count} ${e(title)}</strong><small>${e(detail)}</small></div>${actionLabel?`<button type="button" class="button secondary small" ${attrs}>${e(actionLabel)} ${icon('arrow',14)}</button>`:''}</li>`;
}
function attentionCard(items) {
  if(!items.length)return `<section class="attention-card all-good">${icon('check',26)}<div><strong>Todo al día</strong><p>No hay entregas por revisar ni requisitos fuera de plazo.</p></div></section>`;
  return `<section class="attention-card"><div class="attention-head">${icon('alert',20)}<h2>Requiere tu atención</h2></div><ul class="attention-list">${items.join('')}</ul></section>`;
}
const firstName=name=>String(name||'').trim().split(/\s+/)[0]||'';

pages.resumen.render=async()=>{
  const u=state.usuario;
  if(role('COORDINADOR','PROFESOR_UBB')) {
    const data=await api('/indicadores');const t=data.totales;const items=[];
    if(t.en_revision)items.push(attentionItem('inbox','info',t.en_revision,t.en_revision===1?'entrega por revisar':'entregas por revisar','Versiones nuevas que esperan tu aprobación o una corrección.','Ver prácticas','data-page="indicadores"'));
    if(t.vencidos)items.push(attentionItem('clock','danger',t.vencidos,t.vencidos===1?'requisito fuera de plazo':'requisitos fuera de plazo','Requisitos pendientes cuya fecha límite ya pasó.','Ver atrasos','data-page="indicadores"'));
    if(t.observado)items.push(attentionItem('edit','warn',t.observado,t.observado===1?'corrección solicitada':'correcciones solicitadas','El estudiante debe subir una nueva versión.','',''));
    if(t.sin_configurar)items.push(attentionItem('calendar','warn',t.sin_configurar,t.sin_configurar===1?'práctica sin requisitos':'prácticas sin requisitos','Sin requisitos no se puede medir el avance.',coord()?'Configurar':'Ver prácticas',coord()?'data-page="plantillas"':'data-page="practicas"'));
    const priority=data.practicas.filter(p=>p.situacion==='CON_ATRASO' || p.en_revision>0).slice(0,6);
    const rows=(priority.length?priority:data.practicas.slice(0,6)).map(p=>`<tr><td><strong>${e(p.estudiante)}</strong><small>${e(p.identificador)}</small></td><td>${e(p.asignatura)}<small>${p.anio}-${p.semestre}</small></td><td>${p.aprobado} / ${p.requisitos}</td><td>${p.en_revision?`<span class="pill review">${p.en_revision} por revisar</span>`:''} ${p.vencidos?`<span class="pill danger">${p.vencidos} vencido(s)</span>`:''}</td><td>${button('Ver',`data-detail="${p.id}"`,true)}</td></tr>`).join('');
    return heading(`Hola, ${firstName(u.nombre)}`,coord()?'Resumen de todas las prácticas del sistema.':'Resumen de las prácticas que tienes asignadas.')+
      attentionCard(items)+
      `<div class="stats">${statTile('clipboard',t.practicas,'Prácticas')}${statTile('inbox',t.en_revision,'Entregas por revisar')}${statTile('check',t.aprobado,'Requisitos aprobados')}${statTile('clock',t.vencidos,'Fuera de plazo')}</div>`+
      card(priority.length?'Prácticas que necesitan revisión':'Prácticas recientes',data.practicas.length?`<div class="scroll"><table><thead><tr><th>ESTUDIANTE</th><th>ASIGNATURA</th><th>APROBADOS</th><th>ESTADO</th><th></th></tr></thead><tbody>${rows}</tbody></table></div>`:empty('Todavía no hay prácticas','Cuando existan prácticas asignadas aparecerán aquí.'));
  }
  // Profesor de colegio: sus practicantes asignados.
  const items=state.practicas.length?[attentionItem('edit','info',state.practicas.length,state.practicas.length===1?'practicante asignado':'practicantes asignados','Registra tus observaciones desde el seguimiento de cada uno.','Ver practicantes','data-page="practicas"')]:[];
  return heading(`Hola, ${firstName(u.nombre)}`,'Practicantes que acompañas en tu establecimiento.')+
    (items.length?attentionCard(items):'')+card('Mis practicantes',practicesTable(state.practicas));
};
