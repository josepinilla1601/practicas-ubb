package cl.ubb.practicas;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/**
 * S2-18: responde con el material publicado y cita sus fuentes.
 * S2-19: informa los pendientes y plazos reales del estudiante.
 * S2-20: guarda y muestra el historial personal de cada cuenta.
 */
@RestController
@RequestMapping("/api")
public class AsistenteController {
    private final JdbcTemplate db;
    private final Acceso acceso;
    private final PracticasService servicio;
    private final ObjectMapper json;
    public AsistenteController(JdbcTemplate db,Acceso acceso,PracticasService servicio,ObjectMapper json) {this.db=db;this.acceso=acceso;this.servicio=servicio;this.json=json;}
    public record Pregunta(@NotBlank @Size(max=1000) String pregunta) { }

    private static final Set<String> PALABRAS_PLAZO=Set.of("pendiente","plazo","vence","vencen","vencido","vencida","atrasado","atrasada","atraso");
    private static final Set<String> PALABRAS_PERSONALES=Set.of("mis","me","tengo","debo","falta","faltan","yo");
    private static final Set<String> PALABRAS_ENTREGA=Set.of("entregar","entrega","entregas","requisito","requisitos","documento","documentos","falta","faltan");

    static boolean preguntaPorPlazos(String pregunta) {
        var palabras=new HashSet<>(Arrays.asList(BuscadorMaterial.sinTildes(pregunta).split("[^a-z0-9ñ]+")));
        if(palabras.stream().anyMatch(p -> PALABRAS_PLAZO.contains(p) || p.startsWith("pendiente") || p.startsWith("plazo")))return true;
        return palabras.stream().anyMatch(PALABRAS_PERSONALES::contains) && palabras.stream().anyMatch(PALABRAS_ENTREGA::contains);
    }

    @PostMapping("/asistente") public Map<String,Object> preguntar(@Valid @RequestBody Pregunta datos) {
        var usuario=acceso.actual();
        String pregunta=datos.pregunta().strip();
        Map<String,Object> respuesta="ESTUDIANTE".equals(usuario.get("rol")) && preguntaPorPlazos(pregunta)?plazos():material(usuario,pregunta);
        String fuentes;
        try { fuentes=json.writeValueAsString(respuesta.get("fuentes")); } catch(JsonProcessingException ex) { fuentes="[]"; }
        db.update("INSERT INTO asistente_mensajes(usuario_id,pregunta,respuesta,tipo,fuentes) VALUES(?,?,?,?,?)",usuario.get("id"),pregunta,respuesta.get("respuesta"),respuesta.get("tipo"),fuentes);
        var r=new LinkedHashMap<String,Object>(respuesta);r.put("pregunta",pregunta);
        return r;
    }

    @GetMapping("/asistente/historial") public List<Map<String,Object>> historial() {
        var mensajes=db.queryForList("SELECT id,pregunta,respuesta,tipo,fuentes,creado_en FROM asistente_mensajes WHERE usuario_id=? ORDER BY id DESC LIMIT 50",acceso.id());
        for(var m:mensajes) {
            try { m.put("fuentes",json.readValue(String.valueOf(m.get("fuentes")),new TypeReference<List<Map<String,Object>>>() { })); }
            catch(JsonProcessingException ex) { m.put("fuentes",List.of()); }
        }
        Collections.reverse(mensajes);
        return mensajes;
    }

    /** S2-19: solo usa las prácticas del propio estudiante, filtradas en el servidor. */
    private Map<String,Object> plazos() {
        var f=acceso.filtroPracticas();
        var asignaturas=new HashMap<Long,String>();
        for(var p:servicio.practicas())asignaturas.put(((Number)p.get("id")).longValue(),p.get("asignatura")+" "+p.get("anio")+"-"+p.get("semestre"));
        var pendientes=servicio.requisitos(f.sql(),f.args()).stream().filter(r -> ReglasSeguimiento.pendiente(String.valueOf(r.get("estado"))))
            .sorted(Comparator.comparing(r -> r.get("fecha_limite")==null?"9999-12-31":r.get("fecha_limite").toString())).toList();
        StringBuilder texto=new StringBuilder();
        if(asignaturas.isEmpty())texto.append("Todavía no tienes prácticas registradas. Consulta con la coordinación.");
        else if(pendientes.isEmpty())texto.append("No tienes requisitos pendientes ni correcciones solicitadas. Las entregas en revisión aparecen en tu panel.");
        else {
            texto.append("Tienes ").append(pendientes.size()).append(pendientes.size()==1?" requisito pendiente:":" requisitos pendientes:");
            for(var r:pendientes) {
                texto.append("\n• ").append(r.get("nombre")).append(" (").append(asignaturas.get(((Number)r.get("practica_id")).longValue())).append(")");
                texto.append(r.get("fecha_limite")==null?" — sin fecha límite":" — plazo: "+r.get("fecha_limite"));
                if(Boolean.TRUE.equals(r.get("vencido")))texto.append(" — FUERA DE PLAZO");
                if("OBSERVADO".equals(r.get("estado")))texto.append(" — debes subir una versión corregida");
            }
        }
        return Map.of("tipo","PLAZOS","respuesta",texto.toString(),"fuentes",List.of(Map.of("titulo","Tu seguimiento de prácticas","tipo","SEGUIMIENTO")));
    }

    /** S2-18: el estudiante solo consulta material publicado de las asignaturas y períodos de sus prácticas. */
    private Map<String,Object> material(Map<String,Object> usuario,String pregunta) {
        String sql="SELECT f.material_id,m.titulo,f.orden,f.texto FROM material_fragmentos f JOIN materiales m ON m.id=f.material_id WHERE m.estado='PUBLICADO'";
        List<Object> args=new ArrayList<>();
        if("ESTUDIANTE".equals(usuario.get("rol")) || "PROFESOR_COLEGIO".equals(usuario.get("rol"))) {
            var filtro=acceso.filtroPracticas();
            sql+=" AND EXISTS(SELECT 1 FROM practicas p WHERE p.asignatura_id=m.asignatura_id AND p.periodo_id=m.periodo_id AND "+filtro.sql()+")";
            args.addAll(Arrays.asList(filtro.args()));
        }
        var fragmentos=db.query(sql,(rs,n) -> new BuscadorMaterial.Fragmento(rs.getLong(1),rs.getString(2),rs.getInt(3),rs.getString(4)),args.toArray());
        var resultados=BuscadorMaterial.buscar(pregunta,fragmentos,3);
        if(resultados.isEmpty())
            return Map.of("tipo","SIN_RESPALDO","fuentes",List.of(),"respuesta","No encontré información suficiente en el material publicado para responder con seguridad. "+
                "Prueba con otras palabras o consulta directamente a tu profesor.");
        StringBuilder texto=new StringBuilder("Según el material publicado por tus profesores:");
        List<Map<String,Object>> fuentes=new ArrayList<>();
        for(int i=0;i<resultados.size();i++) {
            var r=resultados.get(i);
            texto.append("\n\n[").append(i+1).append("] ").append(r.extracto());
            fuentes.add(Map.of("numero",i+1,"titulo",r.fragmento().titulo(),"materialId",r.fragmento().materialId(),"fragmento",r.fragmento().orden(),"tipo","MATERIAL"));
        }
        texto.append("\n\nRevisa la fuente completa si necesitas más detalle.");
        return Map.of("tipo","MATERIAL","respuesta",texto.toString(),"fuentes",fuentes);
    }
}
