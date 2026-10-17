-- Requisitos de DEMOSTRACIÓN: validar su obligatoriedad con el profesor antes de usar datos reales.
INSERT INTO asignaturas(codigo,nombre,ciclo,requiere_centro) VALUES
('INI1','Aproximación al sistema escolar','INICIAL',0),
('INI2','Ambientes de aprendizaje','INICIAL',0),
('INT1','Trabajo colaborativo en el aula','INTERMEDIO',1),
('INT2','Investigación en el aula','INTERMEDIO',1),
('PED','Práctica pedagógica','AVANZADO',1),
('PRO','Práctica profesional','AVANZADO',1);
INSERT INTO periodos(anio,semestre) VALUES (2026,1),(2026,2);
INSERT INTO requisitos_plantilla(asignatura_id,periodo_id,codigo,nombre) SELECT a.id,p.id,'PRODUCTO','Producto de la asignatura' FROM asignaturas a CROSS JOIN periodos p WHERE a.codigo='INI1' AND p.anio=2026;
INSERT INTO requisitos_plantilla(asignatura_id,periodo_id,codigo,nombre) SELECT a.id,p.id,'PRODUCTO','Producto de la asignatura' FROM asignaturas a CROSS JOIN periodos p WHERE a.codigo='INI2' AND p.anio=2026;
INSERT INTO requisitos_plantilla(asignatura_id,periodo_id,codigo,nombre) SELECT a.id,p.id,'PAUTA_COLAB','Pauta de observación del profesor colaborador' FROM asignaturas a CROSS JOIN periodos p WHERE a.codigo='INT1' AND p.anio=2026;
INSERT INTO requisitos_plantilla(asignatura_id,periodo_id,codigo,nombre) SELECT a.id,p.id,'INFORME','Informe de la asignatura' FROM asignaturas a CROSS JOIN periodos p WHERE a.codigo='INT1' AND p.anio=2026;
INSERT INTO requisitos_plantilla(asignatura_id,periodo_id,codigo,nombre) SELECT a.id,p.id,'PAUTA_COLAB','Pauta de observación del profesor colaborador' FROM asignaturas a CROSS JOIN periodos p WHERE a.codigo='INT2' AND p.anio=2026;
INSERT INTO requisitos_plantilla(asignatura_id,periodo_id,codigo,nombre) SELECT a.id,p.id,'INFORME','Informe de la asignatura' FROM asignaturas a CROSS JOIN periodos p WHERE a.codigo='INT2' AND p.anio=2026;
INSERT INTO requisitos_plantilla(asignatura_id,periodo_id,codigo,nombre) SELECT a.id,p.id,'COLAB_ESP_1','Colaborador - Especialidad - Observación 1' FROM asignaturas a CROSS JOIN periodos p WHERE a.codigo='PED' AND p.anio=2026;
INSERT INTO requisitos_plantilla(asignatura_id,periodo_id,codigo,nombre) SELECT a.id,p.id,'COLAB_ESP_2','Colaborador - Especialidad - Observación 2' FROM asignaturas a CROSS JOIN periodos p WHERE a.codigo='PED' AND p.anio=2026;
INSERT INTO requisitos_plantilla(asignatura_id,periodo_id,codigo,nombre) SELECT a.id,p.id,'COLAB_ORI_1','Colaborador - Orientación - Observación 1' FROM asignaturas a CROSS JOIN periodos p WHERE a.codigo='PED' AND p.anio=2026;
INSERT INTO requisitos_plantilla(asignatura_id,periodo_id,codigo,nombre) SELECT a.id,p.id,'COLAB_ORI_2','Colaborador - Orientación - Observación 2' FROM asignaturas a CROSS JOIN periodos p WHERE a.codigo='PED' AND p.anio=2026;
INSERT INTO requisitos_plantilla(asignatura_id,periodo_id,codigo,nombre) SELECT a.id,p.id,'EXPECTATIVAS','Texto de expectativas' FROM asignaturas a CROSS JOIN periodos p WHERE a.codigo='PED' AND p.anio=2026;
INSERT INTO requisitos_plantilla(asignatura_id,periodo_id,codigo,nombre) SELECT a.id,p.id,'CONTEXTO','Informe de contexto' FROM asignaturas a CROSS JOIN periodos p WHERE a.codigo='PED' AND p.anio=2026;
INSERT INTO requisitos_plantilla(asignatura_id,periodo_id,codigo,nombre) SELECT a.id,p.id,'TANEP','Taller de análisis de experiencias pedagógicas' FROM asignaturas a CROSS JOIN periodos p WHERE a.codigo='PED' AND p.anio=2026;
INSERT INTO requisitos_plantilla(asignatura_id,periodo_id,codigo,nombre) SELECT a.id,p.id,'FINAL','Informe final de práctica' FROM asignaturas a CROSS JOIN periodos p WHERE a.codigo='PED' AND p.anio=2026;
INSERT INTO requisitos_plantilla(asignatura_id,periodo_id,codigo,nombre) SELECT a.id,p.id,'COLAB_ESP_1','Colaborador - Especialidad - Observación 1' FROM asignaturas a CROSS JOIN periodos p WHERE a.codigo='PRO' AND p.anio=2026;
INSERT INTO requisitos_plantilla(asignatura_id,periodo_id,codigo,nombre) SELECT a.id,p.id,'COLAB_ESP_2','Colaborador - Especialidad - Observación 2' FROM asignaturas a CROSS JOIN periodos p WHERE a.codigo='PRO' AND p.anio=2026;
INSERT INTO requisitos_plantilla(asignatura_id,periodo_id,codigo,nombre) SELECT a.id,p.id,'COLAB_ORI_1','Colaborador - Orientación - Observación 1' FROM asignaturas a CROSS JOIN periodos p WHERE a.codigo='PRO' AND p.anio=2026;
INSERT INTO requisitos_plantilla(asignatura_id,periodo_id,codigo,nombre) SELECT a.id,p.id,'COLAB_ORI_2','Colaborador - Orientación - Observación 2' FROM asignaturas a CROSS JOIN periodos p WHERE a.codigo='PRO' AND p.anio=2026;
INSERT INTO requisitos_plantilla(asignatura_id,periodo_id,codigo,nombre) SELECT a.id,p.id,'TUTOR_ESP_1','Tutor - Especialidad - Observación 1' FROM asignaturas a CROSS JOIN periodos p WHERE a.codigo='PRO' AND p.anio=2026;
INSERT INTO requisitos_plantilla(asignatura_id,periodo_id,codigo,nombre) SELECT a.id,p.id,'TUTOR_ESP_2','Tutor - Especialidad - Observación 2' FROM asignaturas a CROSS JOIN periodos p WHERE a.codigo='PRO' AND p.anio=2026;
INSERT INTO requisitos_plantilla(asignatura_id,periodo_id,codigo,nombre) SELECT a.id,p.id,'TUTOR_ORI_1','Tutor - Orientación - Observación 1' FROM asignaturas a CROSS JOIN periodos p WHERE a.codigo='PRO' AND p.anio=2026;
INSERT INTO requisitos_plantilla(asignatura_id,periodo_id,codigo,nombre) SELECT a.id,p.id,'TUTOR_ORI_2','Tutor - Orientación - Observación 2' FROM asignaturas a CROSS JOIN periodos p WHERE a.codigo='PRO' AND p.anio=2026;
INSERT INTO requisitos_plantilla(asignatura_id,periodo_id,codigo,nombre) SELECT a.id,p.id,'FINAL','Informe final de práctica' FROM asignaturas a CROSS JOIN periodos p WHERE a.codigo='PRO' AND p.anio=2026;
INSERT INTO requisitos_plantilla(asignatura_id,periodo_id,codigo,nombre) SELECT a.id,p.id,'PROYECTO','Proyecto de intervención' FROM asignaturas a CROSS JOIN periodos p WHERE a.codigo='PRO' AND p.anio=2026;
