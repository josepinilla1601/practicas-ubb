'use strict';

registerPage('usuarios','Cuentas y roles',['COORDINADOR'],async()=>{
  state.usuarios=await api('/usuarios');
  return heading('Cuentas y roles','Cada persona ingresa con su propia cuenta. Los docentes acceden a las prácticas asignadas.')+
    `<div class="split">${card('Crear cuenta',`<form id="user-form">${field('Nombre','nombre')}${field('Correo de acceso','correo','email')}${field('Contraseña inicial (10–60 caracteres)','clave','password',true,60)}${selectField('Rol','rol',[['ESTUDIANTE','Estudiante'],['PROFESOR_UBB','Profesor UBB'],['PROFESOR_COLEGIO','Profesor de colegio'],['COORDINADOR','Coordinador']])}${selectField('Estudiante registrado','estudianteId',options(state.estudiantes,s=>`${s.identificador} · ${s.nombres} ${s.apellidos}`),false)}<p class="hint">Primero registra los antecedentes del estudiante. Una cuenta por estudiante.</p>${button('Crear cuenta','type="submit"')}</form>`)}${card('Cuentas registradas',`<div class="scroll"><table><thead><tr><th>PERSONA</th><th>ROL</th><th>ESTADO</th><th>ACCIÓN</th></tr></thead><tbody>${state.usuarios.map(u=>`<tr><td>${e(u.nombre)}<small>${e(u.correo)}</small></td><td>${e(u.rol)}</td><td>${u.activo?'Activa':'Desactivada'}</td><td>${Number(u.id)===Number(state.usuario.id)?'Tu cuenta':button(u.activo?'Desactivar':'Activar',`data-action="toggle-user" data-id="${u.id}" data-active="${!u.activo}"`,true)}</td></tr>`).join('')}</tbody></table></div>`)}</div>`;
},()=>{
  const form=$('#user-form');form.elements.clave.autocomplete='new-password';
  const update=()=>{const student=form.elements.rol.value==='ESTUDIANTE';form.elements.estudianteId.disabled=!student;form.elements.estudianteId.required=student;if(!student)form.elements.estudianteId.value='';};
  form.elements.rol.addEventListener('change',update);update();
  onForm('#user-form',async f=>{const d=formData(f);d.estudianteId=d.estudianteId?Number(d.estudianteId):null;await send('/usuarios',d);await saved('usuarios');});
});
actions['toggle-user']=async target=>{await send(`/usuarios/${target.dataset.id}/activo`,{activo:target.dataset.active==='true'},'PATCH');await saved('usuarios');};
detailSections.push(async data=>{
  if(!coord())return '';
  const users=await api('/usuarios');const p=data.practica;
  return card('Asignar docentes',`<form id="assignment-form">${selectField('Profesor UBB','profesorUbbId',options(users.filter(u=>u.activo&&u.rol==='PROFESOR_UBB'),u=>u.nombre),true,p.profesor_ubb_id)}${selectField('Profesor de colegio','profesorColegioId',options(users.filter(u=>u.activo&&u.rol==='PROFESOR_COLEGIO'),u=>u.nombre),false,p.profesor_colegio_id)}${button('Guardar asignación','type="submit"')}<p class="hint">Dejar el colegio vacío elimina esa asignación. La documentación se conserva.</p></form>`);
});
detailBindings.push(id=>onForm('#assignment-form',async f=>{const d=formData(f);d.profesorUbbId=Number(d.profesorUbbId);d.profesorColegioId=d.profesorColegioId?Number(d.profesorColegioId):null;await send(`/practicas/${id}/docentes`,d,'PUT');await saved('detalle',id);}));
