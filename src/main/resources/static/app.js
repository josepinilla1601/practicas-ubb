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
let homePage = () => 'resumen';

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
// Íconos de línea propios (sin librerías externas). Heredan el color del texto.
const iconPaths={
  home:'<path d="M3 10.5 12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z"/>',
  chart:'<path d="M4 20V11M10 20V5M16 20v-6M2 20h20"/>',
  clipboard:'<rect x="5" y="4" width="14" height="17" rx="2"/><path d="M9 3h6v3H9zM9 11h6M9 15h4"/>',
  users:'<circle cx="9" cy="8" r="3.5"/><path d="M2.5 20a6.5 6.5 0 0 1 13 0M16 4.6a3.5 3.5 0 0 1 0 6.8M18 14.2A6 6 0 0 1 21.5 20"/>',
  school:'<path d="M3 21h18M5 21V10l7-5 7 5v11M10 21v-5h4v5"/>',
  calendar:'<rect x="3" y="5" width="18" height="16" rx="2"/><path d="M3 10h18M8 3v4M16 3v4"/>',
  book:'<path d="M5 4h11a3 3 0 0 1 3 3v13H8a3 3 0 0 1-3-3z"/><path d="M5 17a3 3 0 0 1 3-3h11"/>',
  chat:'<path d="M4 5h16v11H9l-5 4z"/><path d="M8 9h8M8 12h5"/>',
  shield:'<path d="M12 3 4 6v6c0 4.5 3.4 8 8 9 4.6-1 8-4.5 8-9V6z"/><path d="m9 12 2 2 4-4"/>',
  mail:'<rect x="3" y="5" width="18" height="14" rx="2"/><path d="m3 7 9 6 9-6"/>',
  lock:'<rect x="4" y="10" width="16" height="11" rx="2"/><path d="M8 10V7a4 4 0 0 1 8 0v3"/>',
  eye:'<path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12z"/><circle cx="12" cy="12" r="3"/>',
  chevron:'<path d="m6 9 6 6 6-6"/>',
  alert:'<path d="M12 3 2 20h20z"/><path d="M12 10v4M12 17h.01"/>',
  check:'<circle cx="12" cy="12" r="9"/><path d="m8 12 3 3 5-6"/>',
  inbox:'<path d="M3 13h5l2 3h4l2-3h5"/><path d="M5 5h14l2 8v6H3v-6z"/>',
  edit:'<path d="M4 20h4L19 9l-4-4L4 16z"/><path d="m13.5 6.5 4 4"/>',
  clock:'<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>',
  send:'<path d="M22 2 11 13M22 2l-7 20-4-9-9-4z"/>',
  close:'<path d="M6 6l12 12M18 6 6 18"/>',
  arrow:'<path d="M5 12h14M13 6l6 6-6 6"/>',
};
const icon=(name,size=18)=>`<svg class="icon" viewBox="0 0 24 24" width="${size}" height="${size}" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${iconPaths[name]||''}</svg>`;
const statTile=(iconName,value,label)=>`<div class="stat">${icon(iconName,20)}<div class="number">${value||0}</div><div class="label">${e(label)}</div></div>`;
const pageIcons={mipanel:'home',resumen:'home',indicadores:'chart',practicas:'clipboard',estudiantes:'users',centros:'school',plantillas:'calendar',materiales:'book',asistente:'chat',usuarios:'shield'};
// Se ejecutan al iniciar o cerrar sesión y en cada cambio de página (por ejemplo, el asistente flotante).
const sessionHooks=[];
const runSessionHooks=()=>sessionHooks.forEach(hook=>{try{hook();}catch(err){console.error(err);}});
// Secciones del menú lateral. Las páginas de la cuenta personal van en el menú de perfil, no aquí.
const navGroups=[
  ['Inicio',['mipanel','resumen','indicadores']],
  ['Gestión académica',['practicas','estudiantes','centros','plantillas']],
  ['Apoyo docente',['materiales','asistente']],
  ['Administración',['usuarios']],
];
// Grupos plegados por el usuario. Es una preferencia de este navegador: si no se puede guardar, se ignora.
let collapsedGroups=new Set();
try { collapsedGroups=new Set(JSON.parse(localStorage.getItem('navPlegados')||'[]')); } catch { collapsedGroups=new Set(); }
function toggleGroup(title) {
  collapsedGroups.has(title)?collapsedGroups.delete(title):collapsedGroups.add(title);
  try { localStorage.setItem('navPlegados',JSON.stringify([...collapsedGroups])); } catch { /* sin almacenamiento disponible */ }
  renderNav();
}
const accountPages=['cuenta'];
const roleLabels={COORDINADOR:'Coordinación',PROFESOR_UBB:'Profesor UBB',PROFESOR_COLEGIO:'Profesor de colegio',ESTUDIANTE:'Estudiante'};
// Iniciales para el avatar, sin contar palabras cortas como "de" o "la".
const initials=name=>{const words=String(name||'').trim().split(/\s+/).filter(Boolean);const main=words.filter(w=>w.length>2);return (main.length?main:words).slice(0,2).map(w=>w[0]).join('').toUpperCase();};
function visibleInNav(key) {
  const page=pages[key];
  if(!page || !page.label || accountPages.includes(key) || !role(...page.roles))return false;
  // El estudiante tiene su propio panel de inicio; la vista general es para docentes y coordinación.
  return !(key==='resumen' && role('ESTUDIANTE') && pages.mipanel);
}
function renderNav() {
  const listed=new Set(navGroups.flatMap(([,keys])=>keys));
  const extra=Object.keys(pages).filter(key=>!listed.has(key));
  const groups=navGroups.map(([title,keys],i)=>[title,i===0?keys.concat(extra):keys]);
  $('nav').innerHTML=groups.map(([title,keys])=>{
    const items=keys.filter(visibleInNav);
    if(!items.length)return '';
    // El grupo de la página abierta siempre queda desplegado.
    const open=!collapsedGroups.has(title) || items.includes(state.page);
    const links=items.map(key=>`<button data-page="${key}" class="nav-link ${key===state.page?'active':''}" ${key===state.page?'aria-current="page"':''}>${icon(pageIcons[key]||'clipboard')}<span>${e(pages[key].label)}</span></button>`).join('');
    return `<div class="nav-group ${open?'':'collapsed'}"><button type="button" class="nav-label" data-group="${e(title)}" aria-expanded="${open}">${e(title)}${icon('chevron',14)}</button><div class="nav-items">${links}</div></div>`;
  }).join('');
  const u=state.usuario;
  $('#profile').hidden=!u;
  runSessionHooks();
  if(!u){closeProfileMenu();return;}
  $('#avatar').textContent=initials(u.nombre);
  $('#identity').textContent=u.nombre;
  $('#role-label').textContent=roleLabels[u.rol]||u.rol;
  $('#menu-name').textContent=u.nombre;
  $('#menu-email').textContent=u.correo;
}
function closeProfileMenu() {
  const menu=$('#profile-menu');if(!menu)return;
  menu.hidden=true;$('#profile-button').setAttribute('aria-expanded','false');
}
function toggleProfileMenu() {
  const menu=$('#profile-menu');const open=menu.hidden;
  menu.hidden=!open;$('#profile-button').setAttribute('aria-expanded',String(open));
  if(open)menu.querySelector('[role="menuitem"]').focus();
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
  navigation++;document.body.classList.add('signed-out');$('nav').innerHTML='';$('#profile').hidden=true;closeProfileMenu();runSessionHooks();
  const features=[['clipboard','Requisitos, plazos y entregas de cada práctica'],['check','Revisión de entregas con historial de versiones'],['chat','Asistente con el material de tus profesores']];
  $('#app').innerHTML=`<div class="login-shell">
    <section class="login-brand">
      <div class="login-logo" aria-hidden="true">P</div>
      <h1>Prácticas <span>UBB</span></h1>
      <p class="login-sub">Pedagogía en Educación Matemática</p>
      <span class="login-rule" aria-hidden="true"></span>
      <ul class="login-features">${features.map(([i,t])=>`<li>${icon(i,20)}<span>${e(t)}</span></li>`).join('')}</ul>
      <p class="login-foot">Plataforma de seguimiento de la formación práctica · Universidad del Bío-Bío</p>
    </section>
    <section class="login-panel">
      <h2>Iniciar sesión</h2>
      <p class="login-lead">Ingresa con la cuenta asignada por la coordinación.</p>
      <form id="login-form" novalidate>
        <label class="field">Correo<span class="input-icon">${icon('mail')}<input name="username" type="email" required maxlength="150" autocomplete="username" placeholder="nombre@correo.cl"></span></label>
        <label class="field">Contraseña<span class="input-icon">${icon('lock')}<input name="password" type="password" required maxlength="60" autocomplete="current-password"><button type="button" class="toggle-pass" data-toggle-pass aria-label="Mostrar contraseña" aria-pressed="false">${icon('eye')}</button></span></label>
        <button class="button login-submit" type="submit">Entrar al sistema</button>
      </form>
      <p class="login-help">¿No tienes cuenta o no recuerdas tu contraseña? Solicítalo a la coordinación de prácticas.</p>
    </section>
  </div>`;
  onForm('#login-form',async form=>{
    if(!form.elements.username.value.trim() || !form.elements.password.value)throw new Error('Ingresa tu correo y tu contraseña.');
    await api('/login',{method:'POST',body:new URLSearchParams(new FormData(form))});
    await refreshSession();document.body.classList.remove('signed-out');$('#notice').hidden=true;await load();await go(homePage());
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
registerPage('cuenta','Cambiar contraseña',allRoles,()=>heading('Cambiar contraseña','Necesitas conocer tu contraseña actual.')+
  card('Credenciales',`<form id="password-form">${field('Contraseña actual','actual','password',true,60)}${field('Nueva contraseña (10–60 caracteres)','nueva','password',true,60)}${field('Confirmar nueva contraseña','confirmacion','password',true,60)}${button('Actualizar contraseña','type="submit"')}</form>`),()=>{
    $('#password-form').elements.nueva.autocomplete='new-password';$('#password-form').elements.confirmacion.autocomplete='new-password';
    onForm('#password-form',async form=>{const d=formData(form);if(d.nueva!==d.confirmacion)throw new Error('Las contraseñas nuevas no coinciden.');await send('/cuenta/clave',{actual:d.actual,nueva:d.nueva});form.reset();notice('Contraseña actualizada.');});
});
document.addEventListener('keydown',event=>{
  if(event.key==='Escape' && !$('#profile-menu').hidden){closeProfileMenu();$('#profile-button').focus();}
});
document.addEventListener('click',async event=>{
  if(!event.target.closest('#profile'))closeProfileMenu();
  const target=event.target.closest('button');if(!target)return;
  if(target.id==='profile-button'){toggleProfileMenu();return;}
  if(target.dataset.group){toggleGroup(target.dataset.group);return;}
  if(target.hasAttribute('data-toggle-pass')){
    const input=target.parentElement.querySelector('input');const show=input.type==='password';
    input.type=show?'text':'password';target.setAttribute('aria-pressed',String(show));target.setAttribute('aria-label',show?'Ocultar contraseña':'Mostrar contraseña');return;
  }
  if(target.closest('#profile-menu'))closeProfileMenu();
  try {
    if(target.dataset.page){$('#notice').hidden=true;await go(target.dataset.page);}
    else if(target.dataset.detail){$('#notice').hidden=true;await go('detalle',target.dataset.detail);}
    else if(target.dataset.action && actions[target.dataset.action]){target.disabled=true;await actions[target.dataset.action](target);}
    else if(target.id==='logout'){await api('/logout',{method:'POST'});state.usuario=null;await refreshSession();renderLogin();}
  } catch(err) {notice(err.message,true);} finally {target.disabled=false;}
});
document.addEventListener('DOMContentLoaded',async()=>{
  try {await refreshSession();if(!state.usuario){renderLogin();return;}await load();await go(homePage());}
  catch(err){$('#app').innerHTML=empty('No se pudo iniciar','Comprueba la conexión con el servidor y MySQL.');notice(err.message,true);}
});
