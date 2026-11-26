package cl.ubb.practicas;

import java.util.List;

/**
 * Modelo de lenguaje que usa el asistente. Separarlo permite cambiar de proveedor
 * y usar una versión simulada en las pruebas, sin llamar a servicios externos.
 */
public interface ModeloIA {
    /** true si hay una clave configurada y el asistente puede usar IA. */
    boolean disponible();

    /** Genera una respuesta. Lanza {@link Falla} si el servicio no responde o se agotó el cupo. */
    String generar(String instrucciones,String contenido);

    /**
     * Genera una respuesta permitiendo que el modelo busque en internet cuando lo necesite.
     * Devuelve las páginas que usó; si no buscó (o la búsqueda no está disponible), la lista va vacía.
     */
    default Resultado generarConInternet(String instrucciones,String contenido) {
        return new Resultado(generar(instrucciones,contenido),List.of());
    }

    /** Página de internet usada por la IA. */
    record FuenteWeb(String titulo,String url) { }
    record Resultado(String texto,List<FuenteWeb> web) { }

    class Falla extends RuntimeException {
        public final boolean cupo;
        public Falla(String mensaje,boolean cupo) { super(mensaje); this.cupo=cupo; }
    }
}
