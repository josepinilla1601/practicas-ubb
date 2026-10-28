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
    public void practica(long id) {
        var f=filtroPracticas();var args=new ArrayList<>(Arrays.asList(f.args()));args.add(id);
        if(db.queryForObject("SELECT COUNT(*) FROM practicas p WHERE "+f.sql()+" AND p.id=?",Long.class,args.toArray())==0)
            throw Problema.noExiste("La práctica no existe o no está asignada a tu cuenta.");
    }
}
