'use strict';

const state = {usuario:null, csrf:'', csrfHeader:'X-CSRF-TOKEN', page:'resumen', estudiantes:[], centros:[], asignaturas:[], practicas:[], resumen:{}};
const $ = selector => document.querySelector(selector);
const e = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const pages = {};
const actions = {};
const detailSections = [];
const detailBindings = [];
const role = (...roles) => !!state.usuario && roles.includes(state.usuario.rol);
const coord = () => role('COORDINADOR');

async function api(path, options={}) {
  const headers = {...options.headers};
  if(options.body && !(options.body instanceof FormData) && !(options.body instanceof URLSearchParams)) headers['Content-Type']='application/json';
  if(options.method && !['GET','HEAD'].includes(options.method)) headers[state.csrfHeader]=state.csrf;
  const response = await fetch('/api'+path, {...options, headers});
  let data;
  try { data=await response.json(); } catch { data={mensaje:'El servidor no devolvió una respuesta válida.'}; }
  if(!response.ok) {
    if(response.status===401 && path!=='/login') { state.usuario=null; renderLogin(); }
    throw new Error((data.mensaje || 'No se pudo completar la operación.')+(data.detalles?.length?' '+data.detalles.join(' · '):''));
  }
  return data;
}
const send = (path, body, method='POST') => api(path,{method,body:JSON.stringify(body)});
function notice(text, error=false) { const n=$('#notice');n.textContent=text;n.className=error?'error':'';n.hidden=false; }
function heading(title,description,action='') { return `<div class="heading"><div><div class="eyebrow">FORMACIÓN PRÁCTICA</div><h1>${e(title)}</h1><p>${e(description)}</p></div>${action}</div>`; }
function field(label,name,type='text',required=true,max=150,value='') {
  return `<label class="field">${e(label)}${required?' *':''}<input name="${name}" type="${type}" ${required?'required':''} maxlength="${max}" value="${e(value)}" ${type==='password'?'autocomplete="current-password"':''}></label>`;
}
function selectField(label,name,items,required=true,selected='') {
  return `<label class="field">${e(label)}${required?' *':''}<select name="${name}" ${required?'required':''}><option value="">Seleccionar…</option>${items.map(([value,text])=>`<option value="${e(value)}" ${String(value)===String(selected)?'selected':''}>${e(text)}</option>`).join('')}</select></label>`;
}
const options = (items,label) => items.map(x=>[x.id,label(x)]);
const empty = (title,subtitle='') => `<div class="empty"><strong>${e(title)}</strong><p>${e(subtitle)}</p></div>`;
const button = (text,attrs='',secondary=false) => `<button class="button ${secondary?'secondary':''}" ${attrs}>${e(text)}</button>`;
const card = (title,body) => `<section class="card"><div class="card-head"><h2>${e(title)}</h2></div><div class="card-body">${body}</div></section>`;
const formData = form => Object.fromEntries(new FormData(form));
const dateText = value => value ? String(value).replace('T',' ').substring(0,19) : '—';
function onForm(selector, handler) {
  const form=$(selector);if(!form)return;
  form.addEventListener('submit',async event=>{
    event.preventDefault();const submit=form.querySelector('button[type="submit"],button.button');if(submit)submit.disabled=true;
    try { await handler(form); } catch(err) { notice(err.message,true); } finally { if(submit)submit.disabled=false; }
  });
}
function registerPage(key,label,roles,render,bind=()=>{}) { pages[key]={label,roles,render,bind}; }
function renderNav() {
  $('nav').innerHTML=Object.entries(pages).filter(([key,page])=>page.label && role(...page.roles))
    .map(([key,page])=>`<button data-page="${key}" class="${key===state.page?'active':''}">${e(page.label)}</button>`).join('');
  $('#identity').textContent=state.usuario ? `${state.usuario.nombre} · ${state.usuario.rol.replaceAll('_',' ')}` : '';
  $('#logout').hidden=!state.usuario;
}
async function refreshSession() { Object.assign(state,await api('/sesion')); }
async function load() {
  const [asignaturas,practicas,resumen]=await Promise.all(['/asignaturas','/practicas','/resumen'].map(apiPath=>api(apiPath)));
  Object.assign(state,{asignaturas,practicas,resumen});
  if(coord()) [state.estudiantes,state.centros]=await Promise.all([api('/estudiantes'),api('/centros')]);
  else {state.estudiantes=[];state.centros=[];}
}
let navigation=0;
async function go(page,id) {
  const target=pages[page];if(!target || !role(...target.roles))return;
  const turn=++navigation;state.page=page;
  try {
    const html=await target.render(id);if(turn!==navigation || !state.usuario)return;
    $('#app').innerHTML=html;$('#breadcrumb').textContent=target.label || 'Seguimiento individual';renderNav();target.bind(id);
  } catch(err) { notice(err.message,true); }
}
async function saved(page,id) { await load();await go(page,id);notice('Información guardada correctamente.'); }
function renderLogin() {
  navigation++;document.body.classList.add('signed-out');$('nav').innerHTML='';$('#identity').textContent='';$('#logout').hidden=true;
  $('#app').innerHTML=heading('Bienvenido a Prácticas UBB','Ingresa con la cuenta asignada por la coordinación.')+
    card('Iniciar sesión',`<form id="login-form">${field('Correo','username','email')}${field('Contraseña','password','password',true,60)}${button('Ingresar','type="submit"')}<p class="hint">Si todavía no tienes cuenta, solicítala al coordinador.</p></form>`);
  onForm('#login-form',async form=>{
    await api('/login',{method:'POST',body:new URLSearchParams(new FormData(form))});
    await refreshSession();document.body.classList.remove('signed-out');$('#notice').hidden=true;await load();await go('resumen');
  });
}
function progress(p) {
  const total=Number(p.requeridos),rec=Number(p.recibidos);
  if(!total)return '<span class="pill">Sin requisitos definidos</span>';
  return `<span class="pill ${rec===total?'ok':'pending'}">${rec} de ${total} recibidos</span><div class="progress"><span style="width:${100*rec/total}%"></span></div>`;
}
function practicesTable(items) {
  if(!items.length)return empty('No hay prácticas para mostrar','Revisa los filtros o las asignaciones.');
  return `<div class="scroll"><table><thead><tr><th>ESTUDIANTE</th><th>ASIGNATURA / PERÍODO</th><th>DOCUMENTACIÓN</th><th>DETALLE</th></tr></thead><tbody>${items.map(p=>`<tr><td><strong>${e(p.estudiante)}</strong><small>${e(p.identificador)}</small></td><td>${e(p.asignatura)}<small>${p.anio}-${p.semestre} · ${e(p.centro||'Actividad universitaria')}</small></td><td>${progress(p)}</td><td>${button('Ver seguimiento',`data-detail="${p.id}"`,true)}</td></tr>`).join('')}</tbody></table></div>`;
}
const allRoles=['COORDINADOR','PROFESOR_UBB','PROFESOR_COLEGIO','ESTUDIANTE'];
registerPage('resumen','Vista general',allRoles,()=>heading('Una mirada a tus prácticas','Información visible según tu cuenta y tus asignaciones.')+
  `<div class="stats">${[['estudiantes','Estudiantes'],['centros','Centros'],['practicas','Prácticas'],['documentos','Archivos recibidos']].map(([key,label])=>`<div class="stat"><div class="number">${state.resumen[key]||0}</div><div class="label">${label}</div></div>`).join('')}</div>`+
  card('Seguimiento reciente',`<p>Recibido no equivale a aprobado. Una evaluación de desempeño es distinta de una revisión documental.</p>${practicesTable(state.practicas.slice(0,6))}`));

function studentFields(s={}) { return field('Identificador','identificador','text',true,30,s.identificador)+field('Nombres','nombres','text',true,100,s.nombres)+field('Apellidos','apellidos','text',true,100,s.apellidos)+field('Correo','correo','email',true,150,s.correo); }
function centerFields(c={}) { return field('Establecimiento','nombre','text',true,150,c.nombre)+field('Comuna','comuna','text',true,100,c.comuna)+field('Dirección','direccion','text',false,200,c.direccion)+field('Contacto','contacto','text',false,150,c.contacto)+field('Correo','correo','email',false,150,c.correo)+field('Teléfono','telefono','tel',false,40,c.telefono); }
registerPage('estudiantes','Estudiantes',['COORDINADOR'],()=>heading('Estudiantes','Antecedentes para vincular estudiantes a sus prácticas.')+
  `<div class="split">${card('Registrar estudiante',`<form id="student-form">${studentFields()}${button('Registrar','type="submit"')}</form>`)}${card('Estudiantes registrados',`<div class="scroll"><table><thead><tr><th>NOMBRE</th><th>IDENTIFICADOR</th><th>CORREO</th></tr></thead><tbody>${state.estudiantes.map(s=>`<tr><td>${e(s.nombres)} ${e(s.apellidos)}</td><td>${e(s.identificador)}</td><td>${e(s.correo)}</td></tr>`).join('')}</tbody></table></div>`)}</div>`,()=>onForm('#student-form',async f=>{await send('/estudiantes',formData(f));await saved('estudiantes');}));
registerPage('centros','Centros de práctica',['COORDINADOR'],()=>heading('Centros de práctica','Establecimientos y sus datos de contacto.')+
  `<div class="split">${card('Registrar centro',`<form id="center-form">${centerFields()}${button('Registrar','type="submit"')}</form>`)}${card('Centros registrados',`<div class="scroll"><table><thead><tr><th>NOMBRE</th><th>COMUNA</th><th>CONTACTO</th></tr></thead><tbody>${state.centros.map(c=>`<tr><td>${e(c.nombre)}</td><td>${e(c.comuna)}</td><td>${e(c.contacto)}<small>${e(c.correo)}</small></td></tr>`).join('')}</tbody></table></div>`)}</div>`,()=>onForm('#center-form',async f=>{await send('/centros',formData(f));await saved('centros');}));

function practiceForm() { return card('Registrar práctica',`<form id="practice-form">${selectField('Estudiante','estudianteId',options(state.estudiantes,x=>`${x.nombres} ${x.apellidos}`))}${selectField('Asignatura','asignaturaId',options(state.asignaturas,x=>x.nombre))}${selectField('Centro','centroId',options(state.centros,x=>x.nombre),false)}<p id="center-hint" class="hint"></p><div class="row">${field('Año','anio','number',true,4,new Date().getFullYear())}${selectField('Semestre','semestre',[[1,'Primero'],[2,'Segundo']],true,2)}</div>${field('Profesor responsable (referencia)','profesorResponsable')}${button('Registrar práctica','type="submit"')}<p class="hint">El nombre de referencia no concede acceso: asigna la cuenta docente desde el seguimiento.</p></form>`); }
function bindPracticeForm() {
  const form=$('#practice-form');if(!form)return;
  const update=()=>{const a=state.asignaturas.find(x=>Number(x.id)===Number(form.elements.asignaturaId.value));form.elements.centroId.required=!!a?.requiere_centro;$('#center-hint').textContent=a?.requiere_centro?'Esta asignatura requiere un centro.':'El centro es opcional para esta asignatura.';};
  form.elements.asignaturaId.addEventListener('change',update);update();
  onForm('#practice-form',async f=>{const d=formData(f);for(const k of ['estudianteId','asignaturaId','anio','semestre'])d[k]=Number(d[k]);d.centroId=d.centroId?Number(d.centroId):null;const p=await send('/practicas',d);await saved('detalle',p.id);});
}
registerPage('practicas','Prácticas y documentos',allRoles,()=>heading('Prácticas y documentos','Consulta las prácticas que tienes autorizadas.')+
  (coord()?`<div class="split wide">${practiceForm()}${card('Prácticas',practicesTable(state.practicas))}</div>`:card('Prácticas',practicesTable(state.practicas))),bindPracticeForm);

function uploadForm(data) {
  if(!role('COORDINADOR','ESTUDIANTE'))return '';
  return card('Adjuntar documento',data.configurado?`<form id="upload-form">${selectField('Requisito','requisitoId',options(data.requisitos,x=>x.nombre))}<label class="field">Archivo *<input name="archivo" type="file" accept=".pdf,.docx,.xlsx" required><small>PDF, DOCX o XLSX. Máximo 10 MB.</small></label>${button('Guardar entrega','type="submit"')}<p>Los archivos anteriores se conservan.</p></form>`:empty('Sin requisitos definidos','La coordinación debe preparar los requisitos de la práctica.'));
}
function requirementTable(data) {
  return card('Requisitos',data.configurado?`<div class="scroll"><table><thead><tr><th>REQUISITO</th><th>ESTADO</th></tr></thead><tbody>${data.requisitos.map(r=>`<tr><td>${e(r.nombre)}</td><td><span class="pill">${e(r.estado)}</span></td></tr>`).join('')}</tbody></table></div>`:empty('Sin configuración','Cero requisitos no significa práctica completa.'));
}
function documentsTable(data) {
  return card('Archivos entregados',data.documentos.length?`<div class="scroll"><table><thead><tr><th>ARCHIVO</th><th>REQUISITO</th><th>FECHA</th></tr></thead><tbody>${data.documentos.map(d=>`<tr><td><a href="/api/documentos/${d.id}/descarga">${e(d.nombre_original)}</a></td><td>${e(d.requisito)}</td><td>${e(dateText(d.cargado_en))}</td></tr>`).join('')}</tbody></table></div>`:empty('Todavía no hay archivos'));
}
registerPage('detalle','',allRoles,async id=>{
  const data=await api(`/practicas/${id}/seguimiento`);state.detail=data;const p=data.practica;
  return button('← Volver a prácticas','data-page="practicas"',true)+heading(p.estudiante,`${p.asignatura} · ${p.anio}-${p.semestre}`,progress(p))+
    `<div class="meta"><div>Centro<strong>${e(p.centro||'No corresponde')}</strong></div><div>Profesor responsable<strong>${e(p.profesor_responsable)}</strong></div></div>`+
    `<div class="split wide">${uploadForm(data)}${requirementTable(data)}</div>`+documentsTable(data)+(await Promise.all(detailSections.map(render=>render(data)))).join('');
},id=>{
  onForm('#upload-form',async form=>{const file=form.elements.archivo.files[0];if(!file || file.size>10*1024*1024)throw new Error('Selecciona un archivo de hasta 10 MB.');const body=new FormData();body.append('archivo',file);await api(`/practicas/${id}/requisitos/${form.elements.requisitoId.value}/documentos`,{method:'POST',body});await saved('detalle',id);});
  detailBindings.forEach(bind=>bind(id));
});
registerPage('cuenta','Mi contraseña',allRoles,()=>heading('Cambiar contraseña','Necesitas conocer tu contraseña actual.')+
  card('Credenciales',`<form id="password-form">${field('Contraseña actual','actual','password',true,60)}${field('Nueva contraseña (10–60 caracteres)','nueva','password',true,60)}${field('Confirmar nueva contraseña','confirmacion','password',true,60)}${button('Actualizar contraseña','type="submit"')}</form>`),()=>{
    $('#password-form').elements.nueva.autocomplete='new-password';$('#password-form').elements.confirmacion.autocomplete='new-password';
    onForm('#password-form',async form=>{const d=formData(form);if(d.nueva!==d.confirmacion)throw new Error('Las contraseñas nuevas no coinciden.');await send('/cuenta/clave',{actual:d.actual,nueva:d.nueva});form.reset();notice('Contraseña actualizada.');});
});
document.addEventListener('click',async event=>{
  const target=event.target.closest('button');if(!target)return;
  try {
    if(target.dataset.page){$('#notice').hidden=true;await go(target.dataset.page);}
    else if(target.dataset.detail){$('#notice').hidden=true;await go('detalle',target.dataset.detail);}
    else if(target.dataset.action && actions[target.dataset.action]){target.disabled=true;await actions[target.dataset.action](target);}
    else if(target.id==='logout'){await api('/logout',{method:'POST'});state.usuario=null;await refreshSession();renderLogin();}
  } catch(err) {notice(err.message,true);} finally {target.disabled=false;}
});
document.addEventListener('DOMContentLoaded',async()=>{
  try {await refreshSession();if(!state.usuario){renderLogin();return;}await load();await go('resumen');}
  catch(err){$('#app').innerHTML=empty('No se pudo iniciar','Comprueba la conexión con el servidor y MySQL.');notice(err.message,true);}
});
