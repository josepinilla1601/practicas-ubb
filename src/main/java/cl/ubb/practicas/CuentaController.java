package cl.ubb.practicas;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController
@RequestMapping("/api")
public class CuentaController {
    private final Acceso acceso;
    private final JdbcTemplate db;
    private final PasswordEncoder encoder;
    public CuentaController(Acceso acceso,JdbcTemplate db,PasswordEncoder encoder) { this.acceso=acceso;this.db=db;this.encoder=encoder; }

    @GetMapping("/sesion") public Map<String,Object> sesion(CsrfToken csrf) {
        Map<String,Object> resultado=new LinkedHashMap<>();
        resultado.put("csrf",csrf.getToken());resultado.put("csrfHeader",csrf.getHeaderName());
        try { resultado.put("usuario",acceso.actual()); } catch(Problema ex) { resultado.put("usuario",null); }
        return resultado;
    }
    public record CambioClave(@NotBlank String actual,@NotBlank @Size(min=10,max=60) String nueva) { }
    @PostMapping("/cuenta/clave") public Map<String,String> clave(@Valid @RequestBody CambioClave datos,HttpServletRequest request) {
        long id=acceso.id();
        String hash=db.queryForObject("SELECT clave_hash FROM usuarios WHERE id=?",String.class,id);
        if(!encoder.matches(datos.actual(),hash))throw Problema.invalido("La contraseña actual no coincide.");
        validarClave(datos.nueva());
        if(encoder.matches(datos.nueva(),hash))throw Problema.invalido("La nueva contraseña debe ser diferente.");
        db.update("UPDATE usuarios SET clave_hash=? WHERE id=?",encoder.encode(datos.nueva()),id);
        request.changeSessionId();
        return Map.of("mensaje","Contraseña actualizada.");
    }
    static void validarClave(String clave) {
        if(clave==null || clave.isBlank() || clave.length()<10 || clave.length()>60 || clave.getBytes(StandardCharsets.UTF_8).length>72)
            throw Problema.invalido("Usa entre 10 y 60 caracteres y un máximo de 72 bytes para la contraseña.");
    }
}
