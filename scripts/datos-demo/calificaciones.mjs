// Agrega calificaciones internas de DEMOSTRACIÓN a los datos de demo (se ejecuta después de cargar.mjs).
// Entra como cada profesor UBB y registra notas en sus prácticas. Si una práctica ya tiene notas, la omite.
//
// Uso (con la aplicación corriendo):
//   node scripts/datos-demo/calificaciones.mjs
//   APP_URL=https://pacheco.chillan.ubiobio.cl/bestpem node scripts/datos-demo/calificaciones.mjs

const URL_APP = (process.env.APP_URL || 'http://localhost:8080') + '/api';
const CLAVE_DEMO = 'Practicas2026demo';

async function entrar(correo) {
  const cookies = {}; let csrf = '';
  const pedir = async (metodo, ruta, cuerpo) => {
    const headers = { Cookie: Object.entries(cookies).map(([k, v]) => `${k}=${v}`).join('; ') };
    if (metodo !== 'GET') headers['X-CSRF-TOKEN'] = csrf;
    let body = cuerpo;
    if (cuerpo && !(cuerpo instanceof URLSearchParams)) { headers['Content-Type'] = 'application/json'; body = JSON.stringify(cuerpo); }
    const res = await fetch(URL_APP + ruta, { method: metodo, headers, body });
    for (const c of res.headers.getSetCookie?.() || []) { const [par] = c.split(';'); const i = par.indexOf('='); cookies[par.slice(0, i)] = par.slice(i + 1); }
    const datos = await res.json().catch(() => ({}));
    if (!res.ok) throw new Error(`${metodo} ${ruta} → ${res.status}: ${datos.mensaje || ''}`);
    return datos;
  };
  csrf = (await pedir('GET', '/sesion')).csrf;
  await pedir('POST', '/login', new URLSearchParams({ username: correo, password: CLAVE_DEMO }));
  csrf = (await pedir('GET', '/sesion')).csrf;
  return pedir;
}

// Notas por estudiante: [evaluación, nota, ponderación, comentario].
const notas = {
  mrojas: {
    'Camila Soto Venegas': [['Planificación de clase', 6.2, 30, 'Secuencia clara y objetivos medibles.'], ['Observación de clase en aula', 5.8, 30, 'Buen manejo del grupo; ajustar los tiempos del cierre.']],
    'Valentina Morales Díaz': [['Pauta del profesor colaborador', 6.5, 50, 'Excelente disposición al trabajo colaborativo.'], ['Informe de la asignatura', 6.8, 50, 'Informe completo y bien fundamentado.']],
    'Tomás Riquelme Bravo': [['Planificaciones', 5.9, 40, ''], ['Informe final de práctica', 6.1, 60, 'Buena reflexión pedagógica.']],
    'Diego Fuentealba Torres': [['Informe de observación', 5.5, 50, 'Describe bien el establecimiento; profundizar el análisis.']],
  },
  rsalgado: {
    'Benjamín Araya Cifuentes': [['Observaciones de especialidad', 3.8, 40, 'Entregas atrasadas y planificación incompleta.']],
    'Fernanda Henríquez Lagos': [['Pauta del profesor colaborador', 5.0, 30, 'Debe completar los indicadores del ámbito 3.']],
  },
};

const profesores = { mrojas: 'mrojas@ubb.test', rsalgado: 'rsalgado@ubb.test' };
for (const [clave, porEstudiante] of Object.entries(notas)) {
  const api = await entrar(profesores[clave]);
  for (const p of await api('GET', '/practicas')) {
    const lista = porEstudiante[p.estudiante]; if (!lista) continue;
    const actuales = await api('GET', `/practicas/${p.id}/calificaciones`);
    if (actuales.notas.length) { console.log(`  ${p.estudiante}: ya tiene notas, se omite`); continue; }
    let r;
    for (const [evaluacion, nota, ponderacion, comentario] of lista)
      r = await api('POST', `/practicas/${p.id}/calificaciones`, { evaluacion, nota, ponderacion, comentario });
    console.log(`  ${p.estudiante}: promedio ${r.promedio} (${r.ponderacion} % evaluado, ${r.situacion})`);
  }
}
console.log('Listo.');
