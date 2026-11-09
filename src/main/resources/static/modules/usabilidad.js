'use strict';

// Mejoras de usabilidad comunes a todo el sistema (evaluación heurística de Nielsen):
// búsqueda, filtros y orden en tablas; ayuda contextual; guía de bienvenida; atajos y preferencias.

const guardar=(clave,valor)=>{try{localStorage.setItem(clave,JSON.stringify(valor));}catch{/* sin almacenamiento */}};
const leer=(clave,defecto=null)=>{try{const v=localStorage.getItem(clave);return v===null?defecto:JSON.parse(v);}catch{return defecto;}};
const normalizar=t=>String(t||'').normalize('NFD').replace(/\p{M}/gu,'').toLowerCase().trim();
const escribiendo=el=>!!el && (['INPUT','TEXTAREA','SELECT'].includes(el.tagName) || el.isContentEditable);

/* ---------- Tablas: búsqueda, filtros por estado y orden (heurística 7) ---------- */
function valorOrden(celda) {
  const marcado=celda.querySelector('[data-sort]')?.dataset.sort ?? celda.dataset.sort;
  const texto=(marcado ?? celda.textContent).trim();
  const numero=Number(texto.replace(',','.'));
  return texto!=='' && !Number.isNaN(numero)?numero:normalizar(texto);
}
function etiquetaEstado(pill) { const t=pill.textContent.replace(/^\d+\s*/,'').trim();return t.charAt(0).toUpperCase()+t.slice(1); }
function mejorarTabla(table,clave,inicial) {
  const tbody=table.tBodies[0];const head=table.tHead?.rows[0];
  if(!tbody || !head || table.dataset.mejorada)return;
  const filas=[...tbody.rows];if(filas.length<3)return;
  table.dataset.mejorada='1';
  const titulo=table.closest('.card')?.querySelector('.card-head h2')?.textContent||'la tabla';
  // Se recuerda solo el orden elegido; la búsqueda y el filtro empiezan limpios (salvo que un aviso los indique).
  const pref={...leer(clave,{}),texto:'',estado:'',...(inicial||{})};
  const estados=[...new Set(filas.flatMap(f=>[...f.querySelectorAll('.pill')].map(etiquetaEstado)))];
  const chips=estados.length>=2 && estados.length<=7;
  const barra=document.createElement('div');barra.className='table-tools';
  barra.innerHTML=`<label class="table-search"><span class="sr-only">Buscar en ${e(titulo)}</span>${icon('eye',16)}<input type="search" placeholder="Buscar en esta tabla…" title="Atajo: tecla /" value="${e(pref.texto||'')}"></label>`+
    (chips?`<div class="chips" role="group" aria-label="Filtrar por estado"><button type="button" class="chip-filter" data-estado="" aria-pressed="true">Todos</button>${estados.map(x=>`<button type="button" class="chip-filter" data-estado="${e(x)}" aria-pressed="false">${e(x)}</button>`).join('')}</div>`:'')+
    `<span class="table-count" aria-live="polite"></span>`;
  (table.closest('.scroll')||table).before(barra);
  const vacio=document.createElement('tr');vacio.className='no-results';vacio.hidden=true;
  vacio.innerHTML=`<td colspan="${head.cells.length}"><strong>No hay resultados.</strong> Prueba con otra palabra o <button type="button" class="link-button" data-limpiar>muestra todos los registros</button>.</td>`;
  tbody.appendChild(vacio);
  let estado=pref.estado&&estados.includes(pref.estado)?pref.estado:'';
  const input=barra.querySelector('input');
  const aplicar=()=>{
    const q=normalizar(input.value);let visibles=0;
    for(const f of filas){
      const ok=(!q || normalizar(f.textContent).includes(q)) && (!estado || [...f.querySelectorAll('.pill')].some(p=>etiquetaEstado(p)===estado));
      f.hidden=!ok;if(ok)visibles++;
    }
    vacio.hidden=visibles>0;
    barra.querySelector('.table-count').textContent=visibles===filas.length?`${filas.length} registros`:`Mostrando ${visibles} de ${filas.length}`;
    barra.querySelectorAll('.chip-filter').forEach(c=>c.setAttribute('aria-pressed',String(c.dataset.estado===estado)));
  };
  const ordenar=(col,dir)=>{
    const factor=dir==='asc'?1:-1;
    filas.sort((a,b)=>{const x=valorOrden(a.cells[col]),y=valorOrden(b.cells[col]);return (typeof x==='number'&&typeof y==='number'?x-y:String(x).localeCompare(String(y),'es',{numeric:true}))*factor;});
    filas.forEach(f=>tbody.insertBefore(f,vacio));
    [...head.cells].forEach((th,i)=>th.setAttribute('aria-sort',i===col?(dir==='asc'?'ascending':'descending'):'none'));
    guardar(clave,{...leer(clave,{}),col,dir});
  };
  [...head.cells].forEach((th,i)=>{
    const nombre=th.textContent.trim();
    if(!nombre || /^ACCI/i.test(nombre))return;
    th.setAttribute('aria-sort','none');
    th.innerHTML=`<button type="button" class="sort" title="Ordenar por ${e(nombre.toLowerCase())}">${e(nombre)}<span class="sort-arrow" aria-hidden="true"></span></button>`;
    th.querySelector('button').addEventListener('click',()=>ordenar(i,th.getAttribute('aria-sort')==='ascending'?'desc':'asc'));
  });
  input.addEventListener('input',aplicar);
  barra.addEventListener('click',ev=>{const c=ev.target.closest('.chip-filter');if(c){estado=c.dataset.estado;aplicar();}});
  vacio.addEventListener('click',ev=>{if(ev.target.closest('[data-limpiar]')){input.value='';estado='';aplicar();input.focus();}});
  if(pref.orden){const i=[...head.cells].findIndex(th=>normalizar(th.textContent)===normalizar(pref.orden));if(i>=0)ordenar(i,'desc');}
  else if(Number.isInteger(pref.col) && head.cells[pref.col])ordenar(pref.col,pref.dir||'asc');
  aplicar();
}
pageHooks.push(page=>{
  const inicial=state.tablaInicial;state.tablaInicial=null;
  document.querySelectorAll('#app .card table').forEach((t,i)=>mejorarTabla(t,`tabla:${page}:${i}`,i===0&&inicial?{texto:'',estado:inicial.filtro||'',orden:inicial.orden||''}:null));
});

/* ---------- Leyenda de campos obligatorios (heurística 5) ---------- */
pageHooks.push(()=>{
  document.querySelectorAll('#app form').forEach(f=>{
    if(f.querySelector('.form-legend') || f.classList.contains('tools') || f.id==='chat-form' || !f.querySelector('[required]'))return;
    f.insertAdjacentHTML('afterbegin','<p class="form-legend">Los campos con <span class="req">*</span> son obligatorios.</p>');
  });
});

/* ---------- Ayuda contextual (heurística 10) ---------- */
const atajos=[['?','Abrir o cerrar esta ayuda'],['/','Buscar en la tabla de la página'],['A','Abrir el asistente'],['I','Ir al inicio'],['Esc','Cerrar ventanas y paneles']];
const ayudaComun={faq:[['¿Olvidé mi contraseña?','Pídele a la coordinación de prácticas que te asigne una nueva. Después puedes cambiarla desde tu nombre, arriba a la derecha → Cambiar contraseña.'],['¿Qué significa cada estado?','Pendiente: aún no entregas. En revisión: tu profesor todavía no la revisa. Corrección solicitada: debes subir una versión corregida. Aprobado: listo.']]};
const ayudas={
  mipanel:{titulo:'Mi panel',resumen:'Tu resumen personal: qué te falta entregar, qué está en revisión y qué ya está aprobado.',pasos:['Revisa si tienes avisos rojos: son requisitos fuera de plazo.','Mira la tabla "Mis requisitos y plazos": está ordenada por urgencia.','Pulsa "Entregar" o "Subir corrección" en la fila que quieras.','Elige el archivo (PDF, DOCX o XLSX, máximo 10 MB) y pulsa "Entregar documento".'],faq:[['¿Me equivoqué de archivo?','Justo después de entregar aparece "Deshacer". También puedes ir al historial y pulsar "Eliminar entrega" mientras no esté revisada.'],['¿Por qué no puedo entregar un requisito?','Si ya está aprobado no admite más versiones. Si está en revisión, espera la decisión de tu profesor.']]},
  resumen:{titulo:'Vista general',resumen:'Lo que requiere tu atención hoy en las prácticas que tienes a cargo.',pasos:['Lee el cuadro "Requiere tu atención": cada aviso tiene un botón que te lleva directo.','Usa los accesos rápidos para las tareas más comunes.','Abre una práctica con "Ver seguimiento" para revisar sus entregas.'],faq:[['¿Qué cuenta como "fuera de plazo"?','Un requisito sin entregar, o con una corrección pendiente, cuya fecha límite ya pasó.'],['¿Cómo reviso una entrega?','Abre la práctica, baja hasta "Revisar entregas", elige la versión y la decisión, y confirma.']]},
  indicadores:{titulo:'Indicadores',resumen:'El avance de cada práctica: aprobados, pendientes, por revisar y atrasos.',pasos:['Usa los botones de estado para filtrar, por ejemplo "Con atraso".','Pulsa el título de una columna para ordenar.','Pulsa "Ver" para abrir el seguimiento de una práctica.'],faq:[['¿Por qué una práctica sale "Sin requisitos"?','Todavía no se le aplicó una plantilla de requisitos, así que no se puede medir su avance.']]},
  practicas:{titulo:'Prácticas y documentos',resumen:'Busca una práctica y abre su seguimiento.',pasos:['Escribe el nombre o identificador del estudiante, o filtra por asignatura y período.','Pulsa "Buscar".','Pulsa "Ver seguimiento" para ver requisitos, entregas y revisiones.'],faq:[['¿No aparece una práctica?','Solo ves las prácticas asignadas a tu cuenta. Si falta alguna, avisa a la coordinación.']]},
  detalle:{titulo:'Seguimiento de la práctica',resumen:'Requisitos, plazos, entregas y revisiones de una práctica.',pasos:()=>role('ESTUDIANTE')?['Elige el requisito en "Entregar documento".','Selecciona el archivo y pulsa "Entregar documento".','Revisa el historial: ahí verás la revisión y el comentario de tu profesor.']:role('PROFESOR_COLEGIO')?['Lee los requisitos y el avance de tu practicante.','Completa "Evaluación del profesor de colegio" con fortalezas y aspectos por mejorar.','Pulsa "Registrar evaluación".']:['Descarga la entrega desde el historial y revísala.','En "Revisar entregas" elige la versión y la decisión. Si pides una corrección, escribe qué debe cambiar.','Confirma. Si te equivocas, pulsa "Deshacer" en el aviso o "Deshacer revisión" en el historial.'],faq:[['¿Puedo deshacer una revisión?','Sí, mientras el estudiante no haya subido una versión nueva.'],['¿Se pierden las versiones anteriores?','No. Todas las versiones y revisiones quedan en el historial.']]},
  materiales:{titulo:'Material de consulta',resumen:'Documentos que el asistente usa para responder a los estudiantes.',pasos:['Completa título, asignatura y período, y elige el archivo (PDF con texto, TXT o MD).','Pulsa "Cargar y procesar": quedará como borrador.','Revisa el texto extraído con "Ver texto" y pulsa "Publicar".'],faq:[['¿Por qué no se lee mi PDF?','Si es una imagen escaneada no tiene texto. Súbelo en una versión con texto seleccionable.']]},
  asistente:{titulo:'Asistente',resumen:'Responde con el material publicado por tus profesores y, si eres estudiante, con tus propios plazos.',pasos:['Escribe una pregunta corta, por ejemplo "¿Cuál es la asistencia mínima?".','Lee la respuesta y sus fuentes.','Si no encuentra respaldo te lo dirá: prueba con otras palabras o consulta a tu profesor.'],faq:[['¿El asistente inventa respuestas?','No. Solo usa el material publicado y siempre indica la fuente.']]},
  plantillas:{titulo:'Requisitos y períodos',resumen:'Define los requisitos y plazos de cada asignatura y período.',pasos:['Elige asignatura, año y semestre, y pulsa "Consultar".','Agrega o edita requisitos con su fecha límite.','Las prácticas nuevas copian esta plantilla. En una práctica existente, usa "Aplicar plantilla".'],faq:[]},
  usuarios:{titulo:'Cuentas y roles',resumen:'Crea cuentas y activa o desactiva el acceso.',pasos:['Registra primero al estudiante en "Estudiantes".','Crea la cuenta con su correo, una contraseña inicial y el rol.','Para quitar el acceso usa "Desactivar": no se borra ningún dato.'],faq:[]},
  estudiantes:{titulo:'Estudiantes',resumen:'Antecedentes de cada estudiante.',pasos:['Completa los datos y pulsa "Registrar".','Usa "Editar" para corregir datos sin perder sus prácticas.'],faq:[]},
  centros:{titulo:'Centros de práctica',resumen:'Establecimientos y sus contactos.',pasos:['Completa los datos y pulsa "Registrar".','Usa "Editar" para mantener los contactos al día.'],faq:[]},
  cuenta:{titulo:'Cambiar contraseña',resumen:'Cambia tu contraseña de acceso.',pasos:['Escribe tu contraseña actual.','Escribe la nueva dos veces (10 a 60 caracteres).','Pulsa "Actualizar contraseña".'],faq:[]},
};
function renderAyuda() {
  const a=ayudas[state.page]||{titulo:pages[state.page]?.label||'Ayuda',resumen:'',pasos:[],faq:[]};
  const pasos=typeof a.pasos==='function'?a.pasos():a.pasos;
  const faq=[...(a.faq||[]),...ayudaComun.faq];
  return `<div class="help-head"><div><span class="eyebrow">AYUDA</span><h2 id="help-title">${e(a.titulo)}</h2></div><button type="button" class="chat-close help-close" data-help-close aria-label="Cerrar ayuda">${icon('close')}</button></div>
    <div class="help-body">${a.resumen?`<p>${e(a.resumen)}</p>`:''}
    ${pasos.length?`<h3>Paso a paso</h3><ol class="help-steps">${pasos.map(p=>`<li>${e(p)}</li>`).join('')}</ol>`:''}
    <h3>Preguntas frecuentes</h3>${faq.map(([q,r])=>`<details><summary>${e(q)}</summary><p>${e(r)}</p></details>`).join('')}
    <h3>Atajos de teclado</h3><ul class="help-keys">${atajos.map(([k,t])=>`<li><kbd>${e(k)}</kbd>${e(t)}</li>`).join('')}</ul>
    <div class="help-actions"><button type="button" class="button secondary" data-start-tour>Ver guía de bienvenida</button><button type="button" class="button secondary" data-open-chat>Preguntar al asistente</button></div>
    <p class="hint">¿Sigues con dudas? Escribe a la coordinación de prácticas.</p></div>`;
}
function abrirAyuda() {
  let panel=$('#help-panel');
  if(!panel){panel=document.createElement('aside');panel.id='help-panel';panel.className='help-panel';panel.setAttribute('role','dialog');panel.setAttribute('aria-labelledby','help-title');document.body.appendChild(panel);}
  panel.innerHTML=renderAyuda();panel.hidden=false;$('#help-button').setAttribute('aria-expanded','true');
  panel.querySelector('.help-close').focus();
}
function cerrarAyuda() { const p=$('#help-panel');if(!p || p.hidden)return false;p.hidden=true;$('#help-button').setAttribute('aria-expanded','false');return true; }
const alternarAyuda=()=>{if(!cerrarAyuda())abrirAyuda();};
// Si la ayuda está abierta al cambiar de página, se actualiza con la ayuda de la nueva página.
pageHooks.push(()=>{const p=$('#help-panel');if(p && !p.hidden)p.innerHTML=renderAyuda();});

/* ---------- Guía de bienvenida (heurística 10) ---------- */
function pasosGuia() {
  const comunes=[
    ['home','Bienvenida a Prácticas UBB','Aquí sigues todo el proceso de práctica: requisitos, plazos, entregas, revisiones y evaluaciones.'],
    ['clipboard','El menú de la izquierda','Te lleva a cada sección. La sección donde estás queda marcada, y arriba ves la ruta (por ejemplo, "Gestión de prácticas / Mi panel").'],
  ];
  const rol=role('ESTUDIANTE')?[['send','Entrega tus documentos','En "Mi panel" ves qué te falta, ordenado por urgencia. Pulsa "Entregar" en el requisito y sube tu archivo. Si te equivocas, puedes deshacerlo.']]
    :role('PROFESOR_COLEGIO')?[['edit','Evalúa a tus practicantes','Abre el seguimiento de cada practicante y registra fortalezas y aspectos por mejorar.']]
    :[['inbox','Revisa lo urgente primero','En el inicio verás las entregas por revisar y los atrasos. Cada aviso tiene un botón que te lleva directo. Antes de aprobar te pediremos confirmar, y podrás deshacerlo.']];
  return [...comunes,...rol,['chat','Ayuda siempre a mano','El botón "? Ayuda" (arriba) explica cada página paso a paso. El botón verde (abajo a la derecha) abre el asistente. Puedes volver a ver esta guía desde tu nombre → "Ver guía de bienvenida".']];
}
function abrirGuia() {
  cerrarAyuda();$('#tour')?.remove();
  const pasos=pasosGuia();let i=0;const previo=document.activeElement;
  const capa=document.createElement('div');capa.id='tour';capa.className='modal-backdrop';
  const dibujar=()=>{
    const [ic,t,txt]=pasos[i];
    capa.innerHTML=`<div class="modal tour" role="dialog" aria-modal="true" aria-labelledby="tour-title"><div class="modal-icon">${icon(ic,24)}</div><p class="tour-step">Paso ${i+1} de ${pasos.length}</p><h2 id="tour-title">${e(t)}</h2><p>${e(txt)}</p><div class="tour-dots" aria-hidden="true">${pasos.map((_,j)=>`<span class="${j===i?'on':''}"></span>`).join('')}</div><div class="modal-actions"><button type="button" class="button secondary" data-tour="skip">${i===pasos.length-1?'Cerrar':'Omitir guía'}</button>${i>0?'<button type="button" class="button secondary" data-tour="prev">Anterior</button>':''}<button type="button" class="button" data-tour="next">${i===pasos.length-1?'Empezar':'Siguiente'}</button></div></div>`;
    capa.querySelector('[data-tour="next"]').focus();
  };
  const cerrar=()=>{capa.remove();document.removeEventListener('keydown',teclas,true);guardar(`guiaVista:${state.usuario?.correo}`,true);previo?.focus?.();};
  const teclas=ev=>{if(ev.key==='Escape'){ev.stopPropagation();cerrar();}if(ev.key==='ArrowRight' && i<pasos.length-1){i++;dibujar();}if(ev.key==='ArrowLeft' && i>0){i--;dibujar();}};
  capa.addEventListener('click',ev=>{const b=ev.target.closest('[data-tour]');if(!b)return;const a=b.dataset.tour;if(a==='skip')cerrar();else if(a==='prev'){i--;dibujar();}else if(i===pasos.length-1)cerrar();else{i++;dibujar();}});
  document.addEventListener('keydown',teclas,true);document.body.appendChild(capa);dibujar();
}
// Se muestra sola la primera vez que cada persona entra en este equipo.
let guiaRevisada=false;
pageHooks.push(()=>{
  if(guiaRevisada || !state.usuario)return;guiaRevisada=true;
  if(!leer(`guiaVista:${state.usuario.correo}`,false))setTimeout(abrirGuia,400);
});

/* ---------- Preferencias (heurística 7: personalización) ---------- */
function aplicarTexto() {
  const grande=!!leer('textoGrande',false);
  document.documentElement.classList.toggle('text-lg',grande);
  $('#pref-text')?.setAttribute('aria-checked',String(grande));
  if($('#pref-text'))$('#pref-text').textContent=grande?'Texto más grande ✓':'Texto más grande';
}
aplicarTexto();

/* ---------- Sesión: botón de ayuda visible solo con sesión iniciada ---------- */
sessionHooks.push(()=>{
  const conSesion=!!state.usuario && !document.body.classList.contains('signed-out');
  $('#help-button').hidden=!conSesion;
  if(!conSesion){cerrarAyuda();$('#tour')?.remove();guiaRevisada=false;}
  aplicarTexto();
});

/* ---------- Clics ---------- */
document.addEventListener('click',async ev=>{
  const b=ev.target.closest('button');if(!b)return;
  // Los botones de avisos pueden abrir una tabla ya filtrada u ordenada.
  if(b.dataset.filtro!==undefined || b.dataset.orden!==undefined)state.tablaInicial={filtro:b.dataset.filtro||'',orden:b.dataset.orden||''};
  if(b.id==='help-button' || b.hasAttribute('data-open-help')){alternarAyuda();return;}
  if(b.hasAttribute('data-help-close')){cerrarAyuda();$('#help-button').focus();return;}
  if(b.hasAttribute('data-start-tour') || b.id==='pref-tour'){abrirGuia();return;}
  if(b.id==='pref-text'){guardar('textoGrande',!leer('textoGrande',false));aplicarTexto();notice(leer('textoGrande',false)?'Texto más grande activado. Se recordará en este equipo.':'Tamaño de texto normal.');return;}
  if(b.hasAttribute('data-open-chat') && typeof openWidget==='function'){
    cerrarAyuda();if($('#chat-widget')?.hidden)await openWidget();
    if(b.dataset.question)await askWidget(b.dataset.question);
  }
},true);

/* ---------- Atajos de teclado (heurística 7) ---------- */
document.addEventListener('keydown',ev=>{
  if(ev.key==='Escape' && cerrarAyuda()){$('#help-button').focus();return;}
  if(!state.usuario || ev.ctrlKey || ev.metaKey || ev.altKey || escribiendo(ev.target) || document.querySelector('.modal-backdrop'))return;
  if(ev.key==='?'){ev.preventDefault();alternarAyuda();}
  else if(ev.key==='/'){const campo=document.querySelector('#app .table-search input, #app input[type="search"]');if(campo){ev.preventDefault();campo.focus();}}
  else if(ev.key.toLowerCase()==='a' && typeof openWidget==='function' && $('#chat-fab') && !$('#chat-fab').hidden){ev.preventDefault();openWidget();}
  else if(ev.key.toLowerCase()==='i'){ev.preventDefault();go(homePage());}
});
