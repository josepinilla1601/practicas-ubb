package cl.ubb.practicas;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import java.util.*;

@RestController
@RequestMapping("/api")
public class UsuariosController {
    private final JdbcTemplate db;
    private final Acceso acceso;
    private final PasswordEncoder encoder;
    public UsuariosController(JdbcTemplate db,Acceso acceso,PasswordEncoder encoder) {this.db=db;this.acceso=acceso;this.encoder=encoder;}
    public record Cuenta(@NotBlank @Size(max=150) String nombre,@NotBlank @Email @Size(max=150) String correo,
        @NotBlank @Size(min=10,max=60) String clave,@NotBlank @Pattern(regexp="COORDINADOR|PROFESOR_UBB|PROFESOR_COLEGIO|ESTUDIANTE") String rol,
        @Positive Long estudianteId) { }
    public record Activacion(@NotNull Boolean activo) { }
    public record Asignacion(@NotNull @Positive Long profesorUbbId,@Positive Long profesorColegioId) { }

    @GetMapping("/usuarios") public List<Map<String,Object>> listar() {
        acceso.exigir("COORDINADOR");
        return db.queryForList("SELECT id,nombre,correo,rol,activo,estudiante_id FROM usuarios ORDER BY nombre,id");
    }
    @PostMapping("/usuarios") @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> crear(@Valid @RequestBody Cuenta datos) {
        acceso.exigir("COORDINADOR");CuentaController.validarClave(datos.clave());
        if("ESTUDIANTE".equals(datos.rol())) {
            if(datos.estudianteId()==null || db.queryForObject("SELECT COUNT(*) FROM estudiantes WHERE id=?",Long.class,datos.estudianteId())==0)
                throw Problema.invalido("Selecciona un estudiante registrado.");
        } else if(datos.estudianteId()!=null)throw Problema.invalido("Solo las cuentas de estudiante se vinculan a un estudiante.");
        String correo=datos.correo().strip().toLowerCase(Locale.ROOT);
        db.update("INSERT INTO usuarios(nombre,correo,clave_hash,rol,estudiante_id) VALUES(?,?,?,?,?)",datos.nombre().strip(),correo,encoder.encode(datos.clave()),datos.rol(),datos.estudianteId());
        return db.queryForMap("SELECT id,nombre,correo,rol,activo,estudiante_id FROM usuarios WHERE correo=?",correo);
    }
    @PatchMapping("/usuarios/{id}/activo") public Map<String,String> activar(@PathVariable long id,@Valid @RequestBody Activacion datos) {
        acceso.exigir("COORDINADOR");
        if(id==acceso.id() && !datos.activo())throw Problema.invalido("No puedes desactivar tu propia cuenta.");
        if(db.queryForObject("SELECT COUNT(*) FROM usuarios WHERE id=?",Long.class,id)==0)throw Problema.noExiste("La cuenta no existe.");
        db.update("UPDATE usuarios SET activo=? WHERE id=?",datos.activo(),id);
        return Map.of("mensaje","Estado de la cuenta actualizado.");
    }
    @PutMapping("/practicas/{id}/docentes") @Transactional
    public Map<String,String> asignar(@PathVariable long id,@Valid @RequestBody Asignacion datos) {
        acceso.exigir("COORDINADOR");acceso.practica(id);
        validarDocente(datos.profesorUbbId(),"PROFESOR_UBB");
        if(datos.profesorColegioId()!=null)validarDocente(datos.profesorColegioId(),"PROFESOR_COLEGIO");
        String nombre=db.queryForObject("SELECT nombre FROM usuarios WHERE id=?",String.class,datos.profesorUbbId());
        db.update("UPDATE practicas SET profesor_ubb_id=?,profesor_colegio_id=?,profesor_responsable=? WHERE id=?",
            datos.profesorUbbId(),datos.profesorColegioId(),nombre,id);
        return Map.of("mensaje","Docentes asignados. Los documentos se conservaron.");
    }
    private void validarDocente(long id,String rol) {
        if(db.queryForObject("SELECT COUNT(*) FROM usuarios WHERE id=? AND rol=? AND activo=TRUE",Long.class,id,rol)==0)
            throw Problema.invalido("Selecciona una cuenta activa del rol docente correspondiente.");
    }
}
