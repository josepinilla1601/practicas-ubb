-- S2-16 y S2-17: material docente por asignatura y período. El texto extraído alimenta al asistente.
CREATE TABLE materiales (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 asignatura_id BIGINT NOT NULL,
 periodo_id BIGINT NOT NULL,
 titulo VARCHAR(180) NOT NULL,
 nombre_original VARCHAR(255) NOT NULL,
 tipo_contenido VARCHAR(100) NOT NULL,
 tamanio BIGINT NOT NULL,
 contenido LONGBLOB NOT NULL,
 texto MEDIUMTEXT NOT NULL,
 estado VARCHAR(20) NOT NULL DEFAULT 'BORRADOR',
 autor_id BIGINT NOT NULL,
 creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 actualizado_en TIMESTAMP NULL,
 CONSTRAINT fk_material_asignatura FOREIGN KEY (asignatura_id) REFERENCES asignaturas(id),
 CONSTRAINT fk_material_periodo FOREIGN KEY (periodo_id) REFERENCES periodos(id),
 CONSTRAINT fk_material_autor FOREIGN KEY (autor_id) REFERENCES usuarios(id),
 CONSTRAINT ck_material_estado CHECK (estado IN ('BORRADOR','PUBLICADO','RETIRADO')),
 CONSTRAINT ck_material_tamanio CHECK (tamanio BETWEEN 1 AND 10485760)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE material_fragmentos (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 material_id BIGINT NOT NULL,
 orden INT NOT NULL,
 texto TEXT NOT NULL,
 CONSTRAINT fk_fragmento_material FOREIGN KEY (material_id) REFERENCES materiales(id),
 CONSTRAINT uk_fragmento UNIQUE (material_id,orden)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
