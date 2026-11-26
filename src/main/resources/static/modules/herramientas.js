'use strict';

// S3-08 a S3-11: carta de presentación en PDF, importación de estudiantes desde Excel,
// calendario mensual y "Ver como estudiante" para la coordinación.

/* ---------- S3-10: Calendario ---------- */
const nombresMes=['Enero','Febrero','Marzo','Abril','Mayo','Junio','Julio','Agosto','Septiembre','Octubre','Noviembre','Diciembre'];
const tiposEvento={PLAZO:['Plazo','review'],VENCIDO:['Plazo vencido','danger'],JORNADA:['Jornada','ok'],INICIO:['Inicio de práctica','pending'],TERMINO:['Término de práctica','pending']};
registerPage('calendario','Calendario',allRoles,async()=>{
  const hoy=new Date();state.calMes=state.calMes||{anio:hoy.getFullYear(),mes:hoy.getMonth()+1};
  const {anio,mes}=state.calMes;
  const data=await api(`/calendario?anio=${anio}&mes=${mes}`);state.calEventos=data.eventos;
  const primero=new Date(anio,mes-1,1);const dias=new Date(anio,mes,0).getDate();const inicio=(primero.getDay()+6)%7; // lunes primero
  const porDia={};data.eventos.forEach(ev=>{(porDia[ev.fecha]??=[]).push(ev);});
  const celdas=[];
  for(let i=0;i<inicio;i++)celdas.push('<div class="dia vacio" aria-hidden="true"></div>');
  for(let d=1;d<=dias;d++){
    const iso=`${anio}-${String(mes).padStart(2,'0')}-${String(d).padStart(2,'0')}`;const evs=porDia[iso]||[];
    celdas.push(`<button type="button" class="dia ${iso===data.hoy?'hoy':''} ${state.calDia===iso?'elegido':''} ${evs.length?'con-eventos':''}" data-action="cal-dia" data-fecha="${iso}" aria-label="${d} de ${nombresMes[mes-1]}${evs.length?`, ${evs.length} evento(s)`:''}"><span class="num">${d}</span><span class="marcas">${evs.slice(0,4).map(ev=>`<i class="${(tiposEvento[ev.tipo]||['',''])[1]}"></i>`).join('')}${evs.length>4?'<b>+</b>':''}</span></button>`);
  }
  const lista=(state.calDia?(porDia[state.calDia]||[]):data.eventos);
  const filas=lista.length?`<ul class="eventos">${lista.map(ev=>{const [l,c]=tiposEvento[ev.tipo]||[ev.tipo,''];return `<li><span class="evento-fecha">${e(fechaCorta(ev.fecha))}</span><span class="pill ${c}">${l}</span><div><strong>${e(ev.titulo)}</strong>${role('ESTUDIANTE')?'':`<small>${e(ev.estudiante||'')}</small>`}</div>${button('Ver práctica',`type="button" data-detail="${ev.practica_id}"`,true)}</li>`;}).join('')}</ul>`:empty(state.calDia?'Sin eventos este día':'Sin eventos este mes','Aquí aparecen los plazos, las jornadas de bitácora y el inicio y término de cada práctica.');
  return heading('Calendario','Plazos de entrega, jornadas registradas y fechas de cada práctica en un solo lugar.')+
    `<div class="cal-layout">${card(`${nombresMes[mes-1]} ${anio}`,`<div class="cal-nav">${button('← Mes anterior','type="button" data-action="cal-mover" data-paso="-1"',true)}${button('Hoy','type="button" data-action="cal-hoy"',true)}${button('Mes siguiente →','type="button" data-action="cal-mover" data-paso="1"',true)}</div>
      <div class="cal-semana" aria-hidden="true">${['Lun','Mar','Mié','Jue','Vie','Sáb','Dom'].map(d=>`<span>${d}</span>`).join('')}</div><div class="cal-grid">${celdas.join('')}</div>
      <div class="cal-leyenda">${Object.entries(tiposEvento).map(([,[l,c]])=>`<span><i class="${c}"></i>${l}</span>`).join('')}</div>`)}
      ${card(state.calDia?`Eventos del ${fechaCorta(state.calDia)}`:`Eventos de ${nombresMes[mes-1].toLowerCase()}`,(state.calDia?`<p>${button('Ver todo el mes','type="button" data-action="cal-dia" data-fecha=""',true)}</p>`:'')+filas)}</div>`;
});
actions['cal-mover']=async t=>{const {anio,mes}=state.calMes;const d=new Date(anio,mes-1+Number(t.dataset.paso),1);state.calMes={anio:d.getFullYear(),mes:d.getMonth()+1};state.calDia=null;await go('calendario');};
actions['cal-hoy']=async()=>{state.calMes=null;state.calDia=null;await go('calendario');};
actions['cal-dia']=async t=>{state.calDia=t.dataset.fecha&&state.calDia!==t.dataset.fecha?t.dataset.fecha:null;await go('calendario');};

/* ---------- S3-08: Carta de presentación en PDF ---------- */
pageHooks.push(page=>{
  if(page!=='detalle' || !role('COORDINADOR','PROFESOR_UBB','ESTUDIANTE') || !state.detail?.practica?.centro_id)return;
  $('#app .heading')?.insertAdjacentHTML('beforeend',`<a class="button secondary carta" href="api/practicas/${state.detail.practica.id}/carta.pdf" target="_blank" rel="noopener" title="Carta para presentar al practicante en el establecimiento">${icon('file',16)} Carta de presentación (PDF)</a>`);
});

/* ---------- S3-09: Importar estudiantes desde Excel ---------- */
pageHooks.push(page=>{
  if(page!=='estudiantes' || !coord() || $('#import-form'))return;
  const resultado=state.importacion;state.importacion=null;
  const detalle=resultado?`<div class="import-result"><p><strong>${e(resultado.mensaje)}</strong></p>${[...resultado.errores.map(x=>['danger',x]),...resultado.omitidos.map(x=>['pending',x])].map(([c,x])=>`<p><span class="pill ${c}">Fila ${x.fila}</span> ${e(x.motivo)}</p>`).join('')}</div>`:'';
  $('#app .heading')?.insertAdjacentHTML('afterend',card('Importar estudiantes desde Excel',`${detalle}<form id="import-form" class="import-form">
    <label class="field">Archivo Excel (.xlsx) o CSV${req}<input name="archivo" type="file" accept=".xlsx,.csv" required><small>Columnas: Identificador, Nombres, Apellidos y Correo. <a href="api/estudiantes/plantilla.xlsx">Descargar plantilla</a></small></label>
    <label class="check"><input type="checkbox" name="crearCuentas" id="import-cuentas"> Crear también sus cuentas de acceso</label>
    <label class="field" id="import-clave" hidden>Contraseña inicial para todas las cuentas (10 a 60 caracteres)${req}<input name="claveInicial" type="password" minlength="10" maxlength="60" autocomplete="new-password"></label>
    ${button('Importar estudiantes','type="submit"')}<p class="hint">Los estudiantes que ya existen se omiten, y las filas con errores se informan sin detener la importación.</p></form>`));
  const form=$('#import-form');
  form.elements.crearCuentas.addEventListener('change',ev=>{$('#import-clave').hidden=!ev.target.checked;form.elements.claveInicial.required=ev.target.checked;});
  onForm('#import-form',async f=>{
    const body=new FormData();body.append('archivo',f.elements.archivo.files[0]);
    if(f.elements.crearCuentas.checked){body.append('crearCuentas','true');body.append('claveInicial',f.elements.claveInicial.value);}
    const r=await api('/estudiantes/importar',{method:'POST',body});
    state.importacion=r;await load();await go('estudiantes');notice(r.mensaje,r.creados===0 && r.errores.length>0);
  },'Importando…');
});

/* ---------- S3-11: Ver como estudiante (solo lectura) ---------- */
pageHooks.push(page=>{
  if(page!=='estudiantes' || !coord())return;
  document.querySelectorAll('#app [data-action="edit-student"]').forEach(b=>{
    if(b.parentElement.querySelector('[data-action="ver-como"]'))return;
    b.insertAdjacentHTML('afterend',' '+button('Ver como estudiante',`type="button" data-action="ver-como" data-id="${b.dataset.id}"`,true));
  });
});
actions['ver-como']=async t=>{state.vistaPrevia=t.dataset.id;await go('vistaprevia');};
registerPage('vistaprevia','',['COORDINADOR'],async()=>{
  const data=await api(`/estudiantes/${state.vistaPrevia}/panel`);
  return `<div class="preview-banner">${icon('eye',18)}<span>Estás viendo el panel de <strong>${e(data.estudiante.nombres)} ${e(data.estudiante.apellidos)}</strong> tal como lo ve el estudiante. Es solo de lectura.</span>${button('Volver a estudiantes','type="button" data-page="estudiantes"',true)}</div><div class="solo-lectura">${panelEstudiante(data)}</div>`;
});
pageHooks.push(page=>{ if(page==='vistaprevia')$('#breadcrumb').textContent='Vista como estudiante'; });
