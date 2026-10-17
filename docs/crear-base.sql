-- Ejecutar una vez con una cuenta administradora, nunca con la cuenta de la aplicacion.
-- Copiar como docs/crear-base.local.sql y reemplazar los dos marcadores antes de ejecutar.
-- El archivo local esta excluido de Git. No elimina bases ni datos existentes.
CREATE DATABASE IF NOT EXISTS practicas_ubb CHARACTER SET utf8mb4;
CREATE DATABASE IF NOT EXISTS practicas_ubb_test CHARACTER SET utf8mb4;
CREATE USER IF NOT EXISTS 'practicas_app'@'localhost' IDENTIFIED BY 'REEMPLAZAR_CLAVE_DESARROLLO';
CREATE USER IF NOT EXISTS 'practicas_test'@'localhost' IDENTIFIED BY 'REEMPLAZAR_CLAVE_PRUEBAS';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES, DROP
 ON practicas_ubb.* TO 'practicas_app'@'localhost';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES, DROP
 ON practicas_ubb_test.* TO 'practicas_test'@'localhost';
-- Los permisos DDL permiten ejecutar Flyway. No se otorgan permisos globales.
-- Si el usuario ya existe, IF NOT EXISTS no cambia su clave: usar su clave actual
-- o ALTER USER de forma deliberada, sin guardar la nueva clave en Git.
