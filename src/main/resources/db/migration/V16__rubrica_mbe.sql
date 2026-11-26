-- S3: evaluación del profesor de colegio con una rúbrica basada en los dominios del Marco para la Buena Enseñanza.
-- "niveles" guarda el nivel (1 a 4) de cada criterio, por ejemplo "A1=3;A2=4;...".
CREATE TABLE rubricas_colegio (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 practica_id BIGINT NOT NULL,
 autor_id BIGINT NOT NULL,
 niveles VARCHAR(200) NOT NULL,
 puntaje DECIMAL(3,2) NOT NULL,
 nota_sugerida DECIMAL(2,1) NOT NULL,
 comentario VARCHAR(2000) NOT NULL DEFAULT '',
 creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT fk_rubrica_practica FOREIGN KEY (practica_id) REFERENCES practicas(id),
 CONSTRAINT fk_rubrica_autor FOREIGN KEY (autor_id) REFERENCES usuarios(id),
 CONSTRAINT ck_rubrica_nota CHECK (nota_sugerida BETWEEN 1.0 AND 7.0),
 INDEX ix_rubrica_practica (practica_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
