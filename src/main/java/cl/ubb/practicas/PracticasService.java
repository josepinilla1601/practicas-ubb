package cl.ubb.practicas;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.sql.Statement;
import java.util.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Reglas de negocio y consultas SQL parametrizadas. Ningún nombre proviene de SQL del usuario. */
@Service
public class PracticasService {
    private final JdbcTemplate db;
    private final Acceso acceso;
    public PracticasService(JdbcTemplate db,Acceso acceso) { this.db=db;this.acceso=acceso; }
    private static String clean(String x) { return x==null?"":x.strip(); }
    private long insert(String sql,Object... valores) {
        var key=new GeneratedKeyHolder();
        db.update(con->{var ps=con.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS); for(int i=0;i<valores.length;i++) ps.setObject(i+1,valores[i]); return ps;},key);
        return Objects.requireNonNull(key.getKey()).longValue();
    }
    private Map<String,Object> uno(String sql,String error,Object... args) {
        var rows=db.queryForList(sql,args); if(rows.isEmpty())throw Problema.noExiste(error); return rows.get(0);
    }
    public List<Map<String,Object>> estudiantes() { acceso.exigir("COORDINADOR");return db.queryForList("SELECT id,identificador,nombres,apellidos,correo FROM estudiantes ORDER BY apellidos,nombres,id"); }
    public Map<String,Object> estudiante(long id) { acceso.exigir("COORDINADOR");return uno("SELECT id,identificador,nombres,apellidos,correo FROM estudiantes WHERE id=?","El estudiante no existe.",id); }
    public Map<String,Object> crearEstudiante(Solicitudes.Estudiante s) {
        acceso.exigir("COORDINADOR");
        long id=insert("INSERT INTO estudiantes(identificador,nombres,apellidos,correo) VALUES(?,?,?,?)",clean(s.identificador()).toUpperCase(Locale.ROOT),clean(s.nombres()),clean(s.apellidos()),clean(s.correo()).toLowerCase(Locale.ROOT));
        return estudiante(id);
    }
    public Map<String,Object> editarEstudiante(long id,Solicitudes.Estudiante s) {
        estudiante(id);
        db.update("UPDATE estudiantes SET identificador=?,nombres=?,apellidos=?,correo=? WHERE id=?",clean(s.identificador()).toUpperCase(Locale.ROOT),clean(s.nombres()),clean(s.apellidos()),clean(s.correo()).toLowerCase(Locale.ROOT),id);
        return estudiante(id);
    }
    public List<Map<String,Object>> centros() { acceso.exigir("COORDINADOR");return db.queryForList("SELECT * FROM centros ORDER BY nombre,id"); }
    public Map<String,Object> centro(long id) { acceso.exigir("COORDINADOR");return uno("SELECT * FROM centros WHERE id=?","El centro no existe.",id); }
    public Map<String,Object> crearCentro(Solicitudes.Centro s) {
        acceso.exigir("COORDINADOR");
        long id=insert("INSERT INTO centros(nombre,comuna,direccion,contacto,correo,telefono) VALUES(?,?,?,?,?,?)",clean(s.nombre()),clean(s.comuna()),clean(s.direccion()),clean(s.contacto()),clean(s.correo()),clean(s.telefono()));
        return centro(id);
    }
    public Map<String,Object> editarCentro(long id,Solicitudes.Centro s) {
        centro(id);
        db.update("UPDATE centros SET nombre=?,comuna=?,direccion=?,contacto=?,correo=?,telefono=? WHERE id=?",clean(s.nombre()),clean(s.comuna()),clean(s.direccion()),clean(s.contacto()),clean(s.correo()),clean(s.telefono()),id);
        return centro(id);
    }
    public List<Map<String,Object>> asignaturas() { return db.queryForList("SELECT * FROM asignaturas ORDER BY id"); }
    private static final String PRACTICAS="""
        SELECT p.id,p.estudiante_id,p.asignatura_id,p.centro_id,p.profesor_responsable,p.profesor_ubb_id,p.profesor_colegio_id,
          e.identificador,CONCAT(e.nombres,' ',e.apellidos) estudiante,
          a.nombre asignatura,a.ciclo,a.requiere_centro,c.nombre centro,pe.anio,pe.semestre,
          (SELECT COUNT(*) FROM requisitos_practica r WHERE r.practica_id=p.id) requeridos,
          (SELECT COUNT(*) FROM requisitos_practica r WHERE r.practica_id=p.id
           AND EXISTS(SELECT 1 FROM documentos d WHERE d.requisito_id=r.id)) recibidos
        FROM practicas p JOIN estudiantes e ON e.id=p.estudiante_id
        JOIN asignaturas a ON a.id=p.asignatura_id JOIN periodos pe ON pe.id=p.periodo_id
        LEFT JOIN centros c ON c.id=p.centro_id
        """;
    public List<Map<String,Object>> practicas() { var f=acceso.filtroPracticas();return db.queryForList(PRACTICAS+" WHERE "+f.sql()+" ORDER BY p.id DESC",f.args()); }
    public List<Map<String,Object>> buscarPracticas(String busqueda,Long asignaturaId,Integer anio,Integer semestre) {
        var f=acceso.filtroPracticas();StringBuilder sql=new StringBuilder(PRACTICAS+" WHERE "+f.sql());
        var args=new ArrayList<>(Arrays.asList(f.args()));
        if(busqueda!=null && !busqueda.isBlank()) {
            if(busqueda.length()>150)throw Problema.invalido("La búsqueda admite hasta 150 caracteres.");
            String q="%"+busqueda.strip().replace("!","!!").replace("%","!%").replace("_","!_")+"%";
            sql.append(" AND (CONCAT(e.nombres,' ',e.apellidos) LIKE ? ESCAPE '!' OR e.identificador LIKE ? ESCAPE '!')");args.add(q);args.add(q);
        }
        if(asignaturaId!=null){sql.append(" AND p.asignatura_id=?");args.add(asignaturaId);}
        if(anio!=null){sql.append(" AND pe.anio=?");args.add(anio);}
        if(semestre!=null){if(semestre<1||semestre>2)throw Problema.invalido("Semestre inválido.");sql.append(" AND pe.semestre=?");args.add(semestre);}
        return db.queryForList(sql+" ORDER BY p.id DESC",args.toArray());
    }
    public Map<String,Object> practica(long id) { acceso.practica(id);return uno(PRACTICAS+" WHERE p.id=?","La práctica no existe.",id); }
    @Transactional
    public Map<String,Object> crearPractica(Solicitudes.Practica s) {
        acceso.exigir("COORDINADOR");
        estudiante(s.estudianteId());
        var asignatura=uno("SELECT * FROM asignaturas WHERE id=?","La asignatura no existe.",s.asignaturaId());
        boolean necesita=Boolean.TRUE.equals(asignatura.get("requiere_centro")) || "1".equals(String.valueOf(asignatura.get("requiere_centro")));
        if(necesita && s.centroId()==null)throw Problema.invalido("Esta asignatura requiere un centro de práctica.");
        if(s.centroId()!=null)centro(s.centroId());
        db.update("INSERT IGNORE INTO periodos(anio,semestre) VALUES(?,?)",s.anio(),s.semestre());
        long periodo=db.queryForObject("SELECT id FROM periodos WHERE anio=? AND semestre=?",Long.class,s.anio(),s.semestre());
        long id=insert("INSERT INTO practicas(estudiante_id,asignatura_id,periodo_id,centro_id,profesor_responsable) VALUES(?,?,?,?,?)",s.estudianteId(),s.asignaturaId(),periodo,s.centroId(),clean(s.profesorResponsable()));
        db.update("INSERT INTO requisitos_practica(practica_id,codigo,nombre) SELECT ?,codigo,nombre FROM requisitos_plantilla WHERE asignatura_id=? AND periodo_id=?",id,s.asignaturaId(),periodo);
        return practica(id);
    }
    public Map<String,Object> seguimiento(long id) {
        var practica=practica(id);
        var requisitos=db.queryForList("""
            SELECT r.id,r.codigo,r.nombre,COUNT(d.id) archivos,
             CASE WHEN COUNT(d.id)>0 THEN 'RECIBIDO' ELSE 'PENDIENTE' END estado
            FROM requisitos_practica r LEFT JOIN documentos d ON d.requisito_id=r.id
            WHERE r.practica_id=? GROUP BY r.id,r.codigo,r.nombre ORDER BY r.id
            """,id);
        var documentos=db.queryForList("""
            SELECT d.id,d.requisito_id,d.nombre_original,d.tipo_contenido,d.tamanio,
             d.cargado_en,r.nombre requisito
            FROM documentos d JOIN requisitos_practica r ON r.id=d.requisito_id
            WHERE r.practica_id=? ORDER BY d.cargado_en DESC,d.id DESC
            """,id);
        return Map.of("practica",practica,"requisitos",requisitos,"documentos",documentos,
            "configurado",!requisitos.isEmpty(),"nota","Los requisitos precargados son una propuesta de demostración por validar con el profesor.");
    }
    @Transactional
    public Map<String,Object> adjuntar(long practicaId,long requisitoId,MultipartFile archivo) throws IOException {
        acceso.exigir("COORDINADOR","ESTUDIANTE");
        practica(practicaId);
        uno("SELECT id FROM requisitos_practica WHERE id=? AND practica_id=?","El requisito no pertenece a esta práctica.",requisitoId,practicaId);
        if(archivo.isEmpty())throw Problema.invalido("Selecciona un archivo con contenido.");
        if(archivo.getSize()>10L*1024*1024)throw Problema.invalido("El archivo supera 10 MB.");
        String name=clean(archivo.getOriginalFilename()).replace('\\','/'); name=name.substring(name.lastIndexOf('/')+1).replaceAll("[\\p{Cntrl}]", "_");
        if(name.isBlank() || name.length()>255)throw Problema.invalido("El nombre del archivo debe tener entre 1 y 255 caracteres.");
        String lower=name.toLowerCase(Locale.ROOT);byte[] bytes=archivo.getBytes();String mime;
        if(lower.endsWith(".pdf")) {
            if(bytes.length<5 || !new String(bytes,0,5,StandardCharsets.US_ASCII).equals("%PDF-"))throw Problema.invalido("El archivo no tiene una cabecera PDF válida.");
            mime="application/pdf";
        } else if(lower.endsWith(".docx") || lower.endsWith(".xlsx")) {
            if(bytes.length<4 || bytes[0]!='P' || bytes[1]!='K' || bytes[2]!=3 || bytes[3]!=4)throw Problema.invalido("El archivo no tiene la estructura inicial de un documento Office válido.");
            mime=lower.endsWith(".docx")?"application/vnd.openxmlformats-officedocument.wordprocessingml.document":"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        } else throw Problema.invalido("Formatos permitidos: PDF, DOCX y XLSX.");
        long id=insert("INSERT INTO documentos(requisito_id,nombre_original,tipo_contenido,tamanio,contenido) VALUES(?,?,?,?,?)",requisitoId,name,mime,bytes.length,bytes);
        return Map.of("id",id,"nombre_original",name,"requisito_id",requisitoId,"tamanio",bytes.length);
    }
    public record Archivo(String nombre,String tipo,byte[] contenido) { }
    public Archivo descargar(long id) {
        var relacion=uno("SELECT r.practica_id FROM documentos d JOIN requisitos_practica r ON r.id=d.requisito_id WHERE d.id=?","El documento no existe.",id);
        acceso.practica(((Number)relacion.get("practica_id")).longValue());
        var rows=db.query("SELECT nombre_original,tipo_contenido,contenido FROM documentos WHERE id=?",(rs,n)->new Archivo(rs.getString(1),rs.getString(2),rs.getBytes(3)),id);
        if(rows.isEmpty())throw Problema.noExiste("El documento no existe.");return rows.get(0);
    }
    public Map<String,Object> resumen() {
        if(!"COORDINADOR".equals(acceso.rol())) {
            var f=acceso.filtroPracticas();
            var r=db.queryForMap("SELECT COUNT(*) practicas,COUNT(DISTINCT p.estudiante_id) estudiantes,COUNT(DISTINCT p.centro_id) centros FROM practicas p WHERE "+f.sql(),f.args());
            r.put("documentos",db.queryForObject("SELECT COUNT(*) FROM documentos d JOIN requisitos_practica r ON r.id=d.requisito_id JOIN practicas p ON p.id=r.practica_id WHERE "+f.sql(),Long.class,f.args()));return r;
        }
        return Map.of("estudiantes",db.queryForObject("SELECT COUNT(*) FROM estudiantes",Long.class),
          "centros",db.queryForObject("SELECT COUNT(*) FROM centros",Long.class),
          "practicas",db.queryForObject("SELECT COUNT(*) FROM practicas",Long.class),
          "documentos",db.queryForObject("SELECT COUNT(*) FROM documentos",Long.class));
    }
}
