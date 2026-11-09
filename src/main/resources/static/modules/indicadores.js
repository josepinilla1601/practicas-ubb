'use strict';

// S2-15: panel de indicadores para coordinación y profesores UBB.
const situationLabels={SIN_CONFIGURAR:['Sin requisitos','pending'],COMPLETA:['Completa','ok'],CON_ATRASO:['Con atraso','danger'],EN_CURSO:['En curso','review']};
registerPage('indicadores','Indicadores',['COORDINADOR','PROFESOR_UBB'],async()=>{
  const data=await api('/indicadores');const t=data.totales;
  const stats=[['practicas','Prácticas','clipboard','Prácticas que tienes a cargo o autorizadas.'],['pendiente','Requisitos pendientes','calendar','Requisitos que aún no tienen ninguna entrega.'],['en_revision','Entregas por revisar','inbox','Versiones nuevas que esperan una aprobación o una corrección.'],['observado','Correcciones solicitadas','edit','Entregas devueltas: el estudiante debe subir una versión corregida.'],['aprobado','Requisitos aprobados','check','Requisitos con su última versión aprobada.'],['vencidos','Pendientes fuera de plazo','clock','Requisitos sin entregar o por corregir cuya fecha límite ya pasó.']];
  const rows=data.practicas.map(p=>{const [label,css]=situationLabels[p.situacion];return `<tr><td><strong>${e(p.estudiante)}</strong><small>${e(p.identificador)}</small></td><td>${e(p.asignatura)}<small>${p.anio}-${p.semestre}</small></td><td>${p.aprobado} / ${p.requisitos}</td><td>${p.pendiente}</td><td>${p.en_revision}</td><td>${p.observado}</td><td>${p.vencidos}</td><td><span class="pill ${css}">${label}</span></td><td>${button('Ver',`data-detail="${p.id}"`,true)}</td></tr>`;}).join('');
  return heading('Indicadores de avance','Calculados con las prácticas que tienes autorizadas. Una práctica sin requisitos no se cuenta como completa.')+
    `<div class="stats stats-6">${stats.map(([k,l,i,a])=>statTile(i,t[k],l,a)).join('')}</div>`+
    card('Detalle por práctica',data.practicas.length?`<p class="hint">${t.completas} completa(s) · ${t.sin_configurar} sin requisitos configurados.</p><div class="scroll"><table><thead><tr><th>ESTUDIANTE</th><th>ASIGNATURA</th><th>APROBADOS</th><th>PENDIENTES</th><th>POR REVISAR</th><th>CORRECCIÓN</th><th>ATRASADOS</th><th>SITUACIÓN</th><th></th></tr></thead><tbody>${rows}</tbody></table></div>`:empty('No hay prácticas para mostrar'));
});
