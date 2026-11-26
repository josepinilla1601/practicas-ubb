package cl.ubb.practicas;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** Reglas de las calificaciones internas: escala chilena de 1,0 a 7,0, aprobación con 4,0 y promedio ponderado. */
public final class ReglasCalificacion {
    private ReglasCalificacion() { }
    public static final BigDecimal MINIMA=new BigDecimal("1.0"),MAXIMA=new BigDecimal("7.0"),APROBACION=new BigDecimal("4.0");

    public record Nota(BigDecimal nota,int ponderacion) { }

    /** Promedio ponderado con un decimal (redondeo tradicional: 4,45 → 4,5). null si no hay notas. */
    public static BigDecimal promedio(List<Nota> notas) {
        int total=ponderacionTotal(notas);
        if(total==0)return null;
        BigDecimal suma=BigDecimal.ZERO;
        for(var n:notas)suma=suma.add(n.nota().multiply(BigDecimal.valueOf(n.ponderacion())));
        return suma.divide(BigDecimal.valueOf(total),1,RoundingMode.HALF_UP);
    }
    public static int ponderacionTotal(List<Nota> notas) { return notas.stream().mapToInt(Nota::ponderacion).sum(); }

    /** SIN_NOTAS, EN_CURSO (aún no se evalúa el 100 %), APROBADA o REPROBADA. */
    public static String situacion(List<Nota> notas) {
        int total=ponderacionTotal(notas);
        if(total==0)return "SIN_NOTAS";
        if(total<100)return "EN_CURSO";
        return promedio(notas).compareTo(APROBACION)>=0?"APROBADA":"REPROBADA";
    }

    /** Valida una nota: entre 1,0 y 7,0 y con un solo decimal. Devuelve el mensaje de error o null. */
    public static String validarNota(BigDecimal nota) {
        if(nota==null)return "Escribe la nota.";
        if(nota.compareTo(MINIMA)<0 || nota.compareTo(MAXIMA)>0)return "La nota debe estar entre 1,0 y 7,0.";
        if(nota.stripTrailingZeros().scale()>1)return "Usa un solo decimal, por ejemplo 5,5.";
        return null;
    }
}
