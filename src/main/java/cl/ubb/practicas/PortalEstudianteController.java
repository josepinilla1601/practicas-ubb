package cl.ubb.practicas;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/** S2-04: panel personal del estudiante. Solo muestra las prácticas vinculadas a su propia cuenta. */
@RestController
@RequestMapping("/api")
public class PortalEstudianteController {
    private final JdbcTemplate db;
    private final Acceso acceso;
    private final PracticasService servicio;
    public PortalEstudianteController(JdbcTemplate db,Acceso acceso,PracticasService servicio) {this.db=db;this.acceso=acceso;this.servicio=servicio;}

    @GetMapping("/mi-panel") public Map<String,Object> panel() {
        acceso.exigir("ESTUDIANTE");
        Object estudianteId=acceso.actual().get("estudiante_id");
        var estudiante=db.queryForMap("SELECT id,identificador,nombres,apellidos,correo FROM estudiantes WHERE id=?",estudianteId);
        List<Map<String,Object>> practicas=new ArrayList<>(),plazos=new ArrayList<>();
        // El filtro de Acceso limita la consulta a las prácticas del propio estudiante.
        for(var p:servicio.practicas()) {
            long id=((Number)p.get("id")).longValue();
            @SuppressWarnings("unchecked") var requisitos=(List<Map<String,Object>>)servicio.seguimiento(id).get("requisitos");
            var item=new LinkedHashMap<String,Object>(p);item.put("requisitos",requisitos);practicas.add(item);
            for(var r:requisitos) {
                if(r.get("fecha_limite")==null || !ReglasSeguimiento.pendiente(String.valueOf(r.get("estado"))))continue;
                var plazo=new LinkedHashMap<String,Object>(r);plazo.put("practica_id",id);plazo.put("asignatura",p.get("asignatura"));plazos.add(plazo);
            }
        }
        plazos.sort(Comparator.comparing(r -> r.get("fecha_limite").toString()));
        return Map.of("estudiante",estudiante,"practicas",practicas,"plazos",plazos);
    }
}
