# Migraciones con Flyway y JDBC
El proyecto usa JdbcTemplate, no JPA/Hibernate. Por eso spring.jpa.hibernate.ddl-auto=validate no valida este modelo y no se agrega una propiedad que no se ejecutaria.
Ya existen V1__estructura.sql y V2__catalogo_y_requisitos_demo.sql. Se conservan intactas. La siguiente modificacion debera ser V3__descripcion.sql. No borrar bases existentes ni activar baseline-on-migrate para ocultar errores.
Flyway valida nombres, orden y checksums al migrar. Esto no compara todas las columnas con clases Java: las pruebas de integracion verifican las consultas reales y las restricciones de MySQL.
Consulta de evidencia, ejecutar en MySQL:
SELECT installed_rank, version, description, success FROM flyway_schema_history ORDER BY installed_rank;
Verificar filas 1 y 2 con success=1; guardar la salida real. En una base ya migrada no se espera repetir Successfully applied: puede indicar que esta actualizada.
