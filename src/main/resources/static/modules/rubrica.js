'use strict';

// S3-06 y S3-07: evaluación de desempeño con la rúbrica del Marco para la Buena Enseñanza (MBE).
// El profesor de colegio marca un nivel (1 a 4) por criterio; el sistema calcula la nota sugerida;
// el profesor UBB puede usarla como calificación interna.
let rubricaDef=null;
async function definicionRubrica() { if(!rubricaDef)rubricaDef=await api('/rubrica');return rubricaDef; }
const notaDeNiveles=niveles=>{const v=Object.values(niveles);return v.length?(2*v.reduce((a,b)=>a+b,0)/v.length-1):null;};

detailSections.push(Object.assign(async data=>{
  const id=data.practica.id;
  const [def,lista]=await Promise.all([definicionRubrica(),api(`/practicas/${id}/rubricas`)]);
  const nombres=def.niveles;
  const resultados=lista.length?lista.map(r=>`<div class="rubrica-resultado">
      <div class="rubrica-top"><div><strong>Nota sugerida ${notaPill(r.nota_sugerida)}</strong><small>${e(r.autor)} · ${e(dateText(r.creado_en))} · puntaje promedio ${String(r.puntaje).replace('.',',')} de 4</small></div>
      ${role('PROFESOR_UBB','COORDINADOR')?button('Usar como nota',`type="button" data-action="rubrica-nota" data-id="${r.id}" data-practica="${id}"`,true):''}</div>
      <div class="dominios">${Object.entries(def.dominios).map(([d,nombre])=>`<div class="dominio"><span>${d}. ${e(nombre)}</span><div class="barra"><i style="width:${Number(r.dominios[d])/4*100}%"></i></div><b>${String(r.dominios[d]).replace('.',',')}</b></div>`).join('')}</div>
      ${r.comentario?`<p class="hint">${e(r.comentario)}</p>`:''}</div>`).join('')
    :empty('Sin evaluaciones con rúbrica',role('PROFESOR_COLEGIO')?'Completa la rúbrica de abajo para evaluar el desempeño del practicante.':'El profesor de colegio aún no completa la rúbrica.');
  const formulario=role('PROFESOR_COLEGIO','COORDINADOR')?`<form id="rubrica-form" class="rubrica-form"><h3>Evaluar con la rúbrica</h3>
    <p class="hint">Marca un nivel por criterio: ${Object.entries(nombres).map(([n,t])=>`${n} = ${e(t)}`).join(' · ')}.</p>
    ${Object.entries(def.dominios).map(([d,nombre])=>`<fieldset><legend>${d}. ${e(nombre)}</legend>${def.criterios.filter(c=>c.dominio===d).map(c=>`<div class="criterio"><p><b>${c.codigo}</b> ${e(c.descripcion)}</p><div class="niveles" role="radiogroup" aria-label="${e(c.codigo)}">${[1,2,3,4].map(n=>`<label title="${e(nombres[n])}"><input type="radio" name="${c.codigo}" value="${n}"><span>${n}</span></label>`).join('')}</div></div>`).join('')}</fieldset>`).join('')}
    <label class="field">Comentario general<textarea name="comentario" maxlength="2000" placeholder="Fortalezas y aspectos por mejorar del desempeño en el aula."></textarea></label>
    <div class="rubrica-nota">Nota sugerida: <strong id="rubrica-sugerida">—</strong> <small>(se calcula al marcar los 12 criterios)</small></div>
    ${button('Registrar rúbrica','type="submit"')}</form>`:'';
  return card('Evaluación de desempeño (Marco para la Buena Enseñanza)',resultados+formulario);
},{orden:60,pestana:'evaluacion'}));
detailBindings.push(id=>{
  const form=$('#rubrica-form');if(!form)return;
  const leerNiveles=()=>{const n={};for(const c of rubricaDef.criterios){const v=form.querySelector(`input[name="${c.codigo}"]:checked`);if(v)n[c.codigo]=Number(v.value);}return n;};
  form.addEventListener('change',ev=>{ev.target.closest('.criterio')?.classList.remove('falta');const n=leerNiveles();$('#rubrica-sugerida').innerHTML=Object.keys(n).length===rubricaDef.criterios.length?notaPill(Math.round(notaDeNiveles(n)*10)/10):`${Object.keys(n).length} de ${rubricaDef.criterios.length} criterios`;});
  onForm('#rubrica-form',async f=>{
    const niveles=leerNiveles();
    const r=await send(`/practicas/${id}/rubricas`,{niveles,comentario:f.elements.comentario.value});
    await go('detalle',id);notice(r.mensaje);
  },'Registrando rúbrica…',()=>{
    // Marca los criterios sin nivel antes de pedir confirmación.
    const niveles=leerNiveles();let faltan=0;
    form.querySelectorAll('.criterio').forEach(c=>{const sin=!c.querySelector('input:checked');c.classList.toggle('falta',sin);if(sin)faltan++;});
    if(faltan){form.querySelector('.criterio.falta')?.scrollIntoView({block:'center'});notice(faltan===1?'Falta 1 criterio por evaluar: está marcado en rojo.':`Faltan ${faltan} criterios por evaluar: están marcados en rojo.`,true);return false;}
    return confirmar({titulo:'¿Registrar la rúbrica?',mensaje:`La nota sugerida es ${notaTexto(Math.round(notaDeNiveles(niveles)*10)/10)}. El profesor supervisor podrá usarla como calificación.`,aceptar:'Registrar rúbrica'});
  });
});
actions['rubrica-nota']=async t=>{
  const valor=await pedirTexto({titulo:'Usar como calificación',mensaje:'La nota sugerida se agregará a las calificaciones internas con la ponderación que indiques.',etiqueta:'Ponderación (%)',tipo:'number',min:1,max:100,valor:'30',aceptar:'Agregar nota'});
  if(valor===null)return;
  const r=await send(`/rubricas/${t.dataset.id}/usar-como-nota`,{ponderacion:Number(valor)});await go('detalle',t.dataset.practica);notice(r.mensaje);
};
