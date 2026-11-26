package cl.ubb.practicas;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.time.*;
import java.util.*;

/**
 * S3-01 a S3-03: bitácora de práctica (diario de campo).
 * El estudiante registra cada jornada; el profesor de colegio la visa u observa; el profesor UBB deja retroalimentación.
 */
@RestController
@RequestMapping("/api")
public class BitacoraController {
    private final JdbcTemplate db;
    private final Acceso acceso;
    public BitacoraController(JdbcTemplate db,Acceso acceso) {this.db=db;this.acceso=acceso;}

    public record Jornada(@NotNull LocalDate fecha,@NotNull LocalTime horaInicio,@NotNull LocalTime horaFin,
        @NotBlank @Size(max=60) String curso,@NotBlank @Pattern(regexp="OBSERVACION|AYUDANTIA|CLASE|REUNION|OTRA") String tipo,
        @NotBlank @Size(max=3000) String actividades,@Size(max=3000) String reflexion) { }
    public record Visado(@NotBlank @Pattern(regexp="VISADA|OBSERVADA") String decision,@Size(max=1000) String comentario) { }
    public record Comentario(@NotBlank @Size(max=2000) String comentario) { }

    private static final String JORNADAS="""
        SELECT j.*,TIMESTAMPDIFF(MINUTE,j.hora_inicio,j.hora_fin) minutos,v.nombre visador,c.nombre comentador,
          CONCAT(e.nombres,' ',e.apellidos) estudiante,a.nombre asignatura,j.practica_id
        FROM bitacora_jornadas j JOIN practicas p ON p.id=j.practica_id JOIN estudiantes e ON e.id=p.estudiante_id
        JOIN asignaturas a ON a.id=p.asignatura_id
        LEFT JOIN usuarios v ON v.id=j.visado_por LEFT JOIN usuarios c ON c.id=j.comentario_ubb_por
        """;

    @GetMapping("/practicas/{id}/bitacora") public Map<String,Object> listar(@PathVariable long id) {
        acceso.practica(id);
        var jornadas=db.queryForList(JORNADAS+" WHERE j.practica_id=? ORDER BY j.fecha DESC,j.hora_inicio DESC",id);
        return Map.of("jornadas",jornadas,"resumen",resumen(jornadas));
    }

    /** Jornadas de todas las prácticas que la cuenta puede ver; "pendientes" deja solo las que esperan visado. */
    @GetMapping("/bitacora") public Map<String,Object> todas(@RequestParam(defaultValue="false") boolean pendientes) {
        var f=acceso.filtroPracticas();
        var jornadas=db.queryForList(JORNADAS+" WHERE "+f.sql()+(pendientes?" AND j.estado='REGISTRADA'":"")+" ORDER BY j.fecha DESC,j.hora_inicio DESC LIMIT 500",f.args());
        return Map.of("jornadas",jornadas,"resumen",resumen(jornadas));
    }

    @PostMapping("/practicas/{id}/bitacora") @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> registrar(@PathVariable long id,@Valid @RequestBody Jornada j) {
        acceso.exigir("ESTUDIANTE","COORDINADOR");acceso.practica(id);validar(j);
        db.update("INSERT INTO bitacora_jornadas(practica_id,fecha,hora_inicio,hora_fin,curso,tipo,actividades,reflexion) VALUES(?,?,?,?,?,?,?,?)",
            id,j.fecha(),j.horaInicio(),j.horaFin(),j.curso().strip(),j.tipo(),j.actividades().strip(),texto(j.reflexion()));
        return Map.of("mensaje","Jornada registrada. Tu profesor de colegio la revisará y la visará.");
    }

    @PutMapping("/bitacora/{id}") public Map<String,Object> editar(@PathVariable long id,@Valid @RequestBody Jornada j) {
        var actual=editable(id);validar(j);
        // Al corregir una jornada observada, vuelve a quedar pendiente de visado.
        db.update("UPDATE bitacora_jornadas SET fecha=?,hora_inicio=?,hora_fin=?,curso=?,tipo=?,actividades=?,reflexion=?,estado='REGISTRADA',visado_por=NULL,visado_en=NULL WHERE id=?",
            j.fecha(),j.horaInicio(),j.horaFin(),j.curso().strip(),j.tipo(),j.actividades().strip(),texto(j.reflexion()),id);
        return Map.of("mensaje","OBSERVADA".equals(actual.get("estado"))?"Jornada corregida: vuelve a quedar pendiente de visado.":"Jornada actualizada.");
    }

    @DeleteMapping("/bitacora/{id}") public Map<String,Object> eliminar(@PathVariable long id) {
        var actual=editable(id);
        db.update("DELETE FROM bitacora_jornadas WHERE id=?",id);
        actual.remove("id");
        return Map.of("mensaje","Jornada eliminada.","eliminada",actual);
    }

    @PostMapping("/bitacora/{id}/visado") @Transactional
    public Map<String,Object> visar(@PathVariable long id,@Valid @RequestBody Visado v) {
        acceso.exigir("PROFESOR_COLEGIO","COORDINADOR");
        jornada(id);
        String comentario=texto(v.comentario());
        if("OBSERVADA".equals(v.decision()) && comentario.isEmpty())throw Problema.invalido("Escribe qué debe corregir el estudiante en esta jornada.");
        db.update("UPDATE bitacora_jornadas SET estado=?,comentario_colegio=?,visado_por=?,visado_en=CURRENT_TIMESTAMP WHERE id=?",v.decision(),comentario,acceso.id(),id);
        return Map.of("mensaje","VISADA".equals(v.decision())?"Jornada visada.":"Jornada observada: el estudiante verá tu comentario.");
    }

    @PostMapping("/bitacora/{id}/comentario")
    public Map<String,Object> comentar(@PathVariable long id,@Valid @RequestBody Comentario c) {
        acceso.exigir("PROFESOR_UBB","COORDINADOR");
        jornada(id);
        db.update("UPDATE bitacora_jornadas SET comentario_ubb=?,comentario_ubb_por=? WHERE id=?",c.comentario().strip(),acceso.id(),id);
        return Map.of("mensaje","Retroalimentación guardada.");
    }

    /** Horas registradas y visadas, y jornadas por estado. */
    static Map<String,Object> resumen(List<Map<String,Object>> jornadas) {
        long minutos=0,visados=0,pendientes=0,observadas=0;
        for(var j:jornadas) {
            long m=((Number)j.get("minutos")).longValue();minutos+=m;
            switch(String.valueOf(j.get("estado"))) { case "VISADA" -> visados+=m; case "OBSERVADA" -> observadas++; default -> pendientes++; }
        }
        var r=new LinkedHashMap<String,Object>();
        r.put("jornadas",jornadas.size());r.put("horas",Math.round(minutos/6.0)/10.0);r.put("horas_visadas",Math.round(visados/6.0)/10.0);
        r.put("pendientes",pendientes);r.put("observadas",observadas);
        return r;
    }

    private Map<String,Object> jornada(long id) {
        var filas=db.queryForList("SELECT id,practica_id,fecha,hora_inicio,hora_fin,curso,tipo,actividades,reflexion,estado FROM bitacora_jornadas WHERE id=?",id);
        if(filas.isEmpty())throw Problema.noExiste("La jornada no existe o fue eliminada.");
        acceso.practica(((Number)filas.get(0).get("practica_id")).longValue());
        return filas.get(0);
    }
    /** Solo el estudiante (o la coordinación) modifica sus jornadas, y nunca una ya visada. */
    private Map<String,Object> editable(long id) {
        acceso.exigir("ESTUDIANTE","COORDINADOR");
        var j=jornada(id);
        if("VISADA".equals(j.get("estado")))throw Problema.invalido("Esta jornada ya fue visada y no se puede modificar.");
        return j;
    }
    private static void validar(Jornada j) {
        if(!j.horaFin().isAfter(j.horaInicio()))throw Problema.invalido("La hora de término debe ser posterior a la de inicio.");
        if(j.fecha().isAfter(ReglasSeguimiento.hoy()))throw Problema.invalido("No puedes registrar una jornada con fecha futura.");
        if(Duration.between(j.horaInicio(),j.horaFin()).toHours()>12)throw Problema.invalido("Una jornada no puede durar más de 12 horas.");
    }
    private static String texto(String s) { return s==null?"":s.strip(); }
}
