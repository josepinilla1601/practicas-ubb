package cl.ubb.practicas;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * S3-25: avisos de la campanita. Se calculan en el momento con los datos existentes, según el rol y
 * solo sobre las prácticas autorizadas de la cuenta. Cada aviso indica a qué práctica y pestaña lleva.
 */
@RestController
@RequestMapping("/api")
public class NotificacionesController {
    private final JdbcTemplate db;
    private final Acceso acceso;
    private final PracticasService servicio;
    public NotificacionesController(JdbcTemplate db,Acceso acceso,PracticasService servicio) {this.db=db;this.acceso=acceso;this.servicio=servicio;}

    static final int MAXIMO=30;

    @GetMapping("/notificaciones") public Map<String,Object> listar() {
        // Los avisos solo consideran el período actual: el historial no genera pendientes.
        String rol=acceso.rol();var f=acceso.filtroActual();
        Map<Long,String> nombres=new HashMap<>();
        for(var p:servicio.practicasTodas())nombres.put(((Number)p.get("id")).longValue(),String.valueOf(p.get("estudiante")));
        List<Map<String,Object>> avisos=new ArrayList<>();
        LocalDate hoy=ReglasSeguimiento.hoy();
        switch(rol) {
            case "ESTUDIANTE" -> {
                for(var r:servicio.requisitos(f.sql(),f.args())) {
                    long p=practica(r);String nombre=String.valueOf(r.get("nombre"));String estado=String.valueOf(r.get("estado"));
                    if("OBSERVADO".equals(estado))avisos.add(aviso("danger","Te pidieron una corrección en «"+nombre+"»","Sube una versión corregida.",p,"entregas"));
                    else if(Boolean.TRUE.equals(r.get("vencido")))avisos.add(aviso("danger","«"+nombre+"» está fuera de plazo","Entrégalo lo antes posible.",p,"entregas"));
                    else if("PENDIENTE".equals(estado) && r.get("fecha_limite")!=null) {
                        long dias=ChronoUnit.DAYS.between(hoy,ReglasSeguimiento.fecha(r.get("fecha_limite")));
                        if(dias>=0 && dias<=7)avisos.add(aviso("warn","«"+nombre+"» vence "+(dias==0?"hoy":dias==1?"mañana":"en "+dias+" días"),"Todavía no lo entregas.",p,"entregas"));
                    }
                }
                for(var j:db.queryForList("SELECT j.practica_id,j.fecha FROM bitacora_jornadas j JOIN practicas p ON p.id=j.practica_id WHERE "+f.sql()+" AND j.estado='OBSERVADA'",f.args()))
                    avisos.add(aviso("danger","Observaron tu jornada del "+fecha(j.get("fecha")),"Revisa el comentario y corrígela.",practica(j),"bitacora"));
                for(var p:nombres.keySet()) {
                    String ficha=db.queryForList("SELECT estado FROM fichas_inscripcion WHERE practica_id=?",String.class,p).stream().findFirst().orElse("SIN_FICHA");
                    if("DEVUELTA".equals(ficha))avisos.add(aviso("danger","Te devolvieron la ficha de inscripción","Revisa el comentario, corrígela y envíala de nuevo.",p,"inscripcion"));
                    else if("SIN_FICHA".equals(ficha) || "BORRADOR".equals(ficha))avisos.add(aviso("info","Completa y envía tu ficha de inscripción","Curso, días, horario y fechas en el centro.",p,"inscripcion"));
                }
                for(var x:db.queryForList("SELECT p.id practica_id FROM practicas p JOIN asignaturas a ON a.id=p.asignatura_id WHERE "+f.sql()+
                        " AND a.requiere_centro=TRUE AND p.centro_id IS NULL AND NOT EXISTS(SELECT 1 FROM preferencias_centro pc WHERE pc.practica_id=p.id)",f.args()))
                    avisos.add(aviso("warn","Elige tus centros de práctica de preferencia","Marca hasta tres colegios; la coordinación asigna según los cupos.",practica(x),"inscripcion"));
            }
            case "PROFESOR_UBB","COORDINADOR" -> {
                for(var d:db.queryForList("""
                    SELECT r.practica_id,COUNT(*) n FROM documentos d JOIN requisitos_practica r ON r.id=d.requisito_id JOIN practicas p ON p.id=r.practica_id
                    WHERE"""+" "+f.sql()+" AND d.id=(SELECT MAX(d2.id) FROM documentos d2 WHERE d2.requisito_id=d.requisito_id) AND NOT EXISTS(SELECT 1 FROM revisiones rv WHERE rv.documento_id=d.id) GROUP BY r.practica_id",f.args())) {
                    long n=((Number)d.get("n")).longValue();long p=practica(d);
                    avisos.add(aviso("info",n+(n==1?" entrega por revisar":" entregas por revisar")+" de "+nombres.get(p),"Aprueba o solicita una corrección.",p,"entregas"));
                }
                if("COORDINADOR".equals(rol)) {
                    for(var x:db.queryForList("SELECT x.practica_id FROM fichas_inscripcion x JOIN practicas p ON p.id=x.practica_id WHERE "+f.sql()+" AND x.estado='CONFIRMADA'",f.args()))
                        avisos.add(aviso("warn","Aprueba la ficha de inscripción de "+nombres.get(practica(x)),"El colegio ya la confirmó.",practica(x),"inscripcion"));
                    for(var x:db.queryForList("SELECT p.id practica_id FROM practicas p WHERE "+f.sql()+" AND NOT EXISTS(SELECT 1 FROM requisitos_practica r WHERE r.practica_id=p.id)",f.args()))
                        avisos.add(aviso("warn","La práctica de "+nombres.get(practica(x))+" no tiene requisitos","Aplica la plantilla de su asignatura.",practica(x),"entregas"));
                }
            }
            case "PROFESOR_COLEGIO" -> {
                for(var j:db.queryForList("SELECT j.practica_id,COUNT(*) n FROM bitacora_jornadas j JOIN practicas p ON p.id=j.practica_id WHERE "+f.sql()+" AND j.estado='REGISTRADA' GROUP BY j.practica_id",f.args())) {
                    long n=((Number)j.get("n")).longValue();
                    avisos.add(aviso("info",n+(n==1?" jornada por visar":" jornadas por visar")+" de "+nombres.get(practica(j)),"Visa u observa la bitácora.",practica(j),"bitacora"));
                }
                for(var x:db.queryForList("SELECT x.practica_id FROM fichas_inscripcion x JOIN practicas p ON p.id=x.practica_id WHERE "+f.sql()+" AND x.estado='ENVIADA'",f.args()))
                    avisos.add(aviso("warn","Confirma la ficha de inscripción de "+nombres.get(practica(x)),"Revisa curso, días y horario.",practica(x),"inscripcion"));
                for(var x:db.queryForList("SELECT p.id practica_id FROM practicas p WHERE "+f.sql()+" AND NOT EXISTS(SELECT 1 FROM rubricas_colegio r WHERE r.practica_id=p.id) AND EXISTS(SELECT 1 FROM bitacora_jornadas j WHERE j.practica_id=p.id AND j.estado='VISADA')",f.args()))
                    avisos.add(aviso("info","Falta evaluar con la rúbrica a "+nombres.get(practica(x)),"Rúbrica del Marco para la Buena Enseñanza.",practica(x),"evaluacion"));
            }
            default -> { }
        }
        // Primero lo urgente (rojo), luego lo que conviene hacer pronto (amarillo) y al final lo informativo.
        var orden=Map.of("danger",0,"warn",1,"info",2);
        avisos.sort(Comparator.comparing(a -> orden.getOrDefault(String.valueOf(a.get("tono")),3)));
        int total=avisos.size();
        return Map.of("total",total,"avisos",avisos.subList(0,Math.min(MAXIMO,total)));
    }

    private static long practica(Map<String,Object> fila) { return ((Number)fila.get("practica_id")).longValue(); }
    private static String fecha(Object valor) {
        var d=ReglasSeguimiento.fecha(valor);
        String[] m={"ene","feb","mar","abr","may","jun","jul","ago","sep","oct","nov","dic"};
        return d.getDayOfMonth()+" "+m[d.getMonthValue()-1];
    }
    private static Map<String,Object> aviso(String tono,String texto,String detalle,long practica,String pestana) {
        var a=new LinkedHashMap<String,Object>();
        a.put("tono",tono);a.put("texto",texto);a.put("detalle",detalle);a.put("practica_id",practica);a.put("pestana",pestana);
        return a;
    }
}
