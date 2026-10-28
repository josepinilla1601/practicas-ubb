package cl.ubb.practicas;

import java.time.*;

/** Reglas puras, compartidas por seguimiento, indicadores y asistente. */
public final class ReglasSeguimiento {
    public static final ZoneId ZONA=ZoneId.of("America/Santiago");
    private ReglasSeguimiento() { }
    public static LocalDate hoy() { return LocalDate.now(ZONA); }
    public static boolean vencido(LocalDate limite,boolean pendiente,LocalDate hoy) {
        return pendiente && limite!=null && limite.isBefore(hoy);
    }
}
