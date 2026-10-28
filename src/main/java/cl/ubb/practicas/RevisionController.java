package cl.ubb.practicas;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/** S2-11: el profesor UBB o la coordinación aprueba una entrega o solicita una corrección con comentario. */
@RestController
@RequestMapping("/api")
public class RevisionController {
    private final JdbcTemplate db;
    private final Acceso acceso;
    public RevisionController(JdbcTemplate db,Acceso acceso) {this.db=db;this.acceso=acceso;}
    public record Revision(@NotBlank @Pattern(regexp="APROBADO|OBSERVADO") String decision,@Size(max=2000) String comentario) { }

    @PostMapping("/documentos/{id}/revision") @ResponseStatus(HttpStatus.CREATED) @Transactional
    public Map<String,String> revisar(@PathVariable long id,@Valid @RequestBody Revision datos) {
        acceso.exigir("COORDINADOR","PROFESOR_UBB");
        var rows=db.queryForList("SELECT r.practica_id,d.requisito_id FROM documentos d JOIN requisitos_practica r ON r.id=d.requisito_id WHERE d.id=?",id);
        if(rows.isEmpty())throw Problema.noExiste("El documento no existe.");
        acceso.practica(((Number)rows.get(0).get("practica_id")).longValue());
        long requisito=((Number)rows.get(0).get("requisito_id")).longValue();
        db.queryForList("SELECT id FROM requisitos_practica WHERE id=? FOR UPDATE",requisito);
        Long ultima=db.queryForObject("SELECT MAX(id) FROM documentos WHERE requisito_id=?",Long.class,requisito);
        if(ultima==null || ultima!=id)throw Problema.invalido("Solo se revisa la versión más reciente del requisito.");
        if(db.queryForObject("SELECT COUNT(*) FROM revisiones WHERE documento_id=?",Long.class,id)>0)throw Problema.invalido("Esta versión ya fue revisada.");
        String comentario=datos.comentario()==null?"":datos.comentario().strip();
        if("OBSERVADO".equals(datos.decision()) && comentario.isEmpty())throw Problema.invalido("Escribe la corrección que debe realizar el estudiante.");
        db.update("INSERT INTO revisiones(documento_id,revisor_id,decision,comentario) VALUES(?,?,?,?)",id,acceso.id(),datos.decision(),comentario);
        return Map.of("mensaje","APROBADO".equals(datos.decision())?"Entrega aprobada.":"Corrección solicitada al estudiante.");
    }
}
