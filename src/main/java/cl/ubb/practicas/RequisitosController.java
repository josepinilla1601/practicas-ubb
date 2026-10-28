package cl.ubb.practicas;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import java.time.LocalDate;
import java.util.*;

@RestController
@Validated
@RequestMapping("/api")
public class RequisitosController {
    private final JdbcTemplate db;
    private final Acceso acceso;
    public RequisitosController(JdbcTemplate db,Acceso acceso) {this.db=db;this.acceso=acceso;}
    public record Plantilla(@NotNull @Positive Long asignaturaId,@Min(2000) @Max(2100) int anio,@Min(1) @Max(2) int semestre,
        @NotBlank @Pattern(regexp="[A-Za-z0-9_-]{1,60}") String codigo,@NotBlank @Size(max=180) String nombre,LocalDate fechaLimite) { }
    public record Plazo(LocalDate fechaLimite) { }

    @GetMapping("/plantillas") public List<Map<String,Object>> listar(@RequestParam @Positive long asignaturaId,
        @RequestParam @Min(2000) @Max(2100) int anio,@RequestParam @Min(1) @Max(2) int semestre) {
        acceso.exigir("COORDINADOR");
        return db.queryForList("SELECT r.* FROM requisitos_plantilla r JOIN periodos pe ON pe.id=r.periodo_id WHERE r.asignatura_id=? AND pe.anio=? AND pe.semestre=? ORDER BY r.id",asignaturaId,anio,semestre);
    }
    @PostMapping("/plantillas") @ResponseStatus(HttpStatus.CREATED) @Transactional
    public Map<String,String> crear(@Valid @RequestBody Plantilla p) {
        acceso.exigir("COORDINADOR");
        if(db.queryForObject("SELECT COUNT(*) FROM asignaturas WHERE id=?",Long.class,p.asignaturaId())==0)throw Problema.noExiste("La asignatura no existe.");
        db.update("INSERT IGNORE INTO periodos(anio,semestre) VALUES(?,?)",p.anio(),p.semestre());
        long periodo=db.queryForObject("SELECT id FROM periodos WHERE anio=? AND semestre=?",Long.class,p.anio(),p.semestre());
        db.update("INSERT INTO requisitos_plantilla(asignatura_id,periodo_id,codigo,nombre,fecha_limite) VALUES(?,?,?,?,?)",p.asignaturaId(),periodo,p.codigo().strip().toUpperCase(Locale.ROOT),p.nombre().strip(),p.fechaLimite());
        return Map.of("mensaje","Requisito agregado a la plantilla.");
    }
    @PutMapping("/plantillas/{id}") public Map<String,String> editar(@PathVariable long id,@Valid @RequestBody Plantilla p) {
        acceso.exigir("COORDINADOR");
        var rows=db.queryForList("SELECT r.id FROM requisitos_plantilla r JOIN periodos pe ON pe.id=r.periodo_id WHERE r.id=? AND r.asignatura_id=? AND pe.anio=? AND pe.semestre=?",id,p.asignaturaId(),p.anio(),p.semestre());
        if(rows.isEmpty())throw Problema.noExiste("No existe el requisito en esta asignatura y período.");
        db.update("UPDATE requisitos_plantilla SET codigo=?,nombre=?,fecha_limite=? WHERE id=?",p.codigo().strip().toUpperCase(Locale.ROOT),p.nombre().strip(),p.fechaLimite(),id);
        return Map.of("mensaje","Plantilla actualizada. Las prácticas existentes conservan su configuración.");
    }
    @PostMapping("/practicas/{id}/requisitos/configurar") @Transactional
    public Map<String,String> configurar(@PathVariable long id) {
        acceso.exigir("COORDINADOR");acceso.practica(id);
        db.queryForMap("SELECT id FROM practicas WHERE id=? FOR UPDATE",id);
        if(db.queryForObject("SELECT COUNT(*) FROM requisitos_practica WHERE practica_id=?",Long.class,id)>0)throw Problema.invalido("La práctica ya tiene requisitos; se conserva su configuración.");
        int n=db.update("INSERT INTO requisitos_practica(practica_id,codigo,nombre,fecha_limite) SELECT p.id,r.codigo,r.nombre,r.fecha_limite FROM practicas p JOIN requisitos_plantilla r ON r.asignatura_id=p.asignatura_id AND r.periodo_id=p.periodo_id WHERE p.id=?",id);
        if(n==0)throw Problema.invalido("Configura primero la plantilla de esta asignatura y período.");
        return Map.of("mensaje","Requisitos inicializados.");
    }
    @PutMapping("/requisitos/{id}/plazo") public Map<String,String> plazo(@PathVariable long id,@Valid @RequestBody Plazo plazo) {
        acceso.exigir("COORDINADOR","PROFESOR_UBB");
        var rows=db.queryForList("SELECT practica_id FROM requisitos_practica WHERE id=?",id);
        if(rows.isEmpty())throw Problema.noExiste("El requisito no existe.");
        acceso.practica(((Number)rows.getFirst().get("practica_id")).longValue());
        db.update("UPDATE requisitos_practica SET fecha_limite=? WHERE id=?",plazo.fechaLimite(),id);
        return Map.of("mensaje","Plazo actualizado. Las fechas de entrega se conservan.");
    }
}
