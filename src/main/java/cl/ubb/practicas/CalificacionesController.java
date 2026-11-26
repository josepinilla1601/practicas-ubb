package cl.ubb.practicas;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.*;

/**
 * Calificaciones internas de la práctica. Las registra el profesor UBB asignado o la coordinación;
 * el estudiante solo las consulta. El profesor de colegio entrega su evaluación cualitativa por separado.
 */
@RestController
@RequestMapping("/api")
public class CalificacionesController {
    private final JdbcTemplate db;
    private final Acceso acceso;
    public CalificacionesController(JdbcTemplate db,Acceso acceso) {this.db=db;this.acceso=acceso;}
    public record Calificacion(@NotBlank @Size(max=150) String evaluacion,@NotNull BigDecimal nota,
        @NotNull @Min(1) @Max(100) Integer ponderacion,@Size(max=2000) String comentario) { }

    @GetMapping("/practicas/{id}/calificaciones") public Map<String,Object> listar(@PathVariable long id) {
        acceso.exigir("COORDINADOR","PROFESOR_UBB","ESTUDIANTE");acceso.practica(id);
        return resumen(db,id);
    }

    @PostMapping("/practicas/{id}/calificaciones") @ResponseStatus(HttpStatus.CREATED) @Transactional
    public Map<String,Object> registrar(@PathVariable long id,@Valid @RequestBody Calificacion datos) {
        acceso.exigir("COORDINADOR","PROFESOR_UBB");acceso.practica(id);
        insertar(db,id,datos.evaluacion(),datos.nota(),datos.ponderacion(),datos.comentario(),acceso.id());
        var r=new LinkedHashMap<String,Object>(resumen(db,id));
        r.put("mensaje","Nota registrada.");
        return r;
    }

    @DeleteMapping("/calificaciones/{id}") @Transactional
    public Map<String,Object> eliminar(@PathVariable long id) {
        acceso.exigir("COORDINADOR","PROFESOR_UBB");
        var filas=db.queryForList("SELECT practica_id,evaluacion,nota,ponderacion,comentario,autor_id FROM calificaciones WHERE id=?",id);
        if(filas.isEmpty())throw Problema.noExiste("La nota no existe o ya fue eliminada.");
        var c=filas.get(0);
        acceso.practica(((Number)c.get("practica_id")).longValue());
        if(!"COORDINADOR".equals(acceso.rol()) && ((Number)c.get("autor_id")).longValue()!=acceso.id())
            throw new Problema(HttpStatus.FORBIDDEN,"Solo quien registró la nota o la coordinación puede eliminarla.");
        db.update("DELETE FROM calificaciones WHERE id=?",id);
        // Se devuelven los datos para que la interfaz pueda ofrecer "Deshacer".
        return Map.of("mensaje","Nota eliminada.","eliminada",Map.of("evaluacion",c.get("evaluacion"),"nota",c.get("nota"),"ponderacion",c.get("ponderacion"),"comentario",c.get("comentario")));
    }

    /** Registra una nota validando escala, decimales y que la ponderación total no supere 100 %. Debe llamarse en una transacción. */
    static void insertar(JdbcTemplate db,long practica,String evaluacion,BigDecimal nota,int ponderacion,String comentario,long autor) {
        String error=ReglasCalificacion.validarNota(nota);
        if(error!=null)throw Problema.invalido(error);
        db.queryForMap("SELECT id FROM practicas WHERE id=? FOR UPDATE",practica);
        int actual=Optional.ofNullable(db.queryForObject("SELECT SUM(ponderacion) FROM calificaciones WHERE practica_id=?",Integer.class,practica)).orElse(0);
        if(actual+ponderacion>100)
            throw Problema.invalido("Las ponderaciones ya suman "+actual+" %. Con esta evaluación llegarían a "+(actual+ponderacion)+" %, y el total no puede superar 100 %. Puedes usar hasta "+(100-actual)+" %.");
        db.update("INSERT INTO calificaciones(practica_id,evaluacion,nota,ponderacion,comentario,autor_id) VALUES(?,?,?,?,?,?)",
            practica,evaluacion.strip(),nota,ponderacion,comentario==null?"":comentario.strip(),autor);
    }

    /** Notas de una práctica con su promedio ponderado y situación. También lo usan los indicadores y la exportación. */
    static Map<String,Object> resumen(JdbcTemplate db,long practica) {
        var notas=db.queryForList("SELECT c.id,c.evaluacion,c.nota,c.ponderacion,c.comentario,c.creado_en,c.autor_id,u.nombre autor FROM calificaciones c JOIN usuarios u ON u.id=c.autor_id WHERE c.practica_id=? ORDER BY c.id",practica);
        var reglas=notas.stream().map(n -> new ReglasCalificacion.Nota((BigDecimal)n.get("nota"),((Number)n.get("ponderacion")).intValue())).toList();
        var r=new LinkedHashMap<String,Object>();
        r.put("notas",notas);
        r.put("promedio",ReglasCalificacion.promedio(reglas));
        r.put("ponderacion",ReglasCalificacion.ponderacionTotal(reglas));
        r.put("situacion",ReglasCalificacion.situacion(reglas));
        return r;
    }
}
