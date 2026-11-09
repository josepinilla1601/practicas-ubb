-- Respuestas del asistente sobre cómo usar la plataforma (AYUDA).
ALTER TABLE asistente_mensajes DROP CHECK ck_mensaje_tipo;
ALTER TABLE asistente_mensajes ADD CONSTRAINT ck_mensaje_tipo CHECK (tipo IN ('MATERIAL','PLAZOS','SIN_RESPALDO','IA','ARCHIVO','AYUDA'));
