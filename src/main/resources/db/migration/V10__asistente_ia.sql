-- Asistente con IA: respuestas redactadas por el modelo (IA) y explicaciones de documentos (ARCHIVO).
ALTER TABLE asistente_mensajes DROP CHECK ck_mensaje_tipo;
ALTER TABLE asistente_mensajes ADD CONSTRAINT ck_mensaje_tipo CHECK (tipo IN ('MATERIAL','PLAZOS','SIN_RESPALDO','IA','ARCHIVO'));
