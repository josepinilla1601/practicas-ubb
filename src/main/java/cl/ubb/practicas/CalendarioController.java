package cl.ubb.practicas;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

/**
 * S3-10: calendario del mes con los plazos de los requisitos, las jornadas de bitácora y el inicio y término
 * de cada práctica (según la ficha de inscripción). Cada cuenta ve solo sus prácticas autorizadas.
 */
@RestController
@RequestMapping("/api")
public class CalendarioController {
    private final JdbcTemplate db;
    private final Acceso acceso;
    private final PracticasService servicio;
    public CalendarioController(JdbcTemplate db,Acceso acceso,PracticasService servicio) {this.db=db;this.acceso=acceso;this.servicio=servicio;}

    @GetMapping("/calendario") public Map<String,Object> mes(@RequestParam(required=false) Integer anio,@RequestParam(required=false) Integer mes) {
        LocalDate hoy=ReglasSeguimiento.hoy();
        YearMonth ym;
        try { ym=YearMonth.of(anio==null?hoy.getYear():anio,mes==null?hoy.getMonthValue():mes); }
        catch(Exception ex) { throw Problema.invalido("El mes indicado no es válido."); }
        LocalDate desde=ym.atDay(1),hasta=ym.atEndOfMonth();
        var f=acceso.filtroListado();
        Map<Long,String> estudiantes=new HashMap<>();
        for(var p:servicio.practicas())estudiantes.put(((Number)p.get("id")).longValue(),String.valueOf(p.get("estudiante")));
        List<Map<String,Object>> eventos=new ArrayList<>();
        for(var r:servicio.requisitos(f.sql(),f.args())) {
            var fecha=ReglasSeguimiento.fecha(r.get("fecha_limite"));
            if(fecha==null || fecha.isBefore(desde) || fecha.isAfter(hasta))continue;
            long practica=((Number)r.get("practica_id")).longValue();
            eventos.add(evento(fecha,Boolean.TRUE.equals(r.get("vencido"))?"VENCIDO":"PLAZO",String.valueOf(r.get("nombre")),estudiantes.get(practica),String.valueOf(r.get("estado")),practica));
        }
        var args=new ArrayList<Object>(Arrays.asList(f.args()));args.add(desde);args.add(hasta);
        for(var j:db.queryForList("SELECT j.fecha,j.curso,j.tipo,j.estado,j.practica_id,TIME_FORMAT(j.hora_inicio,'%H:%i') inicio,TIME_FORMAT(j.hora_fin,'%H:%i') fin FROM bitacora_jornadas j JOIN practicas p ON p.id=j.practica_id WHERE "+f.sql()+" AND j.fecha BETWEEN ? AND ?",args.toArray())) {
            long practica=((Number)j.get("practica_id")).longValue();
            eventos.add(evento(ReglasSeguimiento.fecha(j.get("fecha")),"JORNADA","Jornada en "+j.get("curso")+" ("+j.get("inicio")+"–"+j.get("fin")+")",estudiantes.get(practica),String.valueOf(j.get("estado")),practica));
        }
        var args2=new ArrayList<Object>(Arrays.asList(f.args()));args2.add(desde);args2.add(hasta);args2.add(desde);args2.add(hasta);
        for(var x:db.queryForList("SELECT x.practica_id,x.fecha_inicio,x.fecha_termino,x.estado FROM fichas_inscripcion x JOIN practicas p ON p.id=x.practica_id WHERE "+f.sql()+" AND (x.fecha_inicio BETWEEN ? AND ? OR x.fecha_termino BETWEEN ? AND ?)",args2.toArray())) {
            long practica=((Number)x.get("practica_id")).longValue();
            var inicio=ReglasSeguimiento.fecha(x.get("fecha_inicio")); var termino=ReglasSeguimiento.fecha(x.get("fecha_termino"));
            if(!inicio.isBefore(desde) && !inicio.isAfter(hasta))eventos.add(evento(inicio,"INICIO","Inicio de la práctica",estudiantes.get(practica),String.valueOf(x.get("estado")),practica));
            if(!termino.isBefore(desde) && !termino.isAfter(hasta))eventos.add(evento(termino,"TERMINO","Término de la práctica",estudiantes.get(practica),String.valueOf(x.get("estado")),practica));
        }
        eventos.sort(Comparator.comparing(e -> e.get("fecha").toString()));
        return Map.of("anio",ym.getYear(),"mes",ym.getMonthValue(),"hoy",hoy.toString(),"eventos",eventos);
    }
    private static Map<String,Object> evento(LocalDate fecha,String tipo,String titulo,String estudiante,String estado,long practica) {
        var e=new LinkedHashMap<String,Object>();
        e.put("fecha",fecha.toString());e.put("tipo",tipo);e.put("titulo",titulo);e.put("estudiante",estudiante);e.put("estado",estado);e.put("practica_id",practica);
        return e;
    }
}
