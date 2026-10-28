package cl.ubb.practicas;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Statement;
import java.util.*;

/** S2-16 y S2-17: el profesor carga material de consulta y decide cuándo queda disponible para el asistente. */
@RestController
@RequestMapping("/api")
public class MaterialController {
    private final JdbcTemplate db;
    private final Acceso acceso;
    public MaterialController(JdbcTemplate db,Acceso acceso) {this.db=db;this.acceso=acceso;}
    public record Estado(@NotBlank @Pattern(regexp="PUBLICADO|RETIRADO") String estado) { }
    private static final String LISTA="""
        SELECT m.id,m.titulo,m.nombre_original,m.tipo_contenido,m.tamanio,m.estado,m.creado_en,m.actualizado_en,
         a.nombre asignatura,pe.anio,pe.semestre,u.nombre autor,
         (SELECT COUNT(*) FROM material_fragmentos f WHERE f.material_id=m.id) fragmentos
        FROM materiales m JOIN asignaturas a ON a.id=m.asignatura_id JOIN periodos pe ON pe.id=m.periodo_id JOIN usuarios u ON u.id=m.autor_id
        """;

    @GetMapping("/materiales") public List<Map<String,Object>> listar(@RequestParam(required=false) Long asignaturaId,
        @RequestParam(required=false) Integer anio,@RequestParam(required=false) Integer semestre) {
        acceso.exigir("COORDINADOR","PROFESOR_UBB");
        StringBuilder sql=new StringBuilder(LISTA+" WHERE 1=1");List<Object> args=new ArrayList<>();
        if(asignaturaId!=null){sql.append(" AND m.asignatura_id=?");args.add(asignaturaId);}
        if(anio!=null){sql.append(" AND pe.anio=?");args.add(anio);}
        if(semestre!=null){sql.append(" AND pe.semestre=?");args.add(semestre);}
        return db.queryForList(sql+" ORDER BY m.id DESC",args.toArray());
    }
    @GetMapping("/materiales/{id}") public Map<String,Object> detalle(@PathVariable long id) {
        acceso.exigir("COORDINADOR","PROFESOR_UBB");
        var rows=db.queryForList(LISTA+" WHERE m.id=?",id);
        if(rows.isEmpty())throw Problema.noExiste("El material no existe.");
        var m=new LinkedHashMap<String,Object>(rows.get(0));
        String texto=db.queryForObject("SELECT texto FROM materiales WHERE id=?",String.class,id);
        m.put("vista_previa",texto.length()>3000?texto.substring(0,3000)+"…":texto);
        return m;
    }
    @PostMapping(value="/materiales",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @ResponseStatus(HttpStatus.CREATED) @Transactional
    public Map<String,Object> cargar(@RequestPart("archivo") MultipartFile archivo,@RequestParam String titulo,
        @RequestParam long asignaturaId,@RequestParam int anio,@RequestParam int semestre) throws IOException {
        acceso.exigir("COORDINADOR","PROFESOR_UBB");
        String t=titulo==null?"":titulo.strip();
        if(t.isEmpty() || t.length()>180)throw Problema.invalido("El título es obligatorio y admite hasta 180 caracteres.");
        if(anio<2000 || anio>2100 || semestre<1 || semestre>2)throw Problema.invalido("Revisa el año y el semestre.");
        if(db.queryForObject("SELECT COUNT(*) FROM asignaturas WHERE id=?",Long.class,asignaturaId)==0)throw Problema.noExiste("La asignatura no existe.");
        if(archivo.isEmpty())throw Problema.invalido("Selecciona un archivo con contenido.");
        if(archivo.getSize()>10L*1024*1024)throw Problema.invalido("El archivo supera 10 MB.");
        String original=archivo.getOriginalFilename()==null?"":archivo.getOriginalFilename().replace('\\','/');
        String nombre=original.substring(original.lastIndexOf('/')+1).replaceAll("[\\p{Cntrl}]","_").strip();
        if(nombre.isBlank() || nombre.length()>255)throw Problema.invalido("El nombre del archivo debe tener entre 1 y 255 caracteres.");
        byte[] bytes=archivo.getBytes();
        var extraccion=TextoMaterial.extraer(nombre,bytes);
        var fragmentos=TextoMaterial.fragmentar(extraccion.texto());
        db.update("INSERT IGNORE INTO periodos(anio,semestre) VALUES(?,?)",anio,semestre);
        long periodo=db.queryForObject("SELECT id FROM periodos WHERE anio=? AND semestre=?",Long.class,anio,semestre);
        long autor=acceso.id();
        var key=new GeneratedKeyHolder();
        db.update(con->{
            var ps=con.prepareStatement("INSERT INTO materiales(asignatura_id,periodo_id,titulo,nombre_original,tipo_contenido,tamanio,contenido,texto,autor_id) VALUES(?,?,?,?,?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1,asignaturaId);ps.setLong(2,periodo);ps.setString(3,t);ps.setString(4,nombre);ps.setString(5,extraccion.tipo());
            ps.setLong(6,bytes.length);ps.setBytes(7,bytes);ps.setString(8,extraccion.texto());ps.setLong(9,autor);return ps;},key);
        long id=Objects.requireNonNull(key.getKey()).longValue();
        for(int i=0;i<fragmentos.size();i++)db.update("INSERT INTO material_fragmentos(material_id,orden,texto) VALUES(?,?,?)",id,i+1,fragmentos.get(i));
        return Map.of("id",id,"fragmentos",fragmentos.size(),"mensaje","Material procesado. Queda como borrador hasta que lo publiques.");
    }
    @PatchMapping("/materiales/{id}/estado") public Map<String,String> estado(@PathVariable long id,@Valid @RequestBody Estado datos) {
        acceso.exigir("COORDINADOR","PROFESOR_UBB");
        if(db.update("UPDATE materiales SET estado=?,actualizado_en=CURRENT_TIMESTAMP WHERE id=?",datos.estado(),id)==0)throw Problema.noExiste("El material no existe.");
        return Map.of("mensaje","PUBLICADO".equals(datos.estado())?"Material publicado: el asistente ya puede usarlo.":"Material retirado: el asistente dejará de usarlo.");
    }
    @GetMapping("/materiales/{id}/descarga") public ResponseEntity<byte[]> descargar(@PathVariable long id) {
        acceso.exigir("COORDINADOR","PROFESOR_UBB");
        var rows=db.queryForList("SELECT nombre_original,tipo_contenido,contenido FROM materiales WHERE id=?",id);
        if(rows.isEmpty())throw Problema.noExiste("El material no existe.");
        var r=rows.get(0);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(r.get("tipo_contenido").toString()))
            .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(r.get("nombre_original").toString(),StandardCharsets.UTF_8).build().toString())
            .header("X-Content-Type-Options","nosniff").body((byte[])r.get("contenido"));
    }
}
