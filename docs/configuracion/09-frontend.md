# Frontend del proyecto
HTML5, CSS y JavaScript nativo en src/main/resources/static. Spring Boot sirve estos archivos. No se usa React, Angular, Vue, Thymeleaf, npm ni librerias externas por CDN.
app.js llama fetch('/api'+path): la ruta es relativa al mismo servidor y no fija localhost ni otro dominio. No necesita variable VITE_API_URL.
No corresponde agregar package.json, package-lock.json, npm ci ni npm audit a este proyecto. Si se incorpora una biblioteca externa, se fijara su version y se revisara su licencia y seguridad.
