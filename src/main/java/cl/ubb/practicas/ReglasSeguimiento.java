package cl.ubb.practicas;

import java.time.*;

/** Reglas puras, compartidas por seguimiento, indicadores y asistente. */
public final class ReglasSeguimiento {
    public static final ZoneId ZONA=ZoneId.of("America/Santiago");
    private ReglasSeguimiento() { }
    public static LocalDate hoy() { return LocalDate.now(ZONA); }
    /** Estado de un requisito según su última versión entregada y la revisión de esa versión. */
    public static String estado(long archivos,String ultimaDecision) {
        if(archivos==0)return "PENDIENTE";
        if(ultimaDecision==null)return "EN_REVISION";
        return ultimaDecision;
    }
    /** Un requisito sigue pendiente para el estudiante mientras no tenga entrega o tenga una corrección solicitada. */
    public static boolean pendiente(String estado) { return "PENDIENTE".equals(estado) || "OBSERVADO".equals(estado); }
    public static boolean vencido(LocalDate limite,boolean pendiente,LocalDate hoy) {
        return pendiente && limite!=null && limite.isBefore(hoy);
    }
    public static LocalDate fecha(Object valor) {
        if(valor==null)return null;
        if(valor instanceof java.sql.Date d)return d.toLocalDate();
        if(valor instanceof LocalDate d)return d;
        return LocalDate.parse(valor.toString());
    }
}
