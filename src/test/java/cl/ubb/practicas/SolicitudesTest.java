package cl.ubb.practicas;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class SolicitudesTest {
    private static ValidatorFactory factory;
    private static Validator validator;
    @BeforeAll static void preparar() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }
    @AfterAll static void cerrar() { factory.close(); }
    @Test void aceptaEstudianteValido() {
        assertTrue(validator.validate(new Solicitudes.Estudiante("EST-1", "Ana", "Perez", "ana@example.com")).isEmpty());
    }
    @Test void rechazaCorreoSinFormato() {
        assertTrue(validator.validate(new Solicitudes.Estudiante("EST-1", "Ana", "Perez", "incorrecto"))
            .stream().anyMatch(v -> v.getPropertyPath().toString().equals("correo")));
    }
    @Test void rechazaSemestreFueraDelCalendario() {
        assertTrue(validator.validate(new Solicitudes.Practica(1L, 1L, 2026, 3, "Profesor", null))
            .stream().anyMatch(v -> v.getPropertyPath().toString().equals("semestre")));
    }
    @Test void rechazaCentroSinComuna() {
        assertTrue(validator.validate(new Solicitudes.Centro("Colegio", "", null, null, null, null))
            .stream().anyMatch(v -> v.getPropertyPath().toString().equals("comuna")));
    }
}
