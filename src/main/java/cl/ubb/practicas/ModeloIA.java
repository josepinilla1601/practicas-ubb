package cl.ubb.practicas;

/**
 * Modelo de lenguaje que usa el asistente. Separarlo permite cambiar de proveedor
 * y usar una versión simulada en las pruebas, sin llamar a servicios externos.
 */
public interface ModeloIA {
    /** true si hay una clave configurada y el asistente puede usar IA. */
    boolean disponible();

    /** Genera una respuesta. Lanza {@link Falla} si el servicio no responde o se agotó el cupo. */
    String generar(String instrucciones,String contenido);

    class Falla extends RuntimeException {
        public final boolean cupo;
        public Falla(String mensaje,boolean cupo) { super(mensaje); this.cupo=cupo; }
    }
}
