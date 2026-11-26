-- Período actual e historial.
-- Solo el período ACTUAL genera plazos vencidos y avisos, y es el único que ven estudiantes y docentes.
-- Los períodos HISTORICOS sirven para registrar el historial de la carrera (por ejemplo, traspasar
-- prácticas en papel de años anteriores) y solo los administra la coordinación.
ALTER TABLE periodos ADD COLUMN estado VARCHAR(10) NOT NULL DEFAULT 'HISTORICO';
ALTER TABLE periodos ADD CONSTRAINT ck_periodo_estado CHECK (estado IN ('ACTUAL','HISTORICO'));

-- El período más reciente pasa a ser el actual.
UPDATE periodos p JOIN (SELECT id FROM periodos ORDER BY anio DESC,semestre DESC LIMIT 1) ultimo ON ultimo.id=p.id
SET p.estado='ACTUAL';
