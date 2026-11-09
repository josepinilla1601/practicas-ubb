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
// Accesos rápidos a las tareas más frecuentes (heurística 7: eficiencia de uso).
const quickActions=items=>`<section class="quick-actions" aria-label="Accesos rápidos">${items.map(([i,t,attrs,kbd])=>`<button type="button" class="quick" ${attrs}>${icon(i,20)}<span>${e(t)}</span>${kbd?`<kbd>${e(kbd)}</kbd>`:''}</button>`).join('')}</section>`;
// Lleva a una práctica concreta si es una sola; si son varias, a Indicadores con la tabla ya filtrada u ordenada.
const irA=(lista,filtro,orden)=>lista.length===1?`data-detail="${lista[0].id}"`:`data-page="indicadores"${filtro?` data-filtro="${e(filtro)}"`:''}${orden?` data-orden="${e(orden)}"`:''}`;

pages.resumen.render=async()=>{
  const u=state.usuario;
  if(role('COORDINADOR','PROFESOR_UBB')) {
    const data=await api('/indicadores');const t=data.totales;const items=[];
    const conRevision=data.practicas.filter(p=>p.en_revision>0),conAtraso=data.practicas.filter(p=>p.vencidos>0),conCorreccion=data.practicas.filter(p=>p.observado>0);
    if(t.en_revision)items.push(attentionItem('inbox','info',t.en_revision,t.en_revision===1?'entrega por revisar':'entregas por revisar','Versiones nuevas que esperan tu aprobación o una corrección.','Revisar',irA(conRevision,'','POR REVISAR')));
    if(t.vencidos)items.push(attentionItem('clock','danger',t.vencidos,t.vencidos===1?'requisito fuera de plazo':'requisitos fuera de plazo','Requisitos pendientes cuya fecha límite ya pasó.','Ver atrasos',irA(conAtraso,'Con atraso','')));
    if(t.observado)items.push(attentionItem('edit','warn',t.observado,t.observado===1?'corrección solicitada':'correcciones solicitadas','El estudiante debe subir una nueva versión. Puedes revisar el comentario que dejaste.','Ver correcciones',irA(conCorreccion,'','CORRECCIÓN')));
    if(t.sin_configurar)items.push(attentionItem('calendar','warn',t.sin_configurar,t.sin_configurar===1?'práctica sin requisitos':'prácticas sin requisitos','Sin requisitos no se puede medir el avance.',coord()?'Configurar':'Ver prácticas',coord()?'data-page="plantillas"':'data-page="indicadores" data-filtro="Sin requisitos"'));
    const priority=data.practicas.filter(p=>p.situacion==='CON_ATRASO' || p.en_revision>0).slice(0,6);
    const rows=(priority.length?priority:data.practicas.slice(0,6)).map(p=>`<tr><td><strong>${e(p.estudiante)}</strong><small>${e(p.identificador)}</small></td><td>${e(p.asignatura)}<small>${p.anio}-${p.semestre}</small></td><td data-sort="${p.requisitos?p.aprobado/p.requisitos:0}">${p.aprobado} / ${p.requisitos}</td><td>${p.en_revision?`<span class="pill review">${p.en_revision} por revisar</span>`:''} ${p.vencidos?`<span class="pill danger">${p.vencidos} vencido(s)</span>`:''}${!p.en_revision && !p.vencidos?'<span class="pill ok">Al día</span>':''}</td><td>${button('Ver seguimiento',`data-detail="${p.id}"`,true)}</td></tr>`).join('');
    const atajos=[['inbox','Revisar entregas',irA(conRevision,'','POR REVISAR')],['chart','Ver indicadores','data-page="indicadores"','I'],['book','Material de consulta','data-page="materiales"'],['chat','Preguntar al asistente','data-open-chat','A']];
    if(coord())atajos.splice(2,0,['users','Registrar estudiante','data-page="estudiantes"']);
    return heading(`Hola, ${firstName(u.nombre)}`,coord()?'Resumen de todas las prácticas del sistema.':'Resumen de las prácticas que tienes asignadas.')+
      attentionCard(items)+quickActions(atajos)+
      `<div class="stats">${statTile('clipboard',t.practicas,'Prácticas','Prácticas que tienes a cargo.')}${statTile('inbox',t.en_revision,'Entregas por revisar','Versiones nuevas que esperan tu aprobación o una corrección.')}${statTile('check',t.aprobado,'Requisitos aprobados','Requisitos con su última versión aprobada.')}${statTile('clock',t.vencidos,'Fuera de plazo','Requisitos sin entregar o por corregir cuya fecha límite ya pasó.')}</div>`+
      card(priority.length?'Prácticas que necesitan revisión':'Prácticas recientes',data.practicas.length?`<div class="scroll"><table><thead><tr><th>ESTUDIANTE</th><th>ASIGNATURA</th><th>APROBADOS</th><th>ESTADO</th><th></th></tr></thead><tbody>${rows}</tbody></table></div>${data.practicas.length>6?`<p class="hint">Se muestran las más urgentes. ${button('Ver todas en Indicadores','data-page="indicadores"',true)}</p>`:''}`:empty('Todavía no hay prácticas','Cuando existan prácticas asignadas aparecerán aquí.'));
  }
  // Profesor de colegio: sus practicantes asignados.
  const items=state.practicas.length?[attentionItem('edit','info',state.practicas.length,state.practicas.length===1?'practicante asignado':'practicantes asignados','Registra tus observaciones desde el seguimiento de cada uno.','Ver practicantes','data-page="practicas"')]:[];
  return heading(`Hola, ${firstName(u.nombre)}`,'Practicantes que acompañas en tu establecimiento.')+
    (items.length?attentionCard(items):'')+card('Mis practicantes',practicesTable(state.practicas));
};
