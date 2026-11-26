-- Calificaciones internas de cada práctica, en escala chilena de 1,0 a 7,0 con ponderación.
-- Las registra el profesor UBB asignado o la coordinación. No reemplazan el acta oficial.
CREATE TABLE calificaciones (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 practica_id BIGINT NOT NULL,
 evaluacion VARCHAR(150) NOT NULL,
 nota DECIMAL(2,1) NOT NULL,
 ponderacion INT NOT NULL,
 comentario VARCHAR(2000) NOT NULL DEFAULT '',
 autor_id BIGINT NOT NULL,
 creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT fk_calificacion_practica FOREIGN KEY (practica_id) REFERENCES practicas(id),
 CONSTRAINT fk_calificacion_autor FOREIGN KEY (autor_id) REFERENCES usuarios(id),
 CONSTRAINT ck_calificacion_nota CHECK (nota BETWEEN 1.0 AND 7.0),
 CONSTRAINT ck_calificacion_ponderacion CHECK (ponderacion BETWEEN 1 AND 100),
 INDEX ix_calificacion_practica (practica_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
