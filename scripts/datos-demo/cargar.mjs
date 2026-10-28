// Carga datos de DEMOSTRACIÓN en una instancia local de Prácticas UBB usando su propia API.
// Todo pasa por las mismas validaciones que usa una persona en la página.
//
// Uso (con la aplicación corriendo):
//   node scripts/datos-demo/cargar.mjs
// Variables opcionales: APP_URL (por defecto http://localhost:8080),
//   COORD_EMAIL y COORD_PASSWORD (por defecto, la cuenta BOOTSTRAP_ADMIN del archivo .env).
//
// Las cuentas creadas y su contraseña están en docs/DATOS_DEMO.md.
// Todos los nombres, correos y establecimientos son ficticios.

import { readFileSync, existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const aqui = dirname(fileURLToPath(import.meta.url));
const raiz = join(aqui, '..', '..');
const env = {};
if (existsSync(join(raiz, '.env')))
  for (const linea of readFileSync(join(raiz, '.env'), 'utf8').split(/\r?\n/)) {
    const m = linea.match(/^\s*([A-Z_]+)\s*=\s*(.*)$/); if (m) env[m[1]] = m[2].trim();
  }
const URL_APP = process.env.APP_URL || `http://localhost:${env.APP_PORT || 8080}`;
const COORD = { correo: process.env.COORD_EMAIL || env.BOOTSTRAP_ADMIN_EMAIL, clave: process.env.COORD_PASSWORD || env.BOOTSTRAP_ADMIN_PASSWORD };
const CLAVE_DEMO = 'Practicas2026demo';
const PERIODO = { anio: 2026, semestre: 2 };

// ---------- Cliente HTTP con sesión y CSRF ----------
class Sesion {
  constructor(nombre) { this.nombre = nombre; this.cookies = {}; this.csrf = ''; }
  cookieHeader() { return Object.entries(this.cookies).map(([k, v]) => `${k}=${v}`).join('; '); }
  guardarCookies(res) {
    for (const c of res.headers.getSetCookie?.() || []) { const [par] = c.split(';'); const i = par.indexOf('='); this.cookies[par.slice(0, i)] = par.slice(i + 1); }
  }
  async pedir(metodo, ruta, cuerpo) {
    const headers = { Cookie: this.cookieHeader() };
    if (metodo !== 'GET') headers['X-CSRF-TOKEN'] = this.csrf;
    let body = cuerpo;
    if (cuerpo && !(cuerpo instanceof FormData) && !(cuerpo instanceof URLSearchParams)) { headers['Content-Type'] = 'application/json'; body = JSON.stringify(cuerpo); }
    const res = await fetch(URL_APP + '/api' + ruta, { method: metodo, headers, body });
    this.guardarCookies(res);
    const datos = await res.json().catch(() => ({}));
    if (!res.ok) throw new Error(`[${this.nombre}] ${metodo} ${ruta} → ${res.status}: ${datos.mensaje || ''} ${(datos.detalles || []).join(' · ')}`);
    return datos;
  }
  async refrescarCsrf() { const s = await this.pedir('GET', '/sesion'); this.csrf = s.csrf; return s; }
  async entrar(correo, clave) {
    await this.refrescarCsrf();
    await this.pedir('POST', '/login', new URLSearchParams({ username: correo, password: clave }));
    const s = await this.refrescarCsrf();
    if (!s.usuario) throw new Error(`No se pudo iniciar sesión como ${correo}`);
    return this;
  }
  get(r) { return this.pedir('GET', r); }
  post(r, c) { return this.pedir('POST', r, c); }
  put(r, c) { return this.pedir('PUT', r, c); }
  patch(r, c) { return this.pedir('PATCH', r, c); }
}
const entrar = (correo, clave = CLAVE_DEMO) => new Sesion(correo).entrar(correo, clave);

// ---------- PDF mínimo con texto seleccionable ----------
function pdf(titulo, lineas) {
  const esc = s => s.replace(/\\/g, '\\\\').replace(/\(/g, '\\(').replace(/\)/g, '\\)');
  const texto = ['BT', '/F1 15 Tf', '56 780 Td', `(${esc(titulo)}) Tj`, '/F1 11 Tf', '0 -28 Td', '15 TL',
    ...lineas.map(l => `(${esc(l)}) Tj T*`), 'ET'].join('\n');
  const contenido = Buffer.from(texto, 'latin1');
  const objetos = [
    '<< /Type /Catalog /Pages 2 0 R >>',
    '<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
    '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 5 0 R >> >> /Contents 4 0 R >>',
    null,
    '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>',
  ];
  const partes = [Buffer.from('%PDF-1.4\n', 'latin1')]; const offsets = []; let largo = partes[0].length;
  objetos.forEach((o, i) => {
    const b = o === null
      ? Buffer.concat([Buffer.from(`${i + 1} 0 obj\n<< /Length ${contenido.length} >>\nstream\n`, 'latin1'), contenido, Buffer.from('\nendstream\nendobj\n', 'latin1')])
      : Buffer.from(`${i + 1} 0 obj\n${o}\nendobj\n`, 'latin1');
    offsets.push(largo); partes.push(b); largo += b.length;
  });
  const xref = `xref\n0 ${objetos.length + 1}\n0000000000 65535 f \n` + offsets.map(o => `${String(o).padStart(10, '0')} 00000 n \n`).join('') +
    `trailer\n<< /Size ${objetos.length + 1} /Root 1 0 R >>\nstartxref\n${largo}\n%%EOF\n`;
  partes.push(Buffer.from(xref, 'latin1'));
  return Buffer.concat(partes);
}
const archivo = (bytes, nombre, tipo) => { const f = new FormData(); f.append('archivo', new Blob([bytes], { type: tipo }), nombre); return f; };
function entregaPdf(estudiante, requisito, version) {
  return pdf(`${requisito} - ${estudiante}`, [
    'Documento de demostración generado para la presentación del Sprint 2.',
    `Estudiante: ${estudiante}`, `Requisito: ${requisito}`, `Versión: ${version}`,
    'Contenido ficticio: no corresponde a una entrega real.',
  ]);
}

// ---------- Datos ficticios ----------
const docentes = [
  { clave: 'rojas', nombre: 'Marcela Rojas Fuentes', correo: 'mrojas@ubb.test', rol: 'PROFESOR_UBB' },
  { clave: 'salgado', nombre: 'Rodrigo Salgado Pérez', correo: 'rsalgado@ubb.test', rol: 'PROFESOR_UBB' },
  { clave: 'munoz', nombre: 'Patricia Muñoz Lagos', correo: 'pmunoz@colegio.test', rol: 'PROFESOR_COLEGIO' },
  { clave: 'valdes', nombre: 'Héctor Valdés Sandoval', correo: 'hvaldes@colegio.test', rol: 'PROFESOR_COLEGIO' },
];
const centros = [
  { clave: 'aromos', nombre: 'Liceo Bicentenario Los Aromos', comuna: 'Chillán', direccion: 'Av. Los Aromos 1450', contacto: 'Patricia Muñoz Lagos', correo: 'contacto@losaromos.test', telefono: '+56 42 255 1100' },
  { clave: 'elena', nombre: 'Colegio Santa Elena', comuna: 'Chillán Viejo', direccion: 'Calle Serrano 820', contacto: 'Héctor Valdés Sandoval', correo: 'contacto@santaelena.test', telefono: '+56 42 255 2200' },
  { clave: 'nuble', nombre: 'Liceo Técnico Río Ñuble', comuna: 'San Carlos', direccion: 'Av. Independencia 300', contacto: 'Unidad Técnico Pedagógica', correo: 'utp@rionuble.test', telefono: '+56 42 255 3300' },
];
const estudiantes = [
  { clave: 'camila', identificador: '20.456.789-1', nombres: 'Camila', apellidos: 'Soto Venegas', correo: 'camila.soto@alumnos.test' },
  { clave: 'benjamin', identificador: '20.987.654-3', nombres: 'Benjamín', apellidos: 'Araya Cifuentes', correo: 'benjamin.araya@alumnos.test' },
  { clave: 'valentina', identificador: '21.123.456-7', nombres: 'Valentina', apellidos: 'Morales Díaz', correo: 'valentina.morales@alumnos.test' },
  { clave: 'diego', identificador: '21.654.321-K', nombres: 'Diego', apellidos: 'Fuentealba Torres', correo: 'diego.fuentealba@alumnos.test' },
  { clave: 'fernanda', identificador: '21.333.444-5', nombres: 'Fernanda', apellidos: 'Henríquez Lagos', correo: 'fernanda.henriquez@alumnos.test' },
  { clave: 'tomas', identificador: '19.876.543-2', nombres: 'Tomás', apellidos: 'Riquelme Bravo', correo: 'tomas.riquelme@alumnos.test' },
];
// Plazos de la plantilla 2026-2 (hoy es fines de octubre: algunos ya pasaron).
const plazos = {
  INI1: { PRODUCTO: '2026-11-06' },
  INT1: { PAUTA_COLAB: '2026-10-16', INFORME: '2026-11-27' },
  INT2: { PAUTA_COLAB: '2026-10-16', INFORME: '2026-11-27' },
  PED: { EXPECTATIVAS: '2026-08-28', CONTEXTO: '2026-09-11', COLAB_ESP_1: '2026-09-18', COLAB_ORI_1: '2026-09-30', COLAB_ESP_2: '2026-10-21', COLAB_ORI_2: '2026-11-10', TANEP: '2026-11-20', FINAL: '2026-12-11' },
  PRO: { COLAB_ESP_1: '2026-09-10', TUTOR_ESP_1: '2026-09-17', COLAB_ORI_1: '2026-09-24', TUTOR_ORI_1: '2026-10-01', COLAB_ESP_2: '2026-10-15', TUTOR_ESP_2: '2026-10-22', COLAB_ORI_2: '2026-11-05', TUTOR_ORI_2: '2026-11-12', PROYECTO: '2026-11-26', FINAL: '2026-12-14' },
};
// Prácticas y lo que ocurrió en cada requisito: A = aprobado, O = observado, R = en revisión, OA = observado y luego aprobado, OR = observado y nueva versión en revisión.
const practicas = [
  { est: 'camila', asig: 'PED', centro: 'aromos', ubb: 'rojas', colegio: 'munoz',
    historia: { EXPECTATIVAS: 'A', CONTEXTO: 'OA', COLAB_ESP_1: 'A', COLAB_ORI_1: 'O', COLAB_ESP_2: 'R' } },
  { est: 'benjamin', asig: 'PRO', centro: 'elena', ubb: 'salgado', colegio: 'valdes',
    historia: { COLAB_ESP_1: 'A', TUTOR_ESP_1: 'OR' } },
  { est: 'valentina', asig: 'INT1', centro: 'nuble', ubb: 'rojas', colegio: 'valdes',
    historia: { PAUTA_COLAB: 'A', INFORME: 'A' } },
  { est: 'diego', asig: 'INI1', centro: null, ubb: 'rojas', colegio: null,
    historia: { PRODUCTO: 'R' } },
  { est: 'fernanda', asig: 'INT2', centro: 'aromos', ubb: 'salgado', colegio: 'munoz',
    historia: { PAUTA_COLAB: 'O' } },
  { est: 'tomas', asig: 'PED', centro: 'elena', ubb: 'rojas', colegio: 'valdes', periodo: { anio: 2026, semestre: 1 },
    historia: { EXPECTATIVAS: 'A', CONTEXTO: 'A', COLAB_ESP_1: 'A', COLAB_ESP_2: 'A', COLAB_ORI_1: 'A', COLAB_ORI_2: 'A', TANEP: 'A', FINAL: 'A' } },
];
const observaciones = {
  CONTEXTO: 'Falta describir el proyecto educativo del establecimiento y la caracterización del curso. Agrega ambos apartados.',
  COLAB_ORI_1: 'La pauta no tiene la firma del profesor colaborador. Sube la versión firmada.',
  TUTOR_ESP_1: 'Faltan las evidencias de la clase observada (planificación y material usado).',
  PAUTA_COLAB: 'La pauta está incompleta: faltan los indicadores del ámbito 3. Complétala con tu profesor colaborador.',
};
const evaluaciones = {
  camila: { colegio: 'munoz', fortalezas: 'Planifica con claridad y usa material concreto para introducir funciones lineales. Mantiene un buen clima en el aula.', aspectosMejora: 'Debe ajustar los tiempos: el cierre de la clase suele quedar corto.', recomendaciones: 'Incluir un ticket de salida para verificar el aprendizaje de la clase.' },
  benjamin: { colegio: 'valdes', fortalezas: 'Domina los contenidos de probabilidad y responde bien las preguntas de los estudiantes.', aspectosMejora: 'Ha tenido atrasos en la entrega de sus pautas y planificaciones.', recomendaciones: 'Organizar un calendario semanal con su profesor guía.' },
  valentina: { colegio: 'valdes', fortalezas: 'Excelente disposición al trabajo colaborativo. Propone actividades en grupo bien estructuradas.', aspectosMejora: 'Puede dar más espacio a que los estudiantes expliquen sus procedimientos.', recomendaciones: '' },
};

// Material de consulta que usa el asistente.
const materiales = [
  { titulo: 'Reglamento de práctica pedagógica 2026', asig: 'PED', autor: 'rojas', formato: 'pdf', estado: 'PUBLICADO', texto: [
    'Artículo 1. La práctica pedagógica se desarrolla en un establecimiento escolar en convenio con la universidad.',
    'Artículo 2. La asistencia mínima exigida al centro de práctica es de 90% de las jornadas programadas.',
    'Las inasistencias deben justificarse ante el profesor colaborador y el profesor supervisor en un plazo de 48 horas.',
    'Artículo 3. El informe final de práctica se entrega en formato PDF a través del sistema Prácticas UBB,',
    'en la sección de seguimiento, antes de la fecha límite publicada por la coordinación.',
    'Artículo 4. Si el profesor solicita correcciones, el estudiante debe subir una nueva versión del documento.',
    'Artículo 5. El taller de análisis de experiencias pedagógicas (TANEP) es obligatorio y se aprueba con asistencia completa.',
    'Artículo 6. La calificación final considera la evaluación del profesor colaborador y la del profesor supervisor.',
  ] },
  { titulo: 'Guía de práctica profesional', asig: 'PRO', autor: 'salgado', formato: 'md', estado: 'PUBLICADO', texto:
`# Guía de práctica profesional

## Asistencia
La asistencia mínima a la práctica profesional es de 90%. Las ausencias se justifican con certificado ante el profesor tutor.

## Proyecto de intervención
El proyecto de intervención se diseña junto al profesor colaborador y debe responder a una necesidad del curso. Se entrega antes del informe final.

## Informe final
El informe final se entrega en PDF en el sistema Prácticas UBB. Debe incluir la reflexión sobre la práctica, las evidencias y los resultados del proyecto de intervención.

## Observaciones de clase
Cada estudiante recibe dos observaciones del profesor colaborador y dos del profesor tutor, una de especialidad y otra de orientación.` },
  { titulo: 'Orientaciones para prácticas intermedias', asig: 'INT1', autor: 'rojas', formato: 'md', estado: 'PUBLICADO', texto:
`# Orientaciones para prácticas intermedias

La pauta de observación del profesor colaborador se completa durante la cuarta semana en el centro y se sube firmada al sistema.

El informe de la asignatura describe el trabajo colaborativo realizado en el aula. Debe tener entre 8 y 12 páginas e incluir evidencias.

La asistencia mínima al centro de práctica es de 85% de las visitas programadas.` },
  { titulo: 'Orientaciones para prácticas intermedias', asig: 'INT2', autor: 'salgado', formato: 'md', estado: 'PUBLICADO', texto:
`# Orientaciones para prácticas intermedias

La pauta de observación del profesor colaborador se completa durante la cuarta semana en el centro y se sube firmada al sistema.

El informe de la asignatura presenta la pequeña investigación realizada en el aula, con pregunta, método y resultados.

La asistencia mínima al centro de práctica es de 85% de las visitas programadas.` },
  { titulo: 'Orientaciones práctica inicial', asig: 'INI1', autor: 'rojas', formato: 'md', estado: 'PUBLICADO', texto:
`# Orientaciones práctica inicial

En la práctica inicial el estudiante conoce el sistema escolar. El producto de la asignatura es un informe de observación del funcionamiento de un establecimiento.

El producto se entrega en PDF en el sistema Prácticas UBB. La asistencia mínima a las actividades es de 80%.` },
  { titulo: 'Rúbrica del informe final (borrador)', asig: 'PED', autor: 'rojas', formato: 'md', estado: 'BORRADOR', texto:
`# Rúbrica del informe final

Borrador en revisión por el equipo de prácticas. Criterios: reflexión pedagógica, uso de evidencias, coherencia con el marco curricular y redacción.` },
];
const preguntas = {
  camila: ['¿Qué tengo pendiente?', '¿Cuál es la asistencia mínima?', '¿Cómo se entrega el informe final?'],
  benjamin: ['¿Cuáles son mis plazos?', '¿Qué es el proyecto de intervención?'],
  diego: ['¿Cuál es la asistencia mínima?'],
};

// ---------- Carga ----------
const log = (...a) => console.log(' ', ...a);
async function main() {
  if (!COORD.correo || !COORD.clave) throw new Error('Falta la cuenta de coordinación: define COORD_EMAIL y COORD_PASSWORD o BOOTSTRAP_ADMIN_* en .env');
  const coord = await entrar(COORD.correo, COORD.clave);
  console.log(`Conectado a ${URL_APP} como coordinación.`);

  const existentes = await coord.get('/estudiantes');
  if (existentes.some(e => e.correo === estudiantes[0].correo)) {
    console.log('Los datos de demostración ya están cargados. No se hizo ningún cambio.'); return;
  }

  const asignaturas = Object.fromEntries((await coord.get('/asignaturas')).map(a => [a.codigo, a]));

  console.log('1. Plazos de la plantilla 2026-2');
  for (const [codigoAsig, reqs] of Object.entries(plazos)) {
    const a = asignaturas[codigoAsig];
    for (const r of await coord.get(`/plantillas?asignaturaId=${a.id}&anio=${PERIODO.anio}&semestre=${PERIODO.semestre}`)) {
      if (!reqs[r.codigo]) continue;
      await coord.put(`/plantillas/${r.id}`, { asignaturaId: a.id, ...PERIODO, codigo: r.codigo, nombre: r.nombre, fechaLimite: reqs[r.codigo] });
    }
    log(`${codigoAsig}: ${Object.keys(reqs).length} plazos`);
  }

  console.log('2. Cuentas docentes');
  const usuarios = {};
  for (const d of docentes) { usuarios[d.clave] = await coord.post('/usuarios', { nombre: d.nombre, correo: d.correo, clave: CLAVE_DEMO, rol: d.rol }); log(`${d.rol}: ${d.nombre}`); }

  console.log('3. Centros de práctica');
  const idCentro = {};
  for (const c of centros) { const { clave, ...datos } = c; idCentro[clave] = (await coord.post('/centros', datos)).id; log(c.nombre); }

  console.log('4. Estudiantes y sus cuentas');
  const idEst = {};
  for (const s of estudiantes) {
    const { clave, ...datos } = s; const creado = await coord.post('/estudiantes', datos); idEst[clave] = creado.id;
    await coord.post('/usuarios', { nombre: `${s.nombres} ${s.apellidos}`, correo: s.correo, clave: CLAVE_DEMO, rol: 'ESTUDIANTE', estudianteId: creado.id });
    log(`${s.nombres} ${s.apellidos}`);
  }

  console.log('5. Prácticas y docentes asignados');
  for (const p of practicas) {
    const periodo = p.periodo || PERIODO; const ubb = docentes.find(d => d.clave === p.ubb);
    const creada = await coord.post('/practicas', { estudianteId: idEst[p.est], asignaturaId: asignaturas[p.asig].id, ...periodo, profesorResponsable: ubb.nombre, centroId: p.centro ? idCentro[p.centro] : null });
    p.id = creada.id;
    await coord.put(`/practicas/${p.id}/docentes`, { profesorUbbId: usuarios[p.ubb].id, profesorColegioId: p.colegio ? usuarios[p.colegio].id : null });
    log(`${p.est} · ${p.asig} ${periodo.anio}-${periodo.semestre}`);
  }

  console.log('6. Entregas de los estudiantes y revisiones de los profesores');
  for (const p of practicas) {
    const est = estudiantes.find(s => s.clave === p.est); const nombreEst = `${est.nombres} ${est.apellidos}`;
    const alumno = await entrar(est.correo); const profe = await entrar(docentes.find(d => d.clave === p.ubb).correo);
    const requisitos = (await alumno.get(`/practicas/${p.id}/seguimiento`)).requisitos;
    for (const [codigo, pasos] of Object.entries(p.historia)) {
      const r = requisitos.find(x => x.codigo === codigo); if (!r) throw new Error(`No existe ${codigo} en la práctica de ${p.est}`);
      const subir = async v => (await alumno.post(`/practicas/${p.id}/requisitos/${r.id}/documentos`,
        archivo(entregaPdf(nombreEst, r.nombre, v), `${codigo}_${est.apellidos.split(' ')[0]}_v${v}.pdf`, 'application/pdf'))).id;
      let doc = await subir(1);
      if (pasos[0] === 'O') {
        await profe.post(`/documentos/${doc}/revision`, { decision: 'OBSERVADO', comentario: observaciones[codigo] || 'Revisa el formato y vuelve a subir el documento.' });
        if (pasos.length > 1) doc = await subir(2);
      }
      const ultimo = pasos.at(-1);
      if (ultimo === 'A') await profe.post(`/documentos/${doc}/revision`, { decision: 'APROBADO', comentario: 'Cumple con lo solicitado. ¡Buen trabajo!' });
    }
    log(`${nombreEst}: ${Object.keys(p.historia).length} requisitos con entregas`);
  }

  console.log('7. Evaluaciones del profesor de colegio');
  for (const [clave, ev] of Object.entries(evaluaciones)) {
    const p = practicas.find(x => x.est === clave && !x.periodo); const { colegio, ...datos } = ev;
    await (await entrar(docentes.find(d => d.clave === colegio).correo)).post(`/practicas/${p.id}/evaluaciones`, datos);
    log(`${clave} evaluada por ${colegio}`);
  }

  console.log('8. Material de consulta del asistente');
  for (const m of materiales) {
    const profe = await entrar(docentes.find(d => d.clave === m.autor).correo);
    const nombre = m.titulo.replace(/[^\p{L}\p{N}]+/gu, '_').replace(/_+$/, '');
    const bytes = m.formato === 'pdf' ? pdf(m.titulo, m.texto) : Buffer.from(m.texto, 'utf8');
    const f = archivo(bytes, `${nombre}.${m.formato}`, m.formato === 'pdf' ? 'application/pdf' : 'text/markdown');
    f.append('titulo', m.titulo); f.append('asignaturaId', asignaturas[m.asig].id); f.append('anio', PERIODO.anio); f.append('semestre', PERIODO.semestre);
    const creado = await profe.post('/materiales', f);
    if (m.estado === 'PUBLICADO') await profe.patch(`/materiales/${creado.id}/estado`, { estado: 'PUBLICADO' });
    log(`${m.titulo} (${m.asig}, ${creado.fragmentos} fragmentos, ${m.estado.toLowerCase()})`);
  }

  console.log('9. Consultas al asistente');
  for (const [clave, lista] of Object.entries(preguntas)) {
    const alumno = await entrar(estudiantes.find(s => s.clave === clave).correo);
    for (const pregunta of lista) { const r = await alumno.post('/asistente', { pregunta }); log(`${clave}: "${pregunta}" → ${r.tipo}`); }
  }

  console.log('\nListo. Cuentas y contraseña en docs/DATOS_DEMO.md');
}
main().catch(err => { console.error('\nError:', err.message); process.exit(1); });
