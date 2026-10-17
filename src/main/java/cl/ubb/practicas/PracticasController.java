package cl.ubb.practicas;

import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api")
public class PracticasController {
    private final PracticasService servicio;
    public PracticasController(PracticasService servicio) { this.servicio=servicio; }
    @GetMapping("/resumen") public Map<String,Object> resumen(){return servicio.resumen();}
    @GetMapping("/asignaturas") public List<Map<String,Object>> asignaturas(){return servicio.asignaturas();}
    @GetMapping("/estudiantes") public List<Map<String,Object>> estudiantes(){return servicio.estudiantes();}
    @GetMapping("/estudiantes/{id}") public Map<String,Object> estudiante(@PathVariable long id){return servicio.estudiante(id);}
    @PostMapping("/estudiantes") @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> crearEstudiante(@Valid @RequestBody Solicitudes.Estudiante datos){return servicio.crearEstudiante(datos);}
    @GetMapping("/centros") public List<Map<String,Object>> centros(){return servicio.centros();}
    @GetMapping("/centros/{id}") public Map<String,Object> centro(@PathVariable long id){return servicio.centro(id);}
    @PostMapping("/centros") @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> crearCentro(@Valid @RequestBody Solicitudes.Centro datos){return servicio.crearCentro(datos);}
    @GetMapping("/practicas") public List<Map<String,Object>> practicas(){return servicio.practicas();}
    @GetMapping("/practicas/{id}") public Map<String,Object> practica(@PathVariable long id){return servicio.practica(id);}
    @PostMapping("/practicas") @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> crearPractica(@Valid @RequestBody Solicitudes.Practica datos){return servicio.crearPractica(datos);}
    @GetMapping("/practicas/{id}/seguimiento") public Map<String,Object> seguimiento(@PathVariable long id){return servicio.seguimiento(id);}
    @PostMapping(value="/practicas/{id}/requisitos/{requisitoId}/documentos",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> adjuntar(@PathVariable long id,@PathVariable long requisitoId,@RequestPart("archivo") MultipartFile archivo) throws IOException {return servicio.adjuntar(id,requisitoId,archivo);}
    @GetMapping("/documentos/{id}/descarga") public ResponseEntity<byte[]> descargar(@PathVariable long id) {
        var a=servicio.descargar(id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(a.tipo()))
          .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(a.nombre(),StandardCharsets.UTF_8).build().toString())
          .header("X-Content-Type-Options","nosniff").body(a.contenido());
    }
}
