ALTER TABLE practicas
 ADD COLUMN profesor_ubb_id BIGINT NULL,
 ADD COLUMN profesor_colegio_id BIGINT NULL,
 ADD CONSTRAINT fk_practica_profesor_ubb FOREIGN KEY (profesor_ubb_id) REFERENCES usuarios(id),
 ADD CONSTRAINT fk_practica_profesor_colegio FOREIGN KEY (profesor_colegio_id) REFERENCES usuarios(id);
