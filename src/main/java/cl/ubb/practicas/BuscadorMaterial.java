package cl.ubb.practicas;

import java.text.Normalizer;
import java.util.*;

/**
 * S2-18: búsqueda por palabras clave sobre los fragmentos del material publicado.
 * Funciona sin servicios externos. Puede reemplazarse más adelante por un modelo de lenguaje con API de pago.
 */
public final class BuscadorMaterial {
    private BuscadorMaterial() { }
    public record Fragmento(long materialId,String titulo,int orden,String texto) { }
    public record Resultado(Fragmento fragmento,double puntaje,String extracto) { }

    private static final Set<String> VACIAS=Set.of(
        "que","cual","cuales","como","donde","cuando","quien","quienes","cuanto","cuanta","cuantos","cuantas","para","por","con","sin","sobre","entre",
        "los","las","del","una","unos","unas","este","esta","estos","estas","ese","esa","eso","hay","son","fue","ser","estan","tiene","tienen",
        "puedo","puede","pueden","debo","debe","deben","hacer","algo","mas","muy","sus","mis","tus","nos","les","pero","porque","segun","the","and");

    public static String sinTildes(String texto) {
        return Normalizer.normalize(texto,Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT);
    }
    /** Palabras significativas, sin tildes y con un plural simple reducido a singular. */
    public static List<String> palabras(String texto) {
        List<String> salida=new ArrayList<>();
        for(String p:sinTildes(texto).split("[^a-z0-9ñ]+")) {
            if(p.length()<3 || VACIAS.contains(p))continue;
            if(p.length()>5 && p.endsWith("es"))p=p.substring(0,p.length()-2);
            else if(p.length()>4 && p.endsWith("s"))p=p.substring(0,p.length()-1);
            salida.add(p);
        }
        return salida;
    }

    /** Devuelve hasta 'maximo' fragmentos que cubren suficientes palabras de la pregunta, del más al menos relevante. */
    public static List<Resultado> buscar(String pregunta,List<Fragmento> fragmentos,int maximo) {
        var consulta=new LinkedHashSet<>(palabras(pregunta));
        if(consulta.isEmpty() || fragmentos.isEmpty())return List.of();
        List<Set<String>> indice=new ArrayList<>();Map<String,Integer> frecuencia=new HashMap<>();
        for(var f:fragmentos) {
            var set=new HashSet<>(palabras(f.titulo()+" "+f.texto()));indice.add(set);
            for(String q:consulta)if(set.contains(q))frecuencia.merge(q,1,Integer::sum);
        }
        int minimo=consulta.size()==1?1:Math.max(2,(int)Math.ceil(consulta.size()*0.5));
        List<Resultado> resultados=new ArrayList<>();
        for(int i=0;i<fragmentos.size();i++) {
            double puntaje=0;int coincidencias=0;
            for(String q:consulta)if(indice.get(i).contains(q)){coincidencias++;puntaje+=1+Math.log((double)fragmentos.size()/frecuencia.get(q));}
            if(coincidencias>=minimo)resultados.add(new Resultado(fragmentos.get(i),puntaje,extracto(fragmentos.get(i).texto(),consulta)));
        }
        resultados.sort(Comparator.comparingDouble(Resultado::puntaje).reversed());
        return resultados.subList(0,Math.min(maximo,resultados.size()));
    }

    /** Elige las oraciones del fragmento que contienen más palabras de la pregunta. */
    static String extracto(String texto,Set<String> consulta) {
        String[] oraciones=texto.replace('\n',' ').split("(?<=[.!?;:])\\s+");
        int mejor=-1,mejorPuntaje=-1;
        for(int i=0;i<oraciones.length;i++) {
            var set=new HashSet<>(palabras(oraciones[i]));int n=0;
            for(String q:consulta)if(set.contains(q))n++;
            if(n>mejorPuntaje){mejor=i;mejorPuntaje=n;}
        }
        StringBuilder b=new StringBuilder(oraciones[mejor].strip());
        if(mejor+1<oraciones.length && b.length()<220)b.append(' ').append(oraciones[mejor+1].strip());
        return b.length()>450?b.substring(0,450).strip()+"…":b.toString();
    }
}
