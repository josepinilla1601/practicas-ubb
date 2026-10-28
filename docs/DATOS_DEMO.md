# Datos de demostración

Datos **ficticios** para presentar el sistema. Se cargan con:

```
node scripts/datos-demo/cargar.mjs
```

Requisitos: la aplicación debe estar corriendo y la base no debe tener ya estos datos (si los tiene, el script no hace nada). Entra como coordinación con la cuenta `BOOTSTRAP_ADMIN_*` de tu `.env`, o con `COORD_EMAIL` y `COORD_PASSWORD`.

## Cuentas

Todas las cuentas de demostración usan la contraseña **`Practicas2026demo`**.

| Rol | Nombre | Correo | Qué muestra |
|---|---|---|---|
| Profesora UBB | Marcela Rojas Fuentes | mrojas@ubb.test | Supervisa a Camila, Valentina, Diego y Tomás. Tiene entregas por revisar |
| Profesor UBB | Rodrigo Salgado Pérez | rsalgado@ubb.test | Supervisa a Benjamín (con atrasos) y Fernanda |
| Profesora de colegio | Patricia Muñoz Lagos | pmunoz@colegio.test | Liceo Bicentenario Los Aromos: Camila y Fernanda |
| Profesor de colegio | Héctor Valdés Sandoval | hvaldes@colegio.test | Benjamín, Valentina y Tomás |
| Estudiante | Camila Soto Venegas | camila.soto@alumnos.test | **El mejor caso para la demo**: aprobados, una corrección, una entrega en revisión, un atraso e historial del asistente |
| Estudiante | Benjamín Araya Cifuentes | benjamin.araya@alumnos.test | Práctica profesional con varios requisitos fuera de plazo |
| Estudiante | Valentina Morales Díaz | valentina.morales@alumnos.test | Todo aprobado (al día) |
| Estudiante | Diego Fuentealba Torres | diego.fuentealba@alumnos.test | Práctica inicial, sin centro, con una entrega en revisión |
| Estudiante | Fernanda Henríquez Lagos | fernanda.henriquez@alumnos.test | Una corrección solicitada |
| Estudiante | Tomás Riquelme Bravo | tomas.riquelme@alumnos.test | Práctica del semestre 2026-1, completa |

## Qué quedó cargado

- **Plazos** en la plantilla 2026-2 de INI1, INT1, INT2, PED y PRO.
- **3 centros**: Liceo Bicentenario Los Aromos (Chillán), Colegio Santa Elena (Chillán Viejo) y Liceo Técnico Río Ñuble (San Carlos).
- **6 prácticas**: 5 del período 2026-2 y 1 del 2026-1.
- **Entregas en PDF** con versiones, aprobaciones y correcciones con comentario.
- **3 evaluaciones** del profesor de colegio.
- **6 materiales** de consulta: 5 publicados (uno por asignatura, el de PED en PDF) y 1 en borrador, que el asistente no usa.
- **Historial del asistente** para Camila, Benjamín y Diego.

## Preguntas que responden bien en la demo

- Estudiante: "¿Qué tengo pendiente?", "¿Cuáles son mis plazos?"
- Cualquier rol: "¿Cuál es la asistencia mínima?", "¿Cómo se entrega el informe final?", "¿Qué es el proyecto de intervención?"
- Para mostrar que no inventa: "¿Cuánto cuesta el estacionamiento?" (responde que no tiene respaldo).
