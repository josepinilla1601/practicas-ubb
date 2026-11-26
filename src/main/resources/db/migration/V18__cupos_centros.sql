-- Asignación de centros por cupos.
-- Cada centro ofrece una cantidad de cupos por período; el estudiante marca hasta tres centros en orden de preferencia
-- y la coordinación asigna (a mano o automáticamente) respetando los cupos.
CREATE TABLE cupos_centro (
 centro_id BIGINT NOT NULL,
 periodo_id BIGINT NOT NULL,
 cupos INT NOT NULL,
 PRIMARY KEY (centro_id,periodo_id),
 CONSTRAINT fk_cupo_centro FOREIGN KEY (centro_id) REFERENCES centros(id),
 CONSTRAINT fk_cupo_periodo FOREIGN KEY (periodo_id) REFERENCES periodos(id),
 CONSTRAINT ck_cupos CHECK (cupos BETWEEN 0 AND 200)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE preferencias_centro (
 practica_id BIGINT NOT NULL,
 prioridad TINYINT NOT NULL,
 centro_id BIGINT NOT NULL,
 creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY (practica_id,prioridad),
 CONSTRAINT uk_preferencia_centro UNIQUE (practica_id,centro_id),
 CONSTRAINT fk_preferencia_practica FOREIGN KEY (practica_id) REFERENCES practicas(id) ON DELETE CASCADE,
 CONSTRAINT fk_preferencia_centro FOREIGN KEY (centro_id) REFERENCES centros(id),
 CONSTRAINT ck_prioridad CHECK (prioridad BETWEEN 1 AND 3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
