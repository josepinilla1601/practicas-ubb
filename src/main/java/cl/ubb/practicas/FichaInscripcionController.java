package cl.ubb.practicas;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.*;

/**
 * S3-04 y S3-05: ficha de inscripción de la práctica.
 * Flujo: BORRADOR → (estudiante envía) ENVIADA → (colegio confirma) CONFIRMADA → (coordinación aprueba) APROBADA.
 * El colegio o la coordinación pueden devolverla (DEVUELTA) con un comentario; el estudiante la corrige y la reenvía.
 */
@RestController
@RequestMapping("/api")
public class FichaInscripcionController {
    private final JdbcTemplate db;
    private final Acceso acceso;
    public FichaInscripcionController(JdbcTemplate db,Acceso acceso) {this.db=db;this.acceso=acceso;}

    public record Ficha(@NotBlank @Size(max=60) String curso,@NotBlank @Size(max=60) String nivel,@NotBlank @Size(max=120) String dias,
        @NotBlank @Size(max=120) String horario,@NotNull LocalDate fechaInicio,@NotNull LocalDate fechaTermino,
        @NotNull @Min(1) @Max(45) Integer horasSemanales,@Size(max=1000) String observaciones) { }
    public record Decision(@NotBlank @Pattern(regexp="CONFIRMAR|APROBAR|DEVOLVER") String accion,@Size(max=1000) String comentario) { }

    @GetMapping("/practicas/{id}/ficha") public Map<String,Object> ver(@PathVariable long id) {
        acceso.practica(id);
        var filas=db.queryForList("""
            SELECT f.*,c.nombre confirmada_por_nombre,a.nombre aprobada_por_nombre FROM fichas_inscripcion f
            LEFT JOIN usuarios c ON c.id=f.confirmada_por LEFT JOIN usuarios a ON a.id=f.aprobada_por WHERE f.practica_id=?""",id);
        return filas.isEmpty()?Map.of("estado","SIN_FICHA"):filas.get(0);
    }

    @PutMapping("/practicas/{id}/ficha") @Transactional
    public Map<String,Object> guardar(@PathVariable long id,@Valid @RequestBody Ficha f) {
        acceso.exigir("ESTUDIANTE","COORDINADOR");acceso.practica(id);
        if(f.fechaTermino().isBefore(f.fechaInicio()))throw Problema.invalido("La fecha de término debe ser igual o posterior a la de inicio.");
        String estado=estado(id);
        if(!List.of("SIN_FICHA","BORRADOR","DEVUELTA").contains(estado))
            throw Problema.invalido("La ficha ya fue enviada y está en revisión. Si necesitas cambiarla, pide que te la devuelvan.");
        if("SIN_FICHA".equals(estado))
            db.update("INSERT INTO fichas_inscripcion(practica_id,curso,nivel,dias,horario,fecha_inicio,fecha_termino,horas_semanales,observaciones) VALUES(?,?,?,?,?,?,?,?,?)",
                id,f.curso().strip(),f.nivel().strip(),f.dias().strip(),f.horario().strip(),f.fechaInicio(),f.fechaTermino(),f.horasSemanales(),texto(f.observaciones()));
        else db.update("UPDATE fichas_inscripcion SET curso=?,nivel=?,dias=?,horario=?,fecha_inicio=?,fecha_termino=?,horas_semanales=?,observaciones=? WHERE practica_id=?",
                f.curso().strip(),f.nivel().strip(),f.dias().strip(),f.horario().strip(),f.fechaInicio(),f.fechaTermino(),f.horasSemanales(),texto(f.observaciones()),id);
        return Map.of("mensaje","Ficha guardada como borrador. Cuando esté completa, envíala.");
    }

    @PostMapping("/practicas/{id}/ficha/enviar") @Transactional
    public Map<String,Object> enviar(@PathVariable long id) {
        acceso.exigir("ESTUDIANTE","COORDINADOR");acceso.practica(id);
        String estado=estado(id);
        if("SIN_FICHA".equals(estado))throw Problema.invalido("Primero completa y guarda la ficha.");
        if(!List.of("BORRADOR","DEVUELTA").contains(estado))throw Problema.invalido("La ficha ya fue enviada.");
        boolean conColegio=db.queryForObject("SELECT profesor_colegio_id IS NOT NULL FROM practicas WHERE id=?",Boolean.class,id);
        // Si la práctica no tiene profesor de colegio (por ejemplo, actividad universitaria), pasa directo a la coordinación.
        db.update("UPDATE fichas_inscripcion SET estado=?,enviada_en=CURRENT_TIMESTAMP,comentario='' WHERE practica_id=?",conColegio?"ENVIADA":"CONFIRMADA",id);
        return Map.of("mensaje",conColegio?"Ficha enviada a tu profesor de colegio para que la confirme.":"Ficha enviada a la coordinación para su aprobación.");
    }

    @PostMapping("/practicas/{id}/ficha/decision") @Transactional
    public Map<String,Object> decidir(@PathVariable long id,@Valid @RequestBody Decision d) {
        acceso.practica(id);
        String estado=estado(id),rol=acceso.rol(),comentario=texto(d.comentario());
        switch(d.accion()) {
            case "CONFIRMAR" -> {
                if(!List.of("PROFESOR_COLEGIO","COORDINADOR").contains(rol))throw new Problema(HttpStatus.FORBIDDEN,"Solo el profesor de colegio confirma la ficha.");
                if(!"ENVIADA".equals(estado))throw Problema.invalido("La ficha no está esperando la confirmación del colegio.");
                db.update("UPDATE fichas_inscripcion SET estado='CONFIRMADA',confirmada_por=?,confirmada_en=CURRENT_TIMESTAMP,comentario=? WHERE practica_id=?",acceso.id(),comentario,id);
                return Map.of("mensaje","Ficha confirmada. Ahora la revisará la coordinación.");
            }
            case "APROBAR" -> {
                if(!"COORDINADOR".equals(rol))throw new Problema(HttpStatus.FORBIDDEN,"Solo la coordinación aprueba la ficha.");
                if(!"CONFIRMADA".equals(estado))throw Problema.invalido("La ficha debe estar confirmada por el colegio antes de aprobarla.");
                db.update("UPDATE fichas_inscripcion SET estado='APROBADA',aprobada_por=?,aprobada_en=CURRENT_TIMESTAMP,comentario=? WHERE practica_id=?",acceso.id(),comentario,id);
                return Map.of("mensaje","Ficha aprobada. La práctica queda inscrita.");
            }
            default -> {
                boolean puede=("PROFESOR_COLEGIO".equals(rol) && "ENVIADA".equals(estado)) || ("COORDINADOR".equals(rol) && List.of("ENVIADA","CONFIRMADA").contains(estado));
                if(!puede)throw new Problema(HttpStatus.FORBIDDEN,"No puedes devolver la ficha en su estado actual.");
                if(comentario.isEmpty())throw Problema.invalido("Escribe qué debe corregir el estudiante.");
                db.update("UPDATE fichas_inscripcion SET estado='DEVUELTA',comentario=?,confirmada_por=NULL,confirmada_en=NULL WHERE practica_id=?",comentario,id);
                return Map.of("mensaje","Ficha devuelta al estudiante con tu comentario.");
            }
        }
    }

    private String estado(long practica) {
        return db.queryForList("SELECT estado FROM fichas_inscripcion WHERE practica_id=? FOR UPDATE",String.class,practica).stream().findFirst().orElse("SIN_FICHA");
    }
    private static String texto(String s) { return s==null?"":s.strip(); }
}
