-- S2-11 a S2-13: cada versión entregada recibe como máximo una revisión. Las versiones anteriores se conservan.
CREATE TABLE revisiones (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 documento_id BIGINT NOT NULL,
 revisor_id BIGINT NOT NULL,
 decision VARCHAR(20) NOT NULL,
 comentario VARCHAR(2000) NOT NULL DEFAULT '',
 creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT fk_revision_documento FOREIGN KEY (documento_id) REFERENCES documentos(id),
 CONSTRAINT fk_revision_revisor FOREIGN KEY (revisor_id) REFERENCES usuarios(id),
 CONSTRAINT uk_revision_documento UNIQUE (documento_id),
 CONSTRAINT ck_revision_decision CHECK (decision IN ('APROBADO','OBSERVADO'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
