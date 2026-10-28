package cl.ubb.practicas;

import org.springframework.web.bind.annotation.*;
import java.util.*;

/** S2-15: indicadores de avance, revisión y atraso de las prácticas autorizadas. */
@RestController
@RequestMapping("/api")
public class IndicadoresController {
    private final Acceso acceso;
    private final PracticasService servicio;
    public IndicadoresController(Acceso acceso,PracticasService servicio) {this.acceso=acceso;this.servicio=servicio;}
    private static final List<String> ESTADOS=List.of("PENDIENTE","EN_REVISION","OBSERVADO","APROBADO");

    @GetMapping("/indicadores") public Map<String,Object> indicadores() {
        acceso.exigir("COORDINADOR","PROFESOR_UBB");
        var f=acceso.filtroPracticas();
        Map<Long,Map<String,Object>> porPractica=new LinkedHashMap<>();
        for(var p:servicio.practicas()) {
            var fila=new LinkedHashMap<String,Object>();
            for(String k:List.of("id","estudiante","identificador","asignatura","anio","semestre"))fila.put(k,p.get(k));
            fila.put("requisitos",0L);ESTADOS.forEach(e -> fila.put(e.toLowerCase(Locale.ROOT),0L));fila.put("vencidos",0L);
            porPractica.put(((Number)p.get("id")).longValue(),fila);
        }
        var totales=new LinkedHashMap<String,Object>();
        totales.put("practicas",(long)porPractica.size());totales.put("requisitos",0L);
        ESTADOS.forEach(e -> totales.put(e.toLowerCase(Locale.ROOT),0L));totales.put("vencidos",0L);
        for(var r:servicio.requisitos(f.sql(),f.args())) {
            var fila=porPractica.get(((Number)r.get("practica_id")).longValue());if(fila==null)continue;
            String clave=r.get("estado").toString().toLowerCase(Locale.ROOT);
            sumar(fila,"requisitos");sumar(fila,clave);sumar(totales,"requisitos");sumar(totales,clave);
            if(Boolean.TRUE.equals(r.get("vencido"))){sumar(fila,"vencidos");sumar(totales,"vencidos");}
        }
        long sinConfigurar=0,completas=0;
        for(var fila:porPractica.values()) {
            long requisitos=(Long)fila.get("requisitos"),aprobados=(Long)fila.get("aprobado");
            // Una práctica sin requisitos no se considera completa.
            String situacion=requisitos==0?"SIN_CONFIGURAR":aprobados==requisitos?"COMPLETA":(Long)fila.get("vencidos")>0?"CON_ATRASO":"EN_CURSO";
            fila.put("situacion",situacion);
            if(requisitos==0)sinConfigurar++;else if(aprobados==requisitos)completas++;
        }
        totales.put("sin_configurar",sinConfigurar);totales.put("completas",completas);
        return Map.of("totales",totales,"practicas",porPractica.values());
    }
    private static void sumar(Map<String,Object> m,String k) { m.put(k,((Long)m.getOrDefault(k,0L))+1); }
}
