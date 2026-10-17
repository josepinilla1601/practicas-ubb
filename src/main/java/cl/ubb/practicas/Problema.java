package cl.ubb.practicas;
import org.springframework.http.HttpStatus;

public class Problema extends RuntimeException {
    public final HttpStatus estado;
    public Problema(HttpStatus estado, String mensaje) { super(mensaje); this.estado=estado; }
    public static Problema invalido(String mensaje) { return new Problema(HttpStatus.BAD_REQUEST,mensaje); }
    public static Problema noExiste(String mensaje) { return new Problema(HttpStatus.NOT_FOUND,mensaje); }
}
