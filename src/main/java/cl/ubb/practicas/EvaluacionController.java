package cl.ubb.practicas;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/** S2-14: el profesor de colegio registra observaciones del practicante asignado; el profesor UBB las consulta. */
@RestController
@RequestMapping("/api")
public class EvaluacionController {
    private final JdbcTemplate db;
    private final Acceso acceso;
    public EvaluacionController(JdbcTemplate db,Acceso acceso) {this.db=db;this.acceso=acceso;}
    public record Evaluacion(@NotBlank @Size(max=3000) String fortalezas,@NotBlank @Size(max=3000) String aspectosMejora,@Size(max=3000) String recomendaciones) { }

    @GetMapping("/practicas/{id}/evaluaciones") public List<Map<String,Object>> listar(@PathVariable long id) {
        acceso.exigir("COORDINADOR","PROFESOR_UBB","PROFESOR_COLEGIO");acceso.practica(id);
        return db.queryForList("SELECT ev.id,ev.fortalezas,ev.aspectos_mejora,ev.recomendaciones,ev.creado_en,u.nombre autor FROM evaluaciones_colegio ev JOIN usuarios u ON u.id=ev.autor_id WHERE ev.practica_id=? ORDER BY ev.id DESC",id);
    }
    @PostMapping("/practicas/{id}/evaluaciones") @ResponseStatus(HttpStatus.CREATED)
    public Map<String,String> registrar(@PathVariable long id,@Valid @RequestBody Evaluacion datos) {
        acceso.exigir("PROFESOR_COLEGIO");acceso.practica(id);
        db.update("INSERT INTO evaluaciones_colegio(practica_id,autor_id,fortalezas,aspectos_mejora,recomendaciones) VALUES(?,?,?,?,?)",
            id,acceso.id(),datos.fortalezas().strip(),datos.aspectosMejora().strip(),datos.recomendaciones()==null?"":datos.recomendaciones().strip());
        return Map.of("mensaje","Evaluación registrada. Las anteriores se conservan.");
    }
}
