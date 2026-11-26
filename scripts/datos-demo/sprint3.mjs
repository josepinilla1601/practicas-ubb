// Datos de DEMOSTRACIÓN del Sprint 3: jornadas de bitácora, fichas de inscripción y una rúbrica MBE.
// Se ejecuta después de cargar.mjs. Cada persona actúa con su propia cuenta, como en el uso real.
// Si una práctica ya tiene jornadas o ficha, se omite esa parte.
//
// Uso:  node scripts/datos-demo/sprint3.mjs
//       APP_URL=https://pacheco.chillan.ubiobio.cl/bestpem node scripts/datos-demo/sprint3.mjs

const URL_APP = (process.env.APP_URL || 'http://localhost:8080') + '/api';
const CLAVE = 'Practicas2026demo';

async function entrar(correo, clave = CLAVE) {
  const cookies = {}; let csrf = '';
  const pedir = async (metodo, ruta, cuerpo) => {
    const headers = { Cookie: Object.entries(cookies).map(([k, v]) => `${k}=${v}`).join('; ') };
    if (metodo !== 'GET') headers['X-CSRF-TOKEN'] = csrf;
    let body = cuerpo;
    if (cuerpo && !(cuerpo instanceof URLSearchParams)) { headers['Content-Type'] = 'application/json'; body = JSON.stringify(cuerpo); }
    const res = await fetch(URL_APP + ruta, { method: metodo, headers, body });
    for (const c of res.headers.getSetCookie?.() || []) { const [par] = c.split(';'); const i = par.indexOf('='); cookies[par.slice(0, i)] = par.slice(i + 1); }
    const datos = await res.json().catch(() => ({}));
    if (!res.ok) throw new Error(`${correo}: ${metodo} ${ruta} → ${res.status}: ${datos.mensaje || ''}`);
    return datos;
  };
  csrf = (await pedir('GET', '/sesion')).csrf;
  await pedir('POST', '/login', new URLSearchParams({ username: correo, password: clave }));
  csrf = (await pedir('GET', '/sesion')).csrf;
  return pedir;
}
const dia = n => { const d = new Date(); d.setDate(d.getDate() - n); return d.toISOString().slice(0, 10); };
const practicaDe = async (api, nombre) => (await api('GET', '/practicas')).find(p => p.estudiante.startsWith(nombre));

// ---- Bitácora de Camila (PED) y Benjamín (PRO) ----
const jornadas = {
  'camila.soto@alumnos.test': ['Camila', [
    [21, '08:00', '13:00', '7° básico A', 'OBSERVACION', 'Observé dos clases de la profesora colaboradora sobre números enteros y apoyé el trabajo en grupos.', 'Noté que los estudiantes se confunden con la resta de negativos; pensé usar la recta numérica.'],
    [14, '08:00', '12:15', '7° básico A', 'AYUDANTIA', 'Apoyé la corrección de una guía de potencias y atendí dudas en los puestos.', 'Explicar con ejemplos concretos funcionó mejor que repetir la regla.'],
    [7, '08:00', '10:15', '7° básico A', 'CLASE', 'Realicé mi primera clase: ecuaciones de primer grado con balanza didáctica.', 'El inicio fue motivador, pero me faltó tiempo para el cierre. Debo planificar mejor los tiempos.'],
    [2, '10:30', '12:00', '7° básico B', 'CLASE', 'Clase de ecuaciones con trabajo en parejas y ticket de salida.', 'El ticket de salida mostró que 20 de 28 estudiantes lograron el objetivo.'],
  ]],
  'benjamin.araya@alumnos.test': ['Benjamín', [
    [10, '14:00', '17:00', '3° medio', 'OBSERVACION', 'Observé una clase de probabilidad condicional.', ''],
  ]],
};
for (const [correo, [nombre, lista]] of Object.entries(jornadas)) {
  const api = await entrar(correo); const p = await practicaDe(api, nombre);
  if ((await api('GET', `/practicas/${p.id}/bitacora`)).jornadas.length) { console.log(`  ${nombre}: ya tiene bitácora, se omite`); continue; }
  for (const [d, horaInicio, horaFin, curso, tipo, actividades, reflexion] of lista)
    await api('POST', `/practicas/${p.id}/bitacora`, { fecha: dia(d), horaInicio, horaFin, curso, tipo, actividades, reflexion });
  console.log(`  ${nombre}: ${lista.length} jornadas`);
}
// Patricia Muñoz visa tres jornadas de Camila y observa la última; Marcela Rojas deja retroalimentación.
{
  const munoz = await entrar('pmunoz@colegio.test'); const rojas = await entrar('mrojas@ubb.test');
  const p = await practicaDe(munoz, 'Camila');
  const js = (await munoz('GET', `/practicas/${p.id}/bitacora`)).jornadas.filter(j => j.estado === 'REGISTRADA').sort((a, b) => a.fecha.localeCompare(b.fecha));
  if (js.length === 4) {
    for (const j of js.slice(0, 3)) await munoz('POST', `/bitacora/${j.id}/visado`, { decision: 'VISADA' });
    await munoz('POST', `/bitacora/${js[3].id}/visado`, { decision: 'OBSERVADA', comentario: 'La clase fue en 7° B, pero el horario fue de 10:30 a 12:15. Corrige la hora de término.' });
    await rojas('POST', `/bitacora/${js[2].id}/comentario`, { comentario: 'Muy buena reflexión sobre los tiempos. Para la próxima clase, prueba con un cronograma visible en la pizarra.' });
    console.log('  Bitácora de Camila: 3 visadas, 1 observada y retroalimentación de la profesora');
  }
}

// ---- Fichas de inscripción ----
const fichas = [
  ['camila.soto@alumnos.test', 'Camila', { curso: '7° básico A y B', nivel: 'Educación básica', dias: 'Lunes y miércoles', horario: '08:00 a 13:00', fechaInicio: '2026-08-03', fechaTermino: '2026-12-04', horasSemanales: 10, observaciones: '' }, 'APROBADA'],
  ['benjamin.araya@alumnos.test', 'Benjamín', { curso: '3° medio A', nivel: 'Educación media', dias: 'Martes y jueves', horario: '14:00 a 18:00', fechaInicio: '2026-08-04', fechaTermino: '2026-12-10', horasSemanales: 8, observaciones: '' }, 'ENVIADA'],
  ['fernanda.henriquez@alumnos.test', 'Fernanda', { curso: '1° medio C', nivel: 'Educación media', dias: 'Viernes', horario: '08:00 a 12:00', fechaInicio: '2026-08-07', fechaTermino: '2026-11-27', horasSemanales: 4, observaciones: '' }, 'DEVUELTA'],
];
const coord = process.env.COORD_EMAIL && process.env.COORD_PASSWORD ? await entrar(process.env.COORD_EMAIL, process.env.COORD_PASSWORD) : null;
for (const [correo, nombre, ficha, meta] of fichas) {
  const api = await entrar(correo); const p = await practicaDe(api, nombre);
  if ((await api('GET', `/practicas/${p.id}/ficha`)).estado !== 'SIN_FICHA') { console.log(`  ${nombre}: ya tiene ficha, se omite`); continue; }
  await api('PUT', `/practicas/${p.id}/ficha`, ficha); await api('POST', `/practicas/${p.id}/ficha/enviar`, {});
  const colegio = await entrar(nombre === 'Benjamín' ? 'hvaldes@colegio.test' : 'pmunoz@colegio.test');
  if (meta === 'DEVUELTA') await colegio('POST', `/practicas/${p.id}/ficha/decision`, { accion: 'DEVOLVER', comentario: 'El curso asignado es 1° medio B, no C. Corrige el curso.' });
  if (meta === 'APROBADA') {
    await colegio('POST', `/practicas/${p.id}/ficha/decision`, { accion: 'CONFIRMAR', comentario: '' });
    if (coord) await coord('POST', `/practicas/${p.id}/ficha/decision`, { accion: 'APROBAR', comentario: '' });
  }
  console.log(`  Ficha de ${nombre}: ${meta === 'APROBADA' && !coord ? 'CONFIRMADA (falta que coordinación apruebe)' : meta}`);
}

// ---- Rúbrica MBE de Patricia Muñoz para Camila ----
{
  const munoz = await entrar('pmunoz@colegio.test'); const p = await practicaDe(munoz, 'Camila');
  if ((await munoz('GET', `/practicas/${p.id}/rubricas`)).length) console.log('  Camila: ya tiene rúbrica, se omite');
  else {
    const niveles = { A1: 4, A2: 3, A3: 3, B1: 4, B2: 3, B3: 3, C1: 3, C2: 3, C3: 2, D1: 4, D2: 4, D3: 3 };
    const r = await munoz('POST', `/practicas/${p.id}/rubricas`, { niveles, comentario: 'Destaca su dominio disciplinar y el buen clima de aula. Debe fortalecer la evaluación durante la clase.' });
    console.log('  Rúbrica de Camila: ' + r.mensaje);
  }
}
console.log('Listo.');
