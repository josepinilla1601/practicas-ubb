package cl.ubb.practicas;

import jakarta.validation.constraints.*;

/** Contratos de entrada: Spring valida estos campos antes de ejecutar las operaciones. */
public final class Solicitudes {
    private Solicitudes() { }
    public record Estudiante(
        @NotBlank @Size(max=30) String identificador,
        @NotBlank @Size(max=100) String nombres,
        @NotBlank @Size(max=100) String apellidos,
        @NotBlank @Email @Size(max=150) String correo) { }
    public record Centro(
        @NotBlank @Size(max=150) String nombre,
        @NotBlank @Size(max=100) String comuna,
        @Size(max=200) String direccion,
        @Size(max=150) String contacto,
        @Email @Size(max=150) String correo,
        @Size(max=40) String telefono) { }
    public record Practica(
        @NotNull @Positive Long estudianteId,
        @NotNull @Positive Long asignaturaId,
        @NotNull @Min(2000) @Max(2100) Integer anio,
        @NotNull @Min(1) @Max(2) Integer semestre,
        @NotBlank @Size(max=150) String profesorResponsable,
        @Positive Long centroId) { }
}
