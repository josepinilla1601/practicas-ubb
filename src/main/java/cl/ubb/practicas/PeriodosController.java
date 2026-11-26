package cl.ubb.practicas;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/**
 * Períodos (año y semestre). Solo uno es el ACTUAL: genera plazos, avisos y es el que ven estudiantes y docentes.
 * Los demás son HISTORICOS: registro de la carrera (por ejemplo, prácticas en papel de años anteriores),
 * visibles y editables solo por la coordinación.
 */
@RestController
@RequestMapping("/api/periodos")
public class PeriodosController {
    private final JdbcTemplate db;
    private final Acceso acceso;
    public PeriodosController(JdbcTemplate db,Acceso acceso) { this.db=db;this.acceso=acceso; }
    public record Periodo(@NotNull @Min(2000) @Max(2100) Integer anio,@NotNull @Min(1) @Max(2) Integer semestre) { }

    @GetMapping public List<Map<String,Object>> listar() {
        acceso.exigir("COORDINADOR");
        return db.queryForList("""
            SELECT pe.id,pe.anio,pe.semestre,pe.estado,
              (SELECT COUNT(*) FROM practicas p WHERE p.periodo_id=pe.id) practicas
            FROM periodos pe ORDER BY pe.anio DESC,pe.semestre DESC""");
    }

    @PostMapping @ResponseStatus(HttpStatus.CREATED) public Map<String,Object> crear(@Valid @RequestBody Periodo datos) {
        acceso.exigir("COORDINADOR");
        if(db.queryForObject("SELECT COUNT(*) FROM periodos WHERE anio=? AND semestre=?",Long.class,datos.anio(),datos.semestre())>0)
            throw new Problema(HttpStatus.CONFLICT,"El período "+datos.anio()+"-"+datos.semestre()+" ya existe.");
        long id=acceso.asegurarPeriodo(datos.anio(),datos.semestre());
        String estado=db.queryForObject("SELECT estado FROM periodos WHERE id=?",String.class,id);
        return Map.of("id",id,"estado",estado,"mensaje","ACTUAL".equals(estado)?"Período creado como período actual."
            :"Período creado como historial. Puedes registrar en él prácticas de años anteriores.");
    }

    /** Cambia el período actual: el anterior pasa a historial (sus prácticas dejan de generar plazos y avisos). */
    @PutMapping("/{id}/actual") @Transactional public Map<String,Object> marcarActual(@PathVariable long id) {
        acceso.exigir("COORDINADOR");
        if(db.queryForObject("SELECT COUNT(*) FROM periodos WHERE id=?",Long.class,id)==0)throw Problema.noExiste("El período no existe.");
        db.update("UPDATE periodos SET estado='HISTORICO' WHERE estado='ACTUAL' AND id<>?",id);
        db.update("UPDATE periodos SET estado='ACTUAL' WHERE id=?",id);
        var p=db.queryForMap("SELECT anio,semestre FROM periodos WHERE id=?",id);
        return Map.of("mensaje","El período actual ahora es "+p.get("anio")+"-"+p.get("semestre")+".");
    }
}
