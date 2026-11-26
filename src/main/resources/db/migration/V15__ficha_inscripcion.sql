-- S3: ficha de inscripción de la práctica. El estudiante la completa y envía, el profesor de colegio la confirma
-- y la coordinación la aprueba. Cualquiera de ellos puede devolverla con un comentario.
CREATE TABLE fichas_inscripcion (
 practica_id BIGINT PRIMARY KEY,
 curso VARCHAR(60) NOT NULL,
 nivel VARCHAR(60) NOT NULL,
 dias VARCHAR(120) NOT NULL,
 horario VARCHAR(120) NOT NULL,
 fecha_inicio DATE NOT NULL,
 fecha_termino DATE NOT NULL,
 horas_semanales INT NOT NULL,
 observaciones VARCHAR(1000) NOT NULL DEFAULT '',
 estado VARCHAR(12) NOT NULL DEFAULT 'BORRADOR',
 comentario VARCHAR(1000) NOT NULL DEFAULT '',
 enviada_en TIMESTAMP NULL,
 confirmada_por BIGINT NULL,
 confirmada_en TIMESTAMP NULL,
 aprobada_por BIGINT NULL,
 aprobada_en TIMESTAMP NULL,
 actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 CONSTRAINT fk_ficha_practica FOREIGN KEY (practica_id) REFERENCES practicas(id),
 CONSTRAINT fk_ficha_confirma FOREIGN KEY (confirmada_por) REFERENCES usuarios(id),
 CONSTRAINT fk_ficha_aprueba FOREIGN KEY (aprobada_por) REFERENCES usuarios(id),
 CONSTRAINT ck_ficha_estado CHECK (estado IN ('BORRADOR','ENVIADA','CONFIRMADA','APROBADA','DEVUELTA')),
 CONSTRAINT ck_ficha_fechas CHECK (fecha_termino >= fecha_inicio),
 CONSTRAINT ck_ficha_horas CHECK (horas_semanales BETWEEN 1 AND 45)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
