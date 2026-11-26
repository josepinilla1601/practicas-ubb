package cl.ubb.practicas;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/**
 * Asignación de centros por cupos.
 * - La coordinación define cuántos cupos ofrece cada centro en un período.
 * - El estudiante cuya práctica requiere centro marca hasta tres centros, en orden de preferencia.
 * - La coordinación asigna a mano o automáticamente: por orden de llegada de las preferencias,
 *   cada estudiante recibe su primera opción con cupo libre.
 */
@RestController
@RequestMapping("/api")
public class CuposController {
    private final JdbcTemplate db;
    private final Acceso acceso;
    public CuposController(JdbcTemplate db,Acceso acceso) { this.db=db;this.acceso=acceso; }

    public record Cupos(@NotNull @Positive Long centroId,@NotNull @Min(2000) @Max(2100) Integer anio,@NotNull @Min(1) @Max(2) Integer semestre,@NotNull @Min(0) @Max(200) Integer cupos) { }
    public record Preferencias(@NotNull @Size(min=1,max=3) List<@NotNull @Positive Long> centros) { }
    public record Asignacion(@NotNull @Positive Long centroId) { }
    public record Periodo(@NotNull @Min(2000) @Max(2100) Integer anio,@NotNull @Min(1) @Max(2) Integer semestre) { }

    /** Centros con cupos en un período: cuántos ofrece, cuántos ya están ocupados y cuántos quedan. */
    private List<Map<String,Object>> centrosConCupos(long periodo) {
        return db.queryForList("""
            SELECT c.id,c.nombre,c.comuna,k.cupos,
              (SELECT COUNT(*) FROM practicas p WHERE p.centro_id=c.id AND p.periodo_id=k.periodo_id) ocupados
            FROM cupos_centro k JOIN centros c ON c.id=k.centro_id WHERE k.periodo_id=? ORDER BY c.nombre""",periodo)
            .stream().peek(c -> c.put("libres",Math.max(0,((Number)c.get("cupos")).intValue()-((Number)c.get("ocupados")).intValue()))).toList();
    }
    private Optional<Long> periodo(int anio,int semestre) {
        return db.queryForList("SELECT id FROM periodos WHERE anio=? AND semestre=?",Long.class,anio,semestre).stream().findFirst();
    }
    private Map<String,Object> practica(long id) {
        return db.queryForMap("SELECT p.id,p.centro_id,p.periodo_id,a.requiere_centro,pe.anio,pe.semestre FROM practicas p JOIN asignaturas a ON a.id=p.asignatura_id JOIN periodos pe ON pe.id=p.periodo_id WHERE p.id=?",id);
    }
    private static boolean requiereCentro(Map<String,Object> p) {
        Object v=p.get("requiere_centro");return Boolean.TRUE.equals(v) || "1".equals(String.valueOf(v));
    }

    // ---------- Estudiante: preferencias ----------

    @GetMapping("/practicas/{id}/preferencias") public Map<String,Object> verPreferencias(@PathVariable long id) {
        acceso.practica(id);
        var p=practica(id);
        var r=new LinkedHashMap<String,Object>();
        r.put("requiereCentro",requiereCentro(p));
        r.put("centroAsignado",p.get("centro_id")==null?null:db.queryForObject("SELECT nombre FROM centros WHERE id=?",String.class,p.get("centro_id")));
        r.put("preferencias",db.queryForList("SELECT f.prioridad,f.centro_id,c.nombre,c.comuna FROM preferencias_centro f JOIN centros c ON c.id=f.centro_id WHERE f.practica_id=? ORDER BY f.prioridad",id));
        r.put("centros",centrosConCupos(((Number)p.get("periodo_id")).longValue()).stream().filter(c -> ((Number)c.get("cupos")).intValue()>0).toList());
        return r;
    }

    @PutMapping("/practicas/{id}/preferencias") @Transactional public Map<String,Object> guardarPreferencias(@PathVariable long id,@Valid @RequestBody Preferencias datos) {
        acceso.exigir("ESTUDIANTE","COORDINADOR");acceso.practica(id);
        var p=practica(id);
        if(!requiereCentro(p))throw Problema.invalido("Esta práctica no se realiza en un centro.");
        if(p.get("centro_id")!=null)throw Problema.invalido("Ya tienes un centro asignado; las preferencias ya no se pueden cambiar.");
        if(new HashSet<>(datos.centros()).size()!=datos.centros().size())throw Problema.invalido("No repitas un centro en tus preferencias.");
        long periodo=((Number)p.get("periodo_id")).longValue();
        for(long c:datos.centros())
            if(db.queryForObject("SELECT COUNT(*) FROM cupos_centro WHERE centro_id=? AND periodo_id=? AND cupos>0",Long.class,c,periodo)==0)
                throw Problema.invalido("Uno de los centros elegidos no ofrece cupos en este período.");
        db.update("DELETE FROM preferencias_centro WHERE practica_id=?",id);
        for(int i=0;i<datos.centros().size();i++)db.update("INSERT INTO preferencias_centro(practica_id,prioridad,centro_id) VALUES(?,?,?)",id,i+1,datos.centros().get(i));
        return Map.of("mensaje","Preferencias guardadas. La coordinación asignará tu centro según los cupos disponibles.");
    }

    // ---------- Coordinación: cupos y asignación ----------

    /** Panel de asignación de un período: centros con sus cupos y prácticas que esperan centro, con sus preferencias. */
    @GetMapping("/asignacion") public Map<String,Object> panel(@RequestParam int anio,@RequestParam int semestre) {
        acceso.exigir("COORDINADOR");
        var r=new LinkedHashMap<String,Object>();
        var periodo=periodo(anio,semestre);
        r.put("centros",periodo.map(this::centrosConCupos).orElse(List.of()));
        r.put("todosLosCentros",db.queryForList("SELECT id,nombre,comuna FROM centros ORDER BY nombre"));
        List<Map<String,Object>> pendientes=periodo.isEmpty()?List.of():db.queryForList("""
            SELECT p.id,CONCAT(e.nombres,' ',e.apellidos) estudiante,a.nombre asignatura,
              (SELECT MIN(f.creado_en) FROM preferencias_centro f WHERE f.practica_id=p.id) preferencias_en
            FROM practicas p JOIN estudiantes e ON e.id=p.estudiante_id JOIN asignaturas a ON a.id=p.asignatura_id
            WHERE p.periodo_id=? AND p.centro_id IS NULL AND a.requiere_centro=TRUE
            ORDER BY preferencias_en IS NULL,preferencias_en,estudiante""",periodo.get());
        for(var p:pendientes)
            p.put("preferencias",db.queryForList("SELECT f.prioridad,f.centro_id,c.nombre FROM preferencias_centro f JOIN centros c ON c.id=f.centro_id WHERE f.practica_id=? ORDER BY f.prioridad",p.get("id")));
        r.put("pendientes",pendientes);
        return r;
    }

    @PutMapping("/asignacion/cupos") public Map<String,Object> definirCupos(@Valid @RequestBody Cupos datos) {
        acceso.exigir("COORDINADOR");
        if(db.queryForObject("SELECT COUNT(*) FROM centros WHERE id=?",Long.class,datos.centroId())==0)throw Problema.noExiste("El centro no existe.");
        db.update("INSERT IGNORE INTO periodos(anio,semestre) VALUES(?,?)",datos.anio(),datos.semestre());
        long periodo=periodo(datos.anio(),datos.semestre()).orElseThrow();
        long ocupados=db.queryForObject("SELECT COUNT(*) FROM practicas WHERE centro_id=? AND periodo_id=?",Long.class,datos.centroId(),periodo);
        if(datos.cupos()<ocupados)throw Problema.invalido("Este centro ya tiene "+ocupados+" practicante(s) asignado(s) en el período: los cupos no pueden ser menos.");
        db.update("INSERT INTO cupos_centro(centro_id,periodo_id,cupos) VALUES(?,?,?) ON DUPLICATE KEY UPDATE cupos=VALUES(cupos)",datos.centroId(),periodo,datos.cupos());
        return Map.of("mensaje","Cupos guardados.");
    }

    @PostMapping("/practicas/{id}/centro") @Transactional public Map<String,Object> asignar(@PathVariable long id,@Valid @RequestBody Asignacion datos) {
        acceso.exigir("COORDINADOR");acceso.practica(id);
        var p=practica(id);
        if(p.get("centro_id")!=null)throw Problema.invalido("Esta práctica ya tiene un centro asignado.");
        long periodo=((Number)p.get("periodo_id")).longValue();
        // Bloquea la fila de cupos para que dos asignaciones simultáneas no superen el límite.
        var cupos=db.queryForList("SELECT cupos FROM cupos_centro WHERE centro_id=? AND periodo_id=? FOR UPDATE",Integer.class,datos.centroId(),periodo);
        if(cupos.isEmpty())throw Problema.invalido("Ese centro no tiene cupos definidos para este período.");
        long ocupados=db.queryForObject("SELECT COUNT(*) FROM practicas WHERE centro_id=? AND periodo_id=?",Long.class,datos.centroId(),periodo);
        if(ocupados>=cupos.get(0))throw new Problema(HttpStatus.CONFLICT,"Ese centro ya no tiene cupos libres en este período.");
        db.update("UPDATE practicas SET centro_id=? WHERE id=? AND centro_id IS NULL",datos.centroId(),id);
        return Map.of("mensaje","Centro asignado.","centro",db.queryForObject("SELECT nombre FROM centros WHERE id=?",String.class,datos.centroId()));
    }

    /** Deshace una asignación (por ejemplo, si se asignó por error). La práctica vuelve a esperar centro. */
    @DeleteMapping("/practicas/{id}/centro") public Map<String,Object> quitar(@PathVariable long id) {
        acceso.exigir("COORDINADOR");acceso.practica(id);
        db.update("UPDATE practicas SET centro_id=NULL WHERE id=?",id);
        return Map.of("mensaje","Asignación deshecha.");
    }

    /** Asigna a cada estudiante su primera preferencia con cupo libre, por orden de llegada de sus preferencias. */
    @PostMapping("/asignacion/automatica") @Transactional public Map<String,Object> automatica(@Valid @RequestBody Periodo datos) {
        acceso.exigir("COORDINADOR");
        var periodo=periodo(datos.anio(),datos.semestre()).orElseThrow(() -> Problema.noExiste("No hay prácticas en ese período."));
        Map<Long,Integer> libres=new HashMap<>();
        for(var c:db.queryForList("SELECT centro_id,cupos FROM cupos_centro WHERE periodo_id=? FOR UPDATE",periodo)) {
            long centro=((Number)c.get("centro_id")).longValue();
            long ocupados=db.queryForObject("SELECT COUNT(*) FROM practicas WHERE centro_id=? AND periodo_id=?",Long.class,centro,periodo);
            libres.put(centro,(int)Math.max(0,((Number)c.get("cupos")).intValue()-ocupados));
        }
        int asignadas=0,sinCupo=0;
        for(var p:db.queryForList("""
            SELECT p.id FROM practicas p JOIN asignaturas a ON a.id=p.asignatura_id
            WHERE p.periodo_id=? AND p.centro_id IS NULL AND a.requiere_centro=TRUE
              AND EXISTS(SELECT 1 FROM preferencias_centro f WHERE f.practica_id=p.id)
            ORDER BY (SELECT MIN(f.creado_en) FROM preferencias_centro f WHERE f.practica_id=p.id),p.id""",periodo)) {
            long id=((Number)p.get("id")).longValue();
            var elegido=db.queryForList("SELECT centro_id FROM preferencias_centro WHERE practica_id=? ORDER BY prioridad",Long.class,id)
                .stream().filter(c -> libres.getOrDefault(c,0)>0).findFirst();
            if(elegido.isEmpty()){sinCupo++;continue;}
            db.update("UPDATE practicas SET centro_id=? WHERE id=?",elegido.get(),id);
            libres.merge(elegido.get(),-1,Integer::sum);asignadas++;
        }
        long sinPreferencias=db.queryForObject("""
            SELECT COUNT(*) FROM practicas p JOIN asignaturas a ON a.id=p.asignatura_id
            WHERE p.periodo_id=? AND p.centro_id IS NULL AND a.requiere_centro=TRUE
              AND NOT EXISTS(SELECT 1 FROM preferencias_centro f WHERE f.practica_id=p.id)""",Long.class,periodo);
        return Map.of("asignadas",asignadas,"sinCupo",sinCupo,"sinPreferencias",sinPreferencias);
    }
}
