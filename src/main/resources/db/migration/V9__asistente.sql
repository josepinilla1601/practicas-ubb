-- S2-18 a S2-20: historial personal de consultas al asistente.
CREATE TABLE asistente_mensajes (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 usuario_id BIGINT NOT NULL,
 pregunta VARCHAR(1000) NOT NULL,
 respuesta TEXT NOT NULL,
 tipo VARCHAR(20) NOT NULL,
 fuentes TEXT NOT NULL,
 creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT fk_mensaje_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id),
 CONSTRAINT ck_mensaje_tipo CHECK (tipo IN ('MATERIAL','PLAZOS','SIN_RESPALDO')),
 INDEX ix_mensaje_usuario (usuario_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
