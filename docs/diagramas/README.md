# Diagramas UML — Sprint 2

Diagramas generados a partir del código implementado en `src/main/java/cl/ubb/practicas`. Los diagramas de secuencia usan las mismas clases y métodos del diagrama de clases.

Cada diagrama tiene su archivo editable (`.puml`, PlantUML) y su imagen (`.png`).

## Diagrama de clases

| Archivo | Contenido |
|---|---|
| `00-diagrama-clases` | Controladores REST, servicios, reglas y utilidades, configuración y registros de entrada y salida, con sus dependencias. |

## Diagramas de secuencia

| Archivo | Funcionalidad | Historias |
|---|---|---|
| `01-iniciar-sesion` | Inicio de sesión y carga de la sesión | S2-01 |
| `02-cuentas-y-asignacion` | Crear una cuenta y asignar docentes a una práctica | S2-02, S2-03 |
| `03-requisitos-y-practica` | Configurar requisitos del período, registrar una práctica y ajustar un plazo | S2-09, S2-10 |
| `04-entregar-documento` | Entregar un documento o una versión corregida | S2-04, S2-13 |
| `05-revisar-entrega` | Aprobar o solicitar corrección y consultar el historial | S2-11, S2-12 |
| `06-evaluacion-colegio` | Registrar y consultar la evaluación del profesor de colegio | S2-14 |
| `07-indicadores` | Indicadores de avance, revisión y atraso | S2-15 |
| `08-material-consulta` | Cargar, procesar, publicar y retirar material | S2-16, S2-17 |
| `09-asistente` | Consultar al asistente, plazos personales e historial | S2-18, S2-19, S2-20 |

## Regenerar las imágenes

Desde la raíz del proyecto, con Java 21:

```powershell
.\mvnw.cmd -q dependency:copy "-Dartifact=net.sourceforge.plantuml:plantuml:1.2024.7" "-DoutputDirectory=target/plantuml"
cd docs\diagramas
java -jar ..\..\target\plantuml\plantuml-1.2024.7.jar -charset UTF-8 -tpng 0*.puml
```

Si cambia el código (por ejemplo, un método nuevo), actualiza el `.puml` correspondiente y vuelve a generar las imágenes, para que los diagramas sigan coherentes con lo implementado.
