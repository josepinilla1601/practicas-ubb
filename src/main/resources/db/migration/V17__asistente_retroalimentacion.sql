-- Asistente: preguntas de reflexión antes de retroalimentar un trabajo (REFLEXION),
-- apoyo pedagógico general (PRACTICA) y preguntas sin relación con la práctica (FUERA_DE_TEMA).
ALTER TABLE asistente_mensajes DROP CHECK ck_mensaje_tipo;
ALTER TABLE asistente_mensajes ADD CONSTRAINT ck_mensaje_tipo CHECK (tipo IN ('MATERIAL','PLAZOS','SIN_RESPALDO','IA','ARCHIVO','AYUDA','MATEMATICA','REFLEXION','PRACTICA','FUERA_DE_TEMA'));
