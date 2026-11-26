package cl.ubb.practicas;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Rúbrica de desempeño basada en los cuatro dominios del Marco para la Buena Enseñanza (MBE).
 * Cada criterio se evalúa de 1 (insatisfactorio) a 4 (destacado). La nota sugerida es una conversión lineal
 * del puntaje promedio a la escala 1,0–7,0: nivel 1 → 1,0; 2,5 → 4,0; 4 → 7,0.
 */
public final class ReglasRubrica {
    private ReglasRubrica() { }

    public record Criterio(String codigo,String dominio,String descripcion) { }
    public static final Map<String,String> DOMINIOS;
    static {
        var d=new LinkedHashMap<String,String>();
        d.put("A","Preparación de la enseñanza");d.put("B","Creación de un ambiente propicio para el aprendizaje");
        d.put("C","Enseñanza para el aprendizaje de todos los estudiantes");d.put("D","Responsabilidades profesionales");
        DOMINIOS=Collections.unmodifiableMap(d);
    }
    public static final List<Criterio> CRITERIOS=List.of(
        new Criterio("A1","A","Domina los contenidos matemáticos que enseña y su didáctica."),
        new Criterio("A2","A","Planifica clases con objetivos claros y coherentes con el currículum."),
        new Criterio("A3","A","Diseña estrategias, recursos y evaluaciones adecuados al curso."),
        new Criterio("B1","B","Establece un clima de respeto, confianza y equidad."),
        new Criterio("B2","B","Maneja normas de convivencia y organiza el tiempo y el espacio del aula."),
        new Criterio("B3","B","Promueve altas expectativas y la participación de todos los estudiantes."),
        new Criterio("C1","C","Comunica con claridad los objetivos y los contenidos de la clase."),
        new Criterio("C2","C","Usa estrategias que desafían el razonamiento y la resolución de problemas."),
        new Criterio("C3","C","Evalúa durante la clase y retroalimenta el aprendizaje."),
        new Criterio("D1","D","Reflexiona sobre su práctica y busca mejorarla."),
        new Criterio("D2","D","Trabaja colaborativamente con el profesor colaborador y el equipo del centro."),
        new Criterio("D3","D","Cumple con puntualidad, asistencia y compromisos asumidos."));
    public static final Map<Integer,String> NIVELES=Map.of(1,"Insatisfactorio",2,"Básico",3,"Competente",4,"Destacado");

    /** Valida que estén todos los criterios con un nivel de 1 a 4. Devuelve el mensaje de error o null. */
    public static String validar(Map<String,Integer> niveles) {
        if(niveles==null)return "Completa la rúbrica.";
        for(var c:CRITERIOS) {
            Integer n=niveles.get(c.codigo());
            if(n==null)return "Falta evaluar el criterio "+c.codigo()+": "+c.descripcion();
            if(n<1 || n>4)return "El nivel del criterio "+c.codigo()+" debe estar entre 1 y 4.";
        }
        if(niveles.size()!=CRITERIOS.size())return "La rúbrica tiene criterios que no existen.";
        return null;
    }
    public static BigDecimal puntaje(Map<String,Integer> niveles) {
        int suma=CRITERIOS.stream().mapToInt(c -> niveles.get(c.codigo())).sum();
        return BigDecimal.valueOf(suma).divide(BigDecimal.valueOf(CRITERIOS.size()),2,RoundingMode.HALF_UP);
    }
    /** nota = 2 × puntaje − 1, con un decimal. */
    public static BigDecimal notaSugerida(Map<String,Integer> niveles) {
        int suma=CRITERIOS.stream().mapToInt(c -> niveles.get(c.codigo())).sum();
        return BigDecimal.valueOf(2L*suma).divide(BigDecimal.valueOf(CRITERIOS.size()),6,RoundingMode.HALF_UP)
            .subtract(BigDecimal.ONE).setScale(1,RoundingMode.HALF_UP);
    }
    public static String serializar(Map<String,Integer> niveles) {
        var b=new StringJoiner(";");for(var c:CRITERIOS)b.add(c.codigo()+"="+niveles.get(c.codigo()));return b.toString();
    }
    public static Map<String,Integer> leer(String texto) {
        var m=new LinkedHashMap<String,Integer>();
        for(String par:texto.split(";")){var p=par.split("=");if(p.length==2)m.put(p[0],Integer.parseInt(p[1]));}
        return m;
    }
    /** Promedio por dominio (A, B, C, D), útil para ver fortalezas y debilidades. */
    public static Map<String,BigDecimal> porDominio(Map<String,Integer> niveles) {
        var r=new LinkedHashMap<String,BigDecimal>();
        for(String d:DOMINIOS.keySet()) {
            var lista=CRITERIOS.stream().filter(c -> c.dominio().equals(d)).toList();
            int suma=lista.stream().mapToInt(c -> niveles.getOrDefault(c.codigo(),0)).sum();
            r.put(d,BigDecimal.valueOf(suma).divide(BigDecimal.valueOf(lista.size()),1,RoundingMode.HALF_UP));
        }
        return r;
    }
}
