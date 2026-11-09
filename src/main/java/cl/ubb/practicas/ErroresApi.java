package cl.ubb.practicas;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import java.util.*;

@RestControllerAdvice
public class ErroresApi {
    @ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
    ResponseEntity<Map<String,Object>> parametros(Exception e) { return respuesta(HttpStatus.BAD_REQUEST,"Revisa los parámetros de consulta.",List.of()); }
    private ResponseEntity<Map<String,Object>> respuesta(HttpStatus estado,String mensaje,Object detalles) {
        return ResponseEntity.status(estado).body(Map.of("mensaje",mensaje,"detalles",detalles));
    }
    @ExceptionHandler(Problema.class)
    ResponseEntity<Map<String,Object>> negocio(Problema e) { return respuesta(e.estado,e.getMessage(),List.of()); }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String,Object>> validacion(MethodArgumentNotValidException e) {
        // "campos" permite que la interfaz marque cada campo con un mensaje claro y la forma de corregirlo.
        var campos=new LinkedHashMap<String,String>();
        for(var x:e.getBindingResult().getFieldErrors())campos.putIfAbsent(x.getField(),explicar(x.getCode(),x.getArguments()));
        var cuerpo=new LinkedHashMap<String,Object>();
        cuerpo.put("mensaje","Revisa los campos marcados en rojo.");
        cuerpo.put("detalles",List.of());
        cuerpo.put("campos",campos);
        return ResponseEntity.badRequest().body(cuerpo);
    }
    static String explicar(String codigo,Object[] argumentos) {
        if(codigo==null)return "Revisa este dato.";
        return switch(codigo) {
            case "NotBlank","NotNull","NotEmpty" -> "Este campo es obligatorio.";
            case "Email" -> "Escribe un correo válido, por ejemplo nombre@correo.cl.";
            case "Size" -> argumentos!=null && argumentos.length>=3
                ?"Debe tener entre "+argumentos[2]+" y "+argumentos[1]+" caracteres.":"El largo del texto no es válido.";
            case "Min","Max","Positive" -> "El número está fuera del rango permitido.";
            case "Pattern" -> "El formato no es válido. Usa solo letras, números o guiones.";
            default -> "Revisa este dato.";
        };
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Map<String,Object>> integridad(DataIntegrityViolationException e) {
        return respuesta(HttpStatus.CONFLICT,"El registro ya existe o contiene una relación no válida.",List.of());
    }
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<Map<String,Object>> tamano(Exception e) { return respuesta(HttpStatus.PAYLOAD_TOO_LARGE,"El archivo supera el máximo de 10 MB.",List.of()); }
    @ExceptionHandler({HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class,MissingServletRequestPartException.class})
    ResponseEntity<Map<String,Object>> formato(Exception e) { return respuesta(HttpStatus.BAD_REQUEST,"La solicitud contiene datos inválidos o falta el archivo.",List.of()); }
}
