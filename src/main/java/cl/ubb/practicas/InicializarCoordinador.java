package cl.ubb.practicas;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import java.util.Locale;

/** Aprovisionamiento inicial explícito. No hay contraseñas de producción en el repositorio. */
@Component
public class InicializarCoordinador implements ApplicationRunner {
    private final JdbcTemplate db;
    private final PasswordEncoder encoder;
    private final String correo,clave;
    public InicializarCoordinador(JdbcTemplate db,PasswordEncoder encoder,
        @Value("${BOOTSTRAP_ADMIN_EMAIL:}") String correo,@Value("${BOOTSTRAP_ADMIN_PASSWORD:}") String clave) {
        this.db=db;this.encoder=encoder;this.correo=correo;this.clave=clave;
    }
    @Override public void run(ApplicationArguments args) {
        if(correo.isBlank() && clave.isBlank())return;
        if(correo.isBlank() || clave.isBlank())throw new IllegalStateException("Configura correo y contraseña del coordinador inicial.");
        String email=correo.strip().toLowerCase(Locale.ROOT);
        if(!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+") || email.length()>150)throw new IllegalStateException("Correo inicial inválido.");
        if(db.queryForObject("SELECT COUNT(*) FROM usuarios",Long.class)>0)return;
        CuentaController.validarClave(clave);
        db.update("INSERT INTO usuarios(correo,nombre,clave_hash,rol) VALUES(?,?,?,'COORDINADOR')",email,"Coordinación de prácticas",encoder.encode(clave));
    }
}
