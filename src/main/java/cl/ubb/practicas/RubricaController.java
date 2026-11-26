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
 * S3-06 y S3-07: el profesor de colegio evalúa el desempeño con la rúbrica del Marco para la Buena Enseñanza;
 * el profesor UBB puede usar la nota sugerida como una calificación interna.
 */
@RestController
@RequestMapping("/api")
public class RubricaController {
    private final JdbcTemplate db;
    private final Acceso acceso;
    public RubricaController(JdbcTemplate db,Acceso acceso) {this.db=db;this.acceso=acceso;}
    public record Evaluacion(@NotNull Map<String,Integer> niveles,@Size(max=2000) String comentario) { }
    public record UsarNota(@NotNull @Min(1) @Max(100) Integer ponderacion) { }

    /** Criterios, dominios y niveles de la rúbrica, para dibujarla en la interfaz. */
    @GetMapping("/rubrica") public Map<String,Object> rubrica() {
        acceso.actual();
        return Map.of("dominios",ReglasRubrica.DOMINIOS,"criterios",ReglasRubrica.CRITERIOS,"niveles",ReglasRubrica.NIVELES);
    }

    @GetMapping("/practicas/{id}/rubricas") public List<Map<String,Object>> listar(@PathVariable long id) {
        acceso.practica(id);
        var lista=db.queryForList("SELECT r.id,r.niveles,r.puntaje,r.nota_sugerida,r.comentario,r.creado_en,u.nombre autor FROM rubricas_colegio r JOIN usuarios u ON u.id=r.autor_id WHERE r.practica_id=? ORDER BY r.id DESC",id);
        for(var r:lista) {
            var niveles=ReglasRubrica.leer(String.valueOf(r.get("niveles")));
            r.put("niveles",niveles);r.put("dominios",ReglasRubrica.porDominio(niveles));
        }
        return lista;
    }

    @PostMapping("/practicas/{id}/rubricas") @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> evaluar(@PathVariable long id,@Valid @RequestBody Evaluacion e) {
        acceso.exigir("PROFESOR_COLEGIO","COORDINADOR");acceso.practica(id);
        String error=ReglasRubrica.validar(e.niveles());
        if(error!=null)throw Problema.invalido(error);
        BigDecimal nota=ReglasRubrica.notaSugerida(e.niveles());
        db.update("INSERT INTO rubricas_colegio(practica_id,autor_id,niveles,puntaje,nota_sugerida,comentario) VALUES(?,?,?,?,?,?)",
            id,acceso.id(),ReglasRubrica.serializar(e.niveles()),ReglasRubrica.puntaje(e.niveles()),nota,e.comentario()==null?"":e.comentario().strip());
        return Map.of("mensaje","Rúbrica registrada. Nota sugerida: "+nota.toPlainString().replace('.',',')+".","notaSugerida",nota);
    }

    /** El profesor UBB (o la coordinación) usa la nota sugerida de una rúbrica como calificación interna. */
    @PostMapping("/rubricas/{id}/usar-como-nota") @Transactional
    public Map<String,Object> usarComoNota(@PathVariable long id,@Valid @RequestBody UsarNota u) {
        acceso.exigir("PROFESOR_UBB","COORDINADOR");
        var filas=db.queryForList("SELECT practica_id,nota_sugerida,comentario FROM rubricas_colegio WHERE id=?",id);
        if(filas.isEmpty())throw Problema.noExiste("La rúbrica no existe.");
        long practica=((Number)filas.get(0).get("practica_id")).longValue();
        acceso.practica(practica);
        BigDecimal nota=(BigDecimal)filas.get(0).get("nota_sugerida");
        CalificacionesController.insertar(db,practica,"Desempeño en el centro (rúbrica MBE del profesor colaborador)",nota,u.ponderacion(),"Nota sugerida por la rúbrica del profesor de colegio.",acceso.id());
        return Map.of("mensaje","Nota "+nota.toPlainString().replace('.',',')+" agregada a las calificaciones internas.");
    }
}
