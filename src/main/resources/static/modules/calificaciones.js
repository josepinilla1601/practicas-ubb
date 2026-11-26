'use strict';

// Calificaciones internas (escala 1,0 a 7,0, aprobación con 4,0). Las registra el profesor UBB asignado o la coordinación;
// el estudiante las consulta. También: botones para exportar a Excel.
const situacionNota={SIN_NOTAS:['Sin notas','pending'],EN_CURSO:['En curso','review'],APROBADA:['Aprobada','ok'],REPROBADA:['Reprobada','danger']};
const notaTexto=n=>n===null || n===undefined || n===''?'—':Number(n).toFixed(1).replace('.',',');
const notaPill=n=>n===null || n===undefined?'<span class="muted">—</span>':`<span class="nota ${Number(n)<4?'roja':''}">${notaTexto(n)}</span>`;
const puedeCalificar=()=>role('COORDINADOR','PROFESOR_UBB');

detailSections.push(Object.assign(async data=>{
  if(!role('COORDINADOR','PROFESOR_UBB','ESTUDIANTE'))return '';
  const c=await api(`/practicas/${data.practica.id}/calificaciones`);
  const [label,css]=situacionNota[c.situacion]||['—',''];
  const resumen=`<div class="grade-summary">
    <div><span>Promedio ponderado</span><strong class="${c.promedio!==null && Number(c.promedio)<4?'roja':''}">${notaTexto(c.promedio)}</strong></div>
    <div><span>Ponderación evaluada</span><strong>${c.ponderacion} %</strong><div class="progress"><span style="width:${c.ponderacion}%"></span></div></div>
    <div><span>Situación</span><span class="pill ${css}">${label}</span></div></div>`;
  const tabla=c.notas.length?`<div class="scroll"><table><thead><tr><th>EVALUACIÓN</th><th>NOTA</th><th>PONDERACIÓN</th><th>REGISTRADA POR</th>${puedeCalificar()?'<th>ACCIONES</th>':''}</tr></thead><tbody>${c.notas.map(n=>`<tr><td><strong>${e(n.evaluacion)}</strong>${n.comentario?`<small>${e(n.comentario)}</small>`:''}</td><td data-sort="${n.nota}">${notaPill(n.nota)}</td><td data-sort="${n.ponderacion}">${n.ponderacion} %</td><td>${e(n.autor)}<small>${e(dateText(n.creado_en))}</small></td>${puedeCalificar()?`<td>${coord() || Number(n.autor_id)===Number(state.usuario.id)?button('Eliminar',`type="button" data-action="delete-grade" data-id="${n.id}" data-name="${e(n.evaluacion)}"`,true):'<span class="muted">—</span>'}</td>`:''}</tr>`).join('')}</tbody></table></div>`
    :empty('Todavía no hay notas',puedeCalificar()?'Registra la primera evaluación con el formulario de abajo.':'Tu profesor supervisor registrará aquí tus notas.');
  const restante=100-c.ponderacion;
  let pie;
  if(!puedeCalificar())pie='<p class="hint">Calificaciones internas registradas por tu profesor supervisor. No reemplazan el acta oficial.</p>';
  else if(restante<=0)pie='<p class="hint">Ya se evaluó el 100 %. Para corregir una nota, elimínala y vuelve a registrarla.</p>';
  else pie=`<form id="grade-form" class="grade-form"><h3>Registrar una nota</h3>${field('Evaluación (por ejemplo: Planificación de clase)','evaluacion','text',true,150)}<div class="row"><label class="field">Nota (1,0 a 7,0)${req}<input name="nota" type="number" min="1" max="7" step="0.1" inputmode="decimal" required placeholder="5,5"></label><label class="field">Ponderación (%)${req}<input name="ponderacion" type="number" min="1" max="${restante}" step="1" required value="${restante}"></label></div><label class="field">Comentario para el estudiante (opcional)<textarea name="comentario" maxlength="2000" placeholder="Ejemplo: buena secuencia didáctica; mejorar el cierre de la clase."></textarea></label>${button('Registrar nota','type="submit"')}<p class="hint">Escala de 1,0 a 7,0: se aprueba con 4,0. Quedan ${restante} % por evaluar.</p></form>`;
  return card('Calificaciones internas',resumen+tabla+pie);
},{orden:70,pestana:'evaluacion'}));
detailBindings.push(id=>onForm('#grade-form',async f=>{
  const d=formData(f);
  const r=await send(`/practicas/${id}/calificaciones`,{evaluacion:d.evaluacion,nota:Number(d.nota),ponderacion:Number(d.ponderacion),comentario:d.comentario});
  const nueva=r.notas[r.notas.length-1];
  await load();await go('detalle',id);
  notice(`Nota ${notaTexto(nueva.nota)} registrada en "${nueva.evaluacion}". Promedio actual: ${notaTexto(r.promedio)}.`,false,{texto:'Deshacer',fn:async()=>{
    await api(`/calificaciones/${nueva.id}`,{method:'DELETE'});await go('detalle',id);notice('Nota eliminada.');
  }});
},'Registrando nota…'));
actions['delete-grade']=async t=>{
  const ok=await confirmar({titulo:'¿Eliminar esta nota?',mensaje:`Se eliminará la nota de "${t.dataset.name}" y se recalculará el promedio.`,aceptar:'Eliminar nota',peligro:true});
  if(!ok)return;
  const practica=state.detail.practica.id;
  const r=await api(`/calificaciones/${t.dataset.id}`,{method:'DELETE'});
  await go('detalle',practica);
  notice(r.mensaje,false,{texto:'Deshacer',fn:async()=>{
    const x=r.eliminada;await send(`/practicas/${practica}/calificaciones`,{evaluacion:x.evaluacion,nota:Number(x.nota),ponderacion:Number(x.ponderacion),comentario:x.comentario});
    await go('detalle',practica);notice('Nota restaurada.');
  }});
};

// Exportar a Excel: respeta los filtros de la página y los permisos de cada cuenta.
function enlaceExcel(filtros={}) {
  const q=new URLSearchParams(Object.entries(filtros).filter(([k,v])=>['asignaturaId','anio','semestre'].includes(k) && v!=='' && v!=null));
  if(state.periodo)q.set('periodo',state.periodo);
  return `<a class="button excel" href="api/exportar/practicas.xlsx${q.toString()?'?'+q:''}" download title="Descarga un Excel con el resumen por práctica, los requisitos y las calificaciones">${icon('chart',16)} Exportar a Excel</a>`;
}
// El botón va a la derecha de la barra de la tabla (sobre la columna "Detalle"). Se agrega al final,
// cuando la barra de búsqueda ya existe; si la tabla es corta y no tiene barra, va en el título de la tarjeta.
pageHooks.push(page=>{
  if(!puedeCalificar() || !['indicadores','practicas'].includes(page))return;
  setTimeout(()=>{
    if(state.page!==page || $('#app .excel'))return;
    const enlace=enlaceExcel(page==='practicas'?state.filters:{});
    const tabla=$('#app .card table');if(!tabla)return;
    const barra=tabla.closest('.card-body')?.querySelector('.table-tools');
    if(barra)barra.insertAdjacentHTML('beforeend',enlace);
    else tabla.closest('.card')?.querySelector('.card-head')?.insertAdjacentHTML('beforeend',enlace);
  },0);
});
document.addEventListener('click',ev=>{ if(ev.target.closest('a.excel'))notice('Preparando el Excel… la descarga comenzará en unos segundos.'); });
