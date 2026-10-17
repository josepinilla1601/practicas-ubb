# Versiones del proyecto
El parent Spring Boot 3.5.16 administra las siete dependencias directas. No se fijan versiones individuales de starters, Flyway, JUnit ni del driver. mysql-connector-j conserva scope runtime. Maven Wrapper utiliza Maven 3.9.9.
Se mantiene Spring Boot durante este laboratorio. La migración a Spring Boot 4.x queda en el Product Backlog, según la indicación de la guía.
Comprobaciones: .\mvnw.cmd dependency:tree y .\mvnw.cmd versions:display-dependency-updates. Una actualización requiere ejecutar verify y revisar las cinco HU del Sprint 1.
