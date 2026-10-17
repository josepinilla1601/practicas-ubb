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
    private ResponseEntity<Map<String,Object>> respuesta(HttpStatus estado,String mensaje,Object detalles) {
        return ResponseEntity.status(estado).body(Map.of("mensaje",mensaje,"detalles",detalles));
    }
    @ExceptionHandler(Problema.class)
    ResponseEntity<Map<String,Object>> negocio(Problema e) { return respuesta(e.estado,e.getMessage(),List.of()); }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String,Object>> validacion(MethodArgumentNotValidException e) {
        return respuesta(HttpStatus.BAD_REQUEST,"Revisa los campos obligatorios y sus formatos.",e.getBindingResult().getFieldErrors().stream().map(x->x.getField()+": "+x.getDefaultMessage()).toList());
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
