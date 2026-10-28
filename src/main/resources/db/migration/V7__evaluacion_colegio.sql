-- S2-14: evaluación cualitativa del profesor de colegio. No reemplaza ni calcula notas oficiales.
CREATE TABLE evaluaciones_colegio (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 practica_id BIGINT NOT NULL,
 autor_id BIGINT NOT NULL,
 fortalezas VARCHAR(3000) NOT NULL,
 aspectos_mejora VARCHAR(3000) NOT NULL,
 recomendaciones VARCHAR(3000) NOT NULL DEFAULT '',
 creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT fk_evaluacion_practica FOREIGN KEY (practica_id) REFERENCES practicas(id),
 CONSTRAINT fk_evaluacion_autor FOREIGN KEY (autor_id) REFERENCES usuarios(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
