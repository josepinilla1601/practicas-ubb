package cl.ubb.practicas;

import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/** Identidad y autorización consultadas en el servidor. Nunca acepta el rol enviado por la interfaz. */
@Service
public class Acceso {
    private final JdbcTemplate db;
    public Acceso(JdbcTemplate db) { this.db=db; }
    public Map<String,Object> actual() {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        if(auth==null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName()))
            throw new Problema(HttpStatus.UNAUTHORIZED,"Inicia sesión.");
        return db.queryForList("SELECT id,correo,nombre,rol,estudiante_id FROM usuarios WHERE correo=? AND activo=TRUE",auth.getName())
            .stream().findFirst().orElseThrow(() -> new Problema(HttpStatus.UNAUTHORIZED,"Cuenta no disponible."));
    }
    public long id() { return ((Number)actual().get("id")).longValue(); }
    public String rol() { return actual().get("rol").toString(); }
    public void exigir(String... roles) {
        if(!Arrays.asList(roles).contains(rol()))throw new Problema(HttpStatus.FORBIDDEN,"No tienes permiso para esta operación.");
    }
    public record Filtro(String sql,Object[] args) { }
    public Filtro filtroPracticas() {
        var u=actual();
        return switch(u.get("rol").toString()) {
            case "COORDINADOR" -> new Filtro("1=1",new Object[]{});
            case "PROFESOR_UBB" -> new Filtro("p.profesor_ubb_id=?",new Object[]{u.get("id")});
            case "PROFESOR_COLEGIO" -> new Filtro("p.profesor_colegio_id=?",new Object[]{u.get("id")});
            case "ESTUDIANTE" -> new Filtro("p.estudiante_id=?",new Object[]{u.get("estudiante_id")});
            default -> new Filtro("1=0",new Object[]{});
        };
    }
    /**
     * Igual que {@link #filtroPracticas()}, pero además limita las listas al período elegido en el selector del encabezado
     * (cabecera X-Periodo o parámetro "periodo", con formato AAAA-S). No se usa para autorizar: una práctica de otro
     * período sigue siendo accesible desde su enlace.
     */
    public Filtro filtroListado() {
        var f=filtroPracticas();
        var periodo=periodoSolicitado();
        if(periodo==null)return f;
        var args=new ArrayList<>(Arrays.asList(f.args()));args.add(periodo[0]);args.add(periodo[1]);
        return new Filtro("("+f.sql()+") AND p.periodo_id=(SELECT pf.id FROM periodos pf WHERE pf.anio=? AND pf.semestre=?)",args.toArray());
    }
    /** Período elegido en el selector, como {año, semestre}, o null si se ven todos. */
    public int[] periodoSolicitado() {
        if(!(org.springframework.web.context.request.RequestContextHolder.getRequestAttributes() instanceof org.springframework.web.context.request.ServletRequestAttributes atributos))return null;
        var pedido=atributos.getRequest();
        String valor=Optional.ofNullable(pedido.getHeader("X-Periodo")).orElse(pedido.getParameter("periodo"));
        if(valor==null || !valor.strip().matches("\\d{4}-[12]"))return null;
        String[] partes=valor.strip().split("-");
        return new int[]{Integer.parseInt(partes[0]),Integer.parseInt(partes[1])};
    }
    /** Períodos que tienen prácticas visibles para la cuenta (para el selector del encabezado). */
    public List<Map<String,Object>> periodosVisibles() {
        var f=filtroPracticas();
        return db.queryForList("SELECT DISTINCT pe.anio,pe.semestre FROM practicas p JOIN periodos pe ON pe.id=p.periodo_id WHERE "+f.sql()+" ORDER BY pe.anio DESC,pe.semestre DESC",f.args());
    }
    public void practica(long id) {
        var f=filtroPracticas();var args=new ArrayList<>(Arrays.asList(f.args()));args.add(id);
        if(db.queryForObject("SELECT COUNT(*) FROM practicas p WHERE "+f.sql()+" AND p.id=?",Long.class,args.toArray())==0)
            throw Problema.noExiste("La práctica no existe o no está asignada a tu cuenta.");
    }
}
