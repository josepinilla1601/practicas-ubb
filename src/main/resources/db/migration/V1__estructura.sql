CREATE TABLE estudiantes (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 identificador VARCHAR(30) NOT NULL,
 nombres VARCHAR(100) NOT NULL,
 apellidos VARCHAR(100) NOT NULL,
 correo VARCHAR(150) NOT NULL,
 creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT uk_estudiante_identificador UNIQUE (identificador)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE centros (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 nombre VARCHAR(150) NOT NULL,
 comuna VARCHAR(100) NOT NULL,
 direccion VARCHAR(200) NOT NULL DEFAULT '',
 contacto VARCHAR(150) NOT NULL DEFAULT '',
 correo VARCHAR(150) NOT NULL DEFAULT '',
 telefono VARCHAR(40) NOT NULL DEFAULT ''
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE asignaturas (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 codigo VARCHAR(20) NOT NULL UNIQUE,
 nombre VARCHAR(150) NOT NULL,
 ciclo VARCHAR(20) NOT NULL,
 requiere_centro BOOLEAN NOT NULL,
 CONSTRAINT ck_ciclo CHECK (ciclo IN ('INICIAL','INTERMEDIO','AVANZADO'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE periodos (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 anio INT NOT NULL,
 semestre INT NOT NULL,
 CONSTRAINT uk_periodo UNIQUE (anio,semestre),
 CONSTRAINT ck_anio CHECK (anio BETWEEN 2000 AND 2100),
 CONSTRAINT ck_semestre CHECK (semestre IN (1,2))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE requisitos_plantilla (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 asignatura_id BIGINT NOT NULL,
 periodo_id BIGINT NOT NULL,
 codigo VARCHAR(60) NOT NULL,
 nombre VARCHAR(180) NOT NULL,
 CONSTRAINT fk_plantilla_asignatura FOREIGN KEY (asignatura_id) REFERENCES asignaturas(id),
 CONSTRAINT fk_plantilla_periodo FOREIGN KEY (periodo_id) REFERENCES periodos(id),
 CONSTRAINT uk_plantilla UNIQUE(asignatura_id,periodo_id,codigo)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE practicas (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 estudiante_id BIGINT NOT NULL,
 asignatura_id BIGINT NOT NULL,
 periodo_id BIGINT NOT NULL,
 centro_id BIGINT NULL,
 profesor_responsable VARCHAR(150) NOT NULL,
 creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT fk_practica_estudiante FOREIGN KEY(estudiante_id) REFERENCES estudiantes(id),
 CONSTRAINT fk_practica_asignatura FOREIGN KEY(asignatura_id) REFERENCES asignaturas(id),
 CONSTRAINT fk_practica_periodo FOREIGN KEY(periodo_id) REFERENCES periodos(id),
 CONSTRAINT fk_practica_centro FOREIGN KEY(centro_id) REFERENCES centros(id),
 CONSTRAINT uk_practica UNIQUE(estudiante_id,asignatura_id,periodo_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Copia de los requisitos para conservar la lista de cada práctica aunque cambie la plantilla.
CREATE TABLE requisitos_practica (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 practica_id BIGINT NOT NULL,
 codigo VARCHAR(60) NOT NULL,
 nombre VARCHAR(180) NOT NULL,
 CONSTRAINT fk_requisito_practica FOREIGN KEY(practica_id) REFERENCES practicas(id),
 CONSTRAINT uk_requisito_practica UNIQUE(practica_id,codigo)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE documentos (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 requisito_id BIGINT NOT NULL,
 nombre_original VARCHAR(255) NOT NULL,
 tipo_contenido VARCHAR(150) NOT NULL,
 tamanio BIGINT NOT NULL,
 contenido LONGBLOB NOT NULL,
 cargado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT fk_documento_requisito FOREIGN KEY(requisito_id) REFERENCES requisitos_practica(id),
 CONSTRAINT ck_documento_tamanio CHECK (tamanio BETWEEN 1 AND 10485760)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
