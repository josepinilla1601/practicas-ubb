# Historias de usuario: Sprint 3

Funciones construidas después del Sprint 2. Cada historia se puede copiar como una tarjeta de Trello: el **título** va en el nombre de la tarjeta y el resto en la descripción.

**Roles:** Coordinación · Profesor UBB (supervisor y revisor) · Profesor de colegio (colaborador) · Estudiante (practicante).
**Estimación:** puntos de historia (1 = muy simple, 8 = compleja).

## Resumen

| ID | Historia | Rol | Pts | Menú |
|---|---|---|---|---|
| S3-01 | Registrar jornadas en la bitácora | Estudiante | 5 | Gestión académica → Bitácora |
| S3-02 | Visar u observar jornadas | Profesor de colegio | 3 | Gestión académica → Bitácora |
| S3-03 | Retroalimentar jornadas | Profesor UBB | 2 | Gestión académica → Bitácora |
| S3-04 | Completar y enviar la ficha de inscripción | Estudiante | 3 | Práctica → Ficha de inscripción |
| S3-05 | Confirmar, aprobar o devolver la ficha | Colegio y coordinación | 3 | Práctica → Ficha de inscripción |
| S3-06 | Evaluar con la rúbrica del Marco para la Buena Enseñanza | Profesor de colegio | 5 | Práctica → Evaluación de desempeño |
| S3-07 | Usar la nota sugerida de la rúbrica como calificación | Profesor UBB | 2 | Práctica → Evaluación de desempeño |
| S3-08 | Descargar la carta de presentación en PDF | Estudiante, UBB y coordinación | 3 | Práctica → botón de arriba |
| S3-09 | Importar estudiantes desde Excel | Coordinación | 5 | Gestión académica → Estudiantes |
| S3-10 | Ver un calendario de plazos y jornadas | Todos | 3 | Inicio → Calendario |
| S3-11 | Ver el panel como lo ve un estudiante | Coordinación | 2 | Gestión académica → Estudiantes |
| S3-12 | Registrar calificaciones internas | Profesor UBB | 5 | Práctica → Calificaciones internas |
| S3-13 | Consultar mis notas y promedio | Estudiante | 1 | Práctica → Calificaciones internas |
| S3-14 | Exportar prácticas a Excel | Profesor UBB y coordinación | 5 | Inicio → Indicadores |
| S3-15 | Respuestas del asistente redactadas con IA | Todos | 5 | Asistente |
| S3-16 | Pedir que la IA explique un documento | Todos | 3 | Asistente (clip) |
| S3-17 | Apoyo matemático con IA | Todos | 2 | Asistente |
| S3-18 | Dictar textos por voz | Todos | 3 | Campos de texto largos |
| S3-19 | Deshacer entregas, revisiones y otros cambios | Todos | 3 | En cada acción |
| S3-20 | Ayuda por página y guía de bienvenida | Todos | 3 | Botón "? Ayuda" |
| S3-21 | Buscar, filtrar y ordenar tablas | Todos | 3 | Todas las tablas |
| S3-22 | Mensajes de error claros y por campo | Todos | 2 | Todos los formularios |
| S3-23 | Publicar el sistema en el servidor Pacheco | Equipo | 5 | — |
| S3-24 | Exportar cualquier lista a Excel | Coordinación y docentes | 3 | Botón en cada lista |
| S3-25 | Avisos de pendientes con campanita | Todos | 3 | Encabezado (campanita) |
| S3-26 | Seguimiento de la práctica en pestañas | Todos | 2 | Práctica → pestañas |

---

## Bitácora de práctica (diario de campo)

### S3-01 · Registrar jornadas en la bitácora
**Como** estudiante en práctica, **quiero** registrar cada jornada en el centro con lo que hice y mi reflexión, **para** llevar mi diario de campo y que mis horas queden registradas.

**Qué hace:** el estudiante entra a *Bitácora* y completa fecha, horario de inicio y término, curso, tipo de actividad (observación, ayudantía, clase realizada, reunión u otra), lo que hizo y su reflexión pedagógica. Puede dictar los textos con el micrófono. Arriba ve cuántas jornadas lleva, las horas registradas, las horas visadas y las que esperan revisión. Puede editar o eliminar una jornada mientras no esté visada.

**Criterios de aceptación**
- No se puede registrar una jornada con fecha futura ni con hora de término anterior a la de inicio.
- Las horas se calculan solas y se suman en el resumen.
- Una jornada visada ya no se puede modificar ni eliminar.
- Si una jornada fue observada y el estudiante la corrige, vuelve a quedar "Por visar".
- Un estudiante no puede ver ni registrar jornadas de otra práctica.

**Técnico:** tabla `bitacora_jornadas` (V14). `GET/POST /api/practicas/{id}/bitacora`, `PUT/DELETE /api/bitacora/{id}`. Pruebas: `Sprint3IntegrationTest.bitacoraRegistroVisadoYRetroalimentacion`.

### S3-02 · Visar u observar jornadas
**Como** profesor de colegio, **quiero** visar las jornadas de mi practicante o pedirle que corrija una, **para** confirmar que asistió y que lo registrado es correcto.

**Qué hace:** en *Bitácora* el profesor de colegio ve las jornadas de sus practicantes y puede filtrar "Solo por visar". Con *Visar* confirma la jornada (pide confirmación). Con *Observar* escribe qué debe corregir el estudiante, que ve el comentario en la jornada.

**Criterios de aceptación**
- Observar exige un comentario.
- Solo el profesor de colegio asignado (o la coordinación) puede visar.
- Las horas visadas se reflejan en el resumen de la práctica.

### S3-03 · Retroalimentar jornadas
**Como** profesor UBB, **quiero** dejar comentarios en las jornadas de mis practicantes, **para** guiar su reflexión pedagógica durante la práctica.

**Qué hace:** con *Retroalimentar* el profesor UBB escribe un comentario que el estudiante ve destacado en la jornada. Puede editarlo después.

**Criterios de aceptación:** solo el profesor UBB asignado o la coordinación pueden comentar; el profesor de colegio no.

---

## Ficha de inscripción

### S3-04 · Completar y enviar la ficha de inscripción
**Como** estudiante, **quiero** registrar el curso, los días, el horario y las fechas de mi práctica en el centro, **para** formalizar mi inscripción.

**Qué hace:** en el seguimiento de su práctica, el estudiante completa la ficha (curso, nivel, días, horario, fecha de inicio y término, horas semanales y observaciones), la guarda como borrador y luego la envía. Un indicador de tres pasos muestra en qué etapa está: estudiante → colegio → coordinación. Si la ficha está pendiente o fue devuelta, aparece un aviso en *Mi panel*.

**Criterios de aceptación**
- No se puede enviar sin haberla guardado, y no se puede modificar mientras está en revisión.
- La fecha de término no puede ser anterior a la de inicio, y las horas semanales van de 1 a 45.
- Si la práctica no tiene profesor de colegio, la ficha pasa directo a la coordinación.

**Técnico:** tabla `fichas_inscripcion` (V15). `GET/PUT /api/practicas/{id}/ficha`, `POST .../ficha/enviar`, `POST .../ficha/decision`. Prueba: `fichaInscripcionFlujoCompleto`.

### S3-05 · Confirmar, aprobar o devolver la ficha
**Como** profesor de colegio o coordinación, **quiero** confirmar, aprobar o devolver la ficha con un comentario, **para** asegurar que los datos de la práctica son correctos.

**Qué hace:** el profesor de colegio *confirma* la ficha enviada; después la coordinación la *aprueba*. Cualquiera de los dos puede *devolverla* escribiendo qué corregir. La ficha aprobada se usa en la carta de presentación (S3-08).

**Criterios de aceptación:** solo la coordinación aprueba; la coordinación no aprueba una ficha que el colegio no confirmó; devolver exige comentario.

---

## Evaluación de desempeño

### S3-06 · Evaluar con la rúbrica del Marco para la Buena Enseñanza
**Como** profesor de colegio, **quiero** evaluar el desempeño del practicante con una rúbrica basada en el Marco para la Buena Enseñanza, **para** entregar una evaluación objetiva y comparable.

**Qué hace:** la rúbrica tiene 12 criterios en los 4 dominios del MBE (A. Preparación de la enseñanza, B. Ambiente para el aprendizaje, C. Enseñanza para el aprendizaje, D. Responsabilidades profesionales), y cada criterio se marca de 1 (insatisfactorio) a 4 (destacado). Mientras se marca, se muestra la **nota sugerida**, que convierte el puntaje promedio a la escala 1,0 a 7,0 (nivel 1 = 1,0; 2,5 = 4,0; 4 = 7,0). El resultado muestra una barra por dominio con las fortalezas y debilidades. Los criterios sin marcar se destacan en rojo.

**Criterios de aceptación:** se deben evaluar los 12 criterios; solo el profesor de colegio asignado o la coordinación evalúan; todos los involucrados (incluido el estudiante) ven el resultado.

**Técnico:** tabla `rubricas_colegio` (V16), clase `ReglasRubrica`. `GET /api/rubrica`, `GET/POST /api/practicas/{id}/rubricas`. Pruebas: `rubricaMbeConNotaSugeridaYUsoComoCalificacion`, `notaSugeridaEsConversionLineal`.

### S3-07 · Usar la nota sugerida como calificación
**Como** profesor UBB, **quiero** agregar la nota sugerida de la rúbrica a las calificaciones internas, **para** integrar la evaluación del colegio en la nota del estudiante.

**Qué hace:** con *Usar como nota*, el profesor UBB indica la ponderación y la nota se agrega como "Desempeño en el centro (rúbrica MBE)". Se aplican las mismas validaciones que una nota normal.

---

## Documentos y administración

### S3-08 · Descargar la carta de presentación en PDF
**Como** estudiante o coordinación, **quiero** descargar una carta de presentación para el establecimiento, **para** presentar formalmente al practicante sin redactarla a mano.

**Qué hace:** en el seguimiento de la práctica aparece el botón *Carta de presentación (PDF)*. La carta incluye el centro, el estudiante, la asignatura, el período, el profesor supervisor y el colaborador, y, si la ficha está aprobada, el curso, los días, el horario y las fechas.

**Criterios de aceptación:** solo para prácticas con centro; el estudiante solo descarga la suya; el profesor de colegio no la genera.

**Técnico:** `GET /api/practicas/{id}/carta.pdf` (PDFBox). Prueba: `cartaPresentacionEnPdf`.

### S3-09 · Importar estudiantes desde Excel
**Como** coordinación, **quiero** subir la lista del curso en Excel, **para** registrar a todos los estudiantes y sus cuentas de una vez.

**Qué hace:** en *Estudiantes* hay una sección para subir un `.xlsx` o `.csv` con las columnas Identificador, Nombres, Apellidos y Correo (hay una plantilla descargable). Opcionalmente crea las cuentas con una contraseña inicial común. Al terminar muestra cuántos se crearon, y fila por fila los omitidos (ya existían) y los errores (por ejemplo, un correo inválido).

**Criterios de aceptación:** los títulos se reconocen sin importar el orden, las mayúsculas ni las tildes (también "RUT" y "Email"); los duplicados se omiten sin detener la importación; la contraseña inicial debe tener entre 10 y 60 caracteres; máximo 1.000 filas.

**Técnico:** `POST /api/estudiantes/importar`, `GET /api/estudiantes/plantilla.xlsx` (Apache POI). Prueba: `importarEstudiantesDesdeCsvYExcel`.

### S3-10 · Ver un calendario de plazos y jornadas
**Como** usuario del sistema, **quiero** ver en un calendario mensual los plazos, las jornadas y las fechas de las prácticas, **para** organizarme.

**Qué hace:** la página *Calendario* muestra el mes con puntos de colores por día: morado para un plazo, rojo para un plazo vencido, verde para una jornada y amarillo para el inicio o término de una práctica. Al pulsar un día se listan sus eventos, cada uno con acceso a su práctica.

**Criterios de aceptación:** cada cuenta ve solo sus prácticas autorizadas; se puede navegar entre meses y volver a "Hoy".

**Técnico:** `GET /api/calendario?anio=&mes=`. Prueba: `calendarioMuestraPlazosYJornadasSegunPermisos`.

### S3-11 · Ver el panel como lo ve un estudiante
**Como** coordinación, **quiero** ver el panel de un estudiante tal como él lo ve, **para** orientarlo cuando tiene dudas.

**Qué hace:** en *Estudiantes*, el botón *Ver como estudiante* abre su panel en modo de solo lectura, con un aviso que lo indica.

**Criterios de aceptación:** solo la coordinación; no permite hacer cambios en nombre del estudiante.

---

## Calificaciones y exportación

### S3-12 · Registrar calificaciones internas
**Como** profesor UBB, **quiero** registrar notas de 1,0 a 7,0 con ponderación, **para** llevar la evaluación de mis practicantes en el sistema.

**Qué hace:** en el seguimiento de la práctica, el profesor registra cada evaluación (nombre, nota, ponderación y comentario). El sistema calcula el promedio ponderado y la situación: aprobada (4,0 o más), reprobada o en curso si aún no se evalúa el 100 %. Se puede eliminar una nota con confirmación y deshacer.

**Criterios de aceptación:** la nota va de 1,0 a 7,0 con un decimal; las ponderaciones no superan el 100 %; solo quien registró la nota o la coordinación la elimina; las notas bajo 4,0 se muestran en rojo.

**Técnico:** tabla `calificaciones` (V13), `ReglasCalificacion`. `GET/POST /api/practicas/{id}/calificaciones`, `DELETE /api/calificaciones/{id}`.

### S3-13 · Consultar mis notas y promedio
**Como** estudiante, **quiero** ver mis notas, su ponderación y mi promedio, **para** saber cómo voy en la práctica. Solo lectura.

### S3-14 · Exportar prácticas a Excel
**Como** profesor UBB o coordinación, **quiero** descargar un Excel ordenado con el estado de las prácticas, **para** informar o archivar el avance.

**Qué hace:** el botón *Exportar a Excel* (en Indicadores y en Prácticas) descarga un `.xlsx` con 4 hojas ordenadas por asignatura, período y estudiante: resumen por práctica (avance, atrasos y nota), requisitos (estado, plazo y versiones), calificaciones y bitácora. Los encabezados tienen filtros y las notas bajo 4,0 aparecen en rojo.

**Criterios de aceptación:** cada profesor exporta solo sus prácticas; el estudiante no puede exportar; respeta los filtros de la página.

**Técnico:** `GET /api/exportar/practicas.xlsx` (Apache POI). Prueba: `exportaPracticasAExcelSegunPermisos`.

### S3-24 · Exportar cualquier lista a Excel
**Como** coordinación o docente, **quiero** descargar en Excel las listas de estudiantes, centros, cuentas y bitácora, **para** trabajar los datos fuera del sistema o archivarlos.

**Qué hace:** cada lista tiene el mismo botón *Exportar a Excel*, a la derecha de su tabla. Si se escribió algo en "Buscar en esta tabla", el Excel trae solo esos resultados (sin importar mayúsculas ni tildes). Cada archivo tiene encabezados con filtro, fechas y horas con formato, y el nombre incluye la fecha de descarga.

**Criterios de aceptación:** estudiantes, centros y cuentas solo los exporta la coordinación; la bitácora la exporta cada rol con lo que puede ver (el estudiante, su propio diario de campo).

**Técnico:** `GET /api/exportar/{estudiantes|centros|usuarios|bitacora}.xlsx?q=`. Prueba: `exportarListasAExcelConBusquedaYPermisos`.

---

### S3-25 · Avisos de pendientes con campanita
**Como** usuario, **quiero** ver en un solo lugar lo que tengo pendiente, **para** no tener que revisar práctica por práctica.

**Qué hace:** arriba, junto a "Ayuda", hay una campanita con un número rojo. Al pulsarla muestra los avisos ordenados por urgencia, y cada uno lleva directo a la práctica y la pestaña donde se resuelve:
- **Estudiante:** correcciones solicitadas, requisitos vencidos o que vencen en 7 días, jornadas observadas y ficha pendiente o devuelta.
- **Profesor UBB:** entregas por revisar.
- **Profesor de colegio:** jornadas por visar, fichas por confirmar y practicantes sin evaluar con la rúbrica.
- **Coordinación:** fichas por aprobar, prácticas sin requisitos y entregas por revisar.

**Criterios de aceptación:** cada cuenta solo ve avisos de sus prácticas autorizadas; los avisos desaparecen cuando se resuelve lo pendiente; si no hay nada, dice "Todo al día".

**Técnico:** `GET /api/notificaciones` (calculado con los datos existentes, sin tablas nuevas). Prueba: `avisosDeLaCampanitaSegunElRol`.

### S3-26 · Seguimiento de la práctica en pestañas
**Como** profesor sin experiencia en informática, **quiero** que la información de una práctica esté separada en pestañas, **para** encontrar lo que busco sin bajar por una página muy larga.

**Qué hace:** la página de cada práctica se divide en *Entregas* (requisitos, entregas, revisión y plazos), *Bitácora*, *Evaluación y notas* (evaluación del colegio, rúbrica y calificaciones) e *Inscripción* (ficha y docentes asignados). Un número rojo en la pestaña indica los pendientes. El profesor de colegio entra directo a la Bitácora. Se puede navegar con las flechas del teclado.

**Criterios de aceptación:** solo se muestran las pestañas que tienen contenido para el rol; al volver a la misma práctica se mantiene la pestaña elegida; los avisos y el calendario abren la pestaña correspondiente.

---

## Asistente con inteligencia artificial

### S3-15 · Respuestas del asistente redactadas con IA
**Como** estudiante, **quiero** que el asistente me responda con palabras claras y citando el reglamento, **para** entender las reglas de la práctica.

**Qué hace:** el asistente busca en el material publicado y envía solo esos fragmentos a Google Gemini, que redacta la respuesta citando cada fuente ([1], [2]). Si el material no tiene la respuesta, lo dice en vez de inventar. También responde cómo usar la plataforma. Si la IA no está disponible, responde con el buscador por palabras clave.

**Criterios de aceptación:** nunca inventa información institucional; no cita fuentes inexistentes; cada estudiante solo consulta material de sus asignaturas.

### S3-16 · Pedir que la IA explique un documento
**Como** estudiante, **quiero** subir un documento y que el asistente me lo explique, **para** entender pautas o instrucciones largas.

**Qué hace:** con el clip del asistente (o "Explicar un documento"), sube un PDF, Word, TXT o MD, o elige un material publicado. La IA explica de qué trata, las ideas principales, qué debe hacer y los conceptos difíciles. El archivo no se guarda.

### S3-17 · Apoyo matemático con IA
**Como** estudiante de Pedagogía en Matemática, **quiero** que el asistente resuelva y explique ejercicios y me dé ideas para enseñarlos, **para** preparar mis clases.

**Qué hace:** resuelve paso a paso explicando el porqué de cada paso y sugiere actividades para el aula, como material concreto o errores frecuentes de los alumnos. Se marca como "Apoyo matemático con IA", con una advertencia para revisar los pasos.

### S3-18 · Dictar textos por voz
**Como** profesor o estudiante, **quiero** dictar los textos largos en vez de escribirlos, **para** ahorrar tiempo.

**Qué hace:** los campos largos (comentarios, evaluaciones, reflexiones y preguntas al asistente) tienen un micrófono. Se habla y el texto se escribe solo, en español de Chile. Entiende "coma", "punto y seguido" y "punto y aparte", y se detiene con otro clic o con Esc.

**Criterios de aceptación:** funciona en Chrome y Edge; si falta el permiso o el micrófono, explica cómo solucionarlo; en navegadores sin soporte el botón no aparece.

---

## Usabilidad (evaluación heurística de Nielsen)

### S3-19 · Deshacer entregas, revisiones y otros cambios
**Como** usuario, **quiero** poder deshacer una acción hecha por error, **para** no depender de la coordinación para corregirla.

**Qué hace:** tras entregar, revisar, registrar una nota, eliminar una jornada o publicar material aparece un aviso con *Deshacer*. El estudiante puede eliminar una entrega no revisada, y el profesor puede anular su revisión mientras no haya una versión nueva. Las acciones importantes piden confirmación.

### S3-20 · Ayuda por página y guía de bienvenida
**Como** usuario nuevo, **quiero** ayuda paso a paso en cada página, **para** usar el sistema sin capacitación.

**Qué hace:** el botón *? Ayuda* (o la tecla ?) abre los pasos, las preguntas frecuentes y los atajos de la página actual. La primera vez se muestra una guía de bienvenida según el rol.

### S3-21 · Buscar, filtrar y ordenar tablas
**Como** profesor, **quiero** buscar, filtrar por estado y ordenar las tablas, **para** encontrar rápido lo que necesito. También hay accesos rápidos y atajos de teclado (/, A, I).

### S3-22 · Mensajes de error claros y por campo
**Como** usuario, **quiero** que los errores digan qué pasó y cómo corregirlo, junto al campo, **para** no quedarme bloqueado. Distingue una sesión vencida de la falta de permiso, y una clave incorrecta de una cuenta desactivada.

---

## Infraestructura

### S3-23 · Publicar el sistema en el servidor Pacheco
**Como** equipo, **queremos** publicar el sistema en el servidor de la universidad, **para** que cualquier persona con el link pueda usarlo.

**Qué hace:** MySQL y la aplicación corren en contenedores Podman, como servicios que se reinician solos, en https://pacheco.chillan.ubiobio.cl/bestpem/. La guía está en `despliegue/pacheco/README.md`.

---

## Trabajo futuro (no implementado)

- **Recordatorios por correo** de plazos y correcciones: necesita un servidor de correo disponible en Pacheco.
- **Asignación de centros por cupos:** el estudiante marca sus preferencias y la coordinación asigna según los cupos de cada colegio.
- **Selector de período global** (año y semestre) en el encabezado.
