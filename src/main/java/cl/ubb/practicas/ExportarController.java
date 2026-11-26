package cl.ubb.practicas;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * Exporta a Excel las prácticas que la cuenta puede ver: resumen por práctica, requisitos y calificaciones.
 * Respeta los mismos permisos que el resto del sistema (cada profesor UBB exporta solo sus prácticas).
 */
@RestController
@RequestMapping("/api")
public class ExportarController {
    private final JdbcTemplate db;
    private final Acceso acceso;
    private final PracticasService servicio;
    public ExportarController(JdbcTemplate db,Acceso acceso,PracticasService servicio) {this.db=db;this.acceso=acceso;this.servicio=servicio;}

    private static final Map<String,String> ESTADOS=Map.of("PENDIENTE","Pendiente","EN_REVISION","En revisión","OBSERVADO","Corrección solicitada","APROBADO","Aprobado");
    private static final Map<String,String> TIPOS_JORNADA=Map.of("OBSERVACION","Observación","AYUDANTIA","Ayudantía","CLASE","Clase realizada","REUNION","Reunión","OTRA","Otra");
    private static final Map<String,String> ESTADOS_JORNADA=Map.of("REGISTRADA","Por visar","VISADA","Visada","OBSERVADA","Observada");
    private static final Map<String,String> SITUACION_NOTA=Map.of("SIN_NOTAS","Sin notas","EN_CURSO","En curso","APROBADA","Aprobada","REPROBADA","Reprobada");

    @GetMapping("/exportar/practicas.xlsx")
    public ResponseEntity<byte[]> exportar(@RequestParam(required=false) Long asignaturaId,@RequestParam(required=false) Integer anio,
        @RequestParam(required=false) Integer semestre) throws IOException {
        acceso.exigir("COORDINADOR","PROFESOR_UBB");
        // Ordenadas por asignatura, período y estudiante.
        var practicas=new ArrayList<>(servicio.buscarPracticas(null,asignaturaId,anio,semestre));
        practicas.sort(Comparator.comparing((Map<String,Object> p) -> String.valueOf(p.get("asignatura")))
            .thenComparing(p -> ((Number)p.get("anio")).intValue()).thenComparing(p -> ((Number)p.get("semestre")).intValue())
            .thenComparing(p -> String.valueOf(p.get("estudiante"))));
        Set<Long> ids=new HashSet<>();practicas.forEach(p -> ids.add(id(p)));
        Map<Long,String> nombres=new HashMap<>();
        for(var u:db.queryForList("SELECT id,nombre FROM usuarios"))nombres.put(id(u),String.valueOf(u.get("nombre")));
        var f=acceso.filtroPracticas();
        Map<Long,List<Map<String,Object>>> requisitos=new HashMap<>();
        for(var r:servicio.requisitos(f.sql(),f.args()))if(ids.contains(((Number)r.get("practica_id")).longValue()))
            requisitos.computeIfAbsent(((Number)r.get("practica_id")).longValue(),k -> new ArrayList<>()).add(r);

        try(var libro=new XSSFWorkbook();var salida=new ByteArrayOutputStream()) {
            var estilos=new Estilos(libro);
            // Hoja 1: resumen por práctica.
            var resumen=hoja(libro,estilos,"Resumen por práctica",List.of("Estudiante","Identificador","Asignatura","Período","Centro","Profesor UBB","Profesor de colegio",
                "Requisitos","Aprobados","En revisión","Corrección solicitada","Pendientes","Fuera de plazo","Avance","Situación","Promedio","Ponderación evaluada","Situación de la nota"));
            var hojaReq=hoja(libro,estilos,"Requisitos",List.of("Estudiante","Identificador","Asignatura","Período","Requisito","Plazo","Estado","Versiones entregadas","Fuera de plazo"));
            var hojaNotas=hoja(libro,estilos,"Calificaciones",List.of("Estudiante","Identificador","Asignatura","Período","Evaluación","Nota","Ponderación","Comentario","Registrada por","Fecha"));
            var hojaBit=hoja(libro,estilos,"Bitácora",List.of("Estudiante","Identificador","Asignatura","Período","Fecha","Inicio","Término","Horas","Curso","Tipo","Estado","Visada por"));
            int fr=1,fq=1,fn=1,fb=1;
            for(var p:practicas) {
                long pid=id(p);String periodo=p.get("anio")+"-"+p.get("semestre");
                var reqs=requisitos.getOrDefault(pid,List.of());
                long aprob=contar(reqs,"APROBADO"),rev=contar(reqs,"EN_REVISION"),obs=contar(reqs,"OBSERVADO"),pend=contar(reqs,"PENDIENTE");
                long venc=reqs.stream().filter(r -> Boolean.TRUE.equals(r.get("vencido"))).count();
                String situacion=reqs.isEmpty()?"Sin requisitos":aprob==reqs.size()?"Completa":venc>0?"Con atraso":"En curso";
                var notas=CalificacionesController.resumen(db,pid);
                var fila=resumen.createRow(fr++);
                texto(fila,0,p.get("estudiante"));texto(fila,1,p.get("identificador"));texto(fila,2,p.get("asignatura"));texto(fila,3,periodo);
                texto(fila,4,p.get("centro")==null?"Actividad universitaria":p.get("centro"));texto(fila,5,p.get("profesor_responsable"));
                texto(fila,6,p.get("profesor_colegio_id")==null?"—":nombres.get(id(p,"profesor_colegio_id")));
                numero(fila,7,reqs.size(),null);numero(fila,8,aprob,null);numero(fila,9,rev,null);numero(fila,10,obs,null);numero(fila,11,pend,null);
                numero(fila,12,venc,venc>0?estilos.alerta:null);
                numero(fila,13,reqs.isEmpty()?0:(double)aprob/reqs.size(),estilos.porcentaje);
                texto(fila,14,situacion);
                if(notas.get("promedio") instanceof BigDecimal prom)numero(fila,15,prom.doubleValue(),prom.compareTo(ReglasCalificacion.APROBACION)<0?estilos.notaRoja:estilos.nota);else texto(fila,15,"—");
                numero(fila,16,((Number)notas.get("ponderacion")).doubleValue()/100,estilos.porcentaje);
                texto(fila,17,SITUACION_NOTA.get(String.valueOf(notas.get("situacion"))));
                for(var r:reqs) {
                    var q=hojaReq.createRow(fq++);
                    texto(q,0,p.get("estudiante"));texto(q,1,p.get("identificador"));texto(q,2,p.get("asignatura"));texto(q,3,periodo);texto(q,4,r.get("nombre"));
                    var limite=ReglasSeguimiento.fecha(r.get("fecha_limite"));
                    if(limite!=null){var c=q.createCell(5);c.setCellValue(limite);c.setCellStyle(estilos.fecha);}else texto(q,5,"Sin plazo");
                    texto(q,6,ESTADOS.getOrDefault(String.valueOf(r.get("estado")),String.valueOf(r.get("estado"))));
                    numero(q,7,((Number)r.get("archivos")).doubleValue(),null);
                    texto(q,8,Boolean.TRUE.equals(r.get("vencido"))?"Sí":"No");
                    if(Boolean.TRUE.equals(r.get("vencido")))q.getCell(8).setCellStyle(estilos.textoAlerta);
                }
                @SuppressWarnings("unchecked") var lista=(List<Map<String,Object>>)notas.get("notas");
                for(var n:lista) {
                    var q=hojaNotas.createRow(fn++);var nota=(BigDecimal)n.get("nota");
                    texto(q,0,p.get("estudiante"));texto(q,1,p.get("identificador"));texto(q,2,p.get("asignatura"));texto(q,3,periodo);texto(q,4,n.get("evaluacion"));
                    numero(q,5,nota.doubleValue(),nota.compareTo(ReglasCalificacion.APROBACION)<0?estilos.notaRoja:estilos.nota);
                    numero(q,6,((Number)n.get("ponderacion")).doubleValue()/100,estilos.porcentaje);
                    texto(q,7,n.get("comentario"));texto(q,8,n.get("autor"));
                    var c=q.createCell(9);c.setCellValue(((java.sql.Timestamp)n.get("creado_en")).toLocalDateTime().toLocalDate());c.setCellStyle(estilos.fecha);
                }
                for(var j:db.queryForList("SELECT j.fecha,TIME_FORMAT(j.hora_inicio,'%H:%i') inicio,TIME_FORMAT(j.hora_fin,'%H:%i') fin,TIMESTAMPDIFF(MINUTE,j.hora_inicio,j.hora_fin) minutos,j.curso,j.tipo,j.estado,u.nombre visador FROM bitacora_jornadas j LEFT JOIN usuarios u ON u.id=j.visado_por WHERE j.practica_id=? ORDER BY j.fecha,j.hora_inicio",pid)) {
                    var q=hojaBit.createRow(fb++);
                    texto(q,0,p.get("estudiante"));texto(q,1,p.get("identificador"));texto(q,2,p.get("asignatura"));texto(q,3,periodo);
                    var c=q.createCell(4);c.setCellValue(ReglasSeguimiento.fecha(j.get("fecha")));c.setCellStyle(estilos.fecha);
                    texto(q,5,j.get("inicio"));texto(q,6,j.get("fin"));numero(q,7,((Number)j.get("minutos")).doubleValue()/60,estilos.nota);
                    texto(q,8,j.get("curso"));texto(q,9,TIPOS_JORNADA.getOrDefault(String.valueOf(j.get("tipo")),String.valueOf(j.get("tipo"))));
                    texto(q,10,ESTADOS_JORNADA.getOrDefault(String.valueOf(j.get("estado")),String.valueOf(j.get("estado"))));texto(q,11,j.get("visador")==null?"—":j.get("visador"));
                }
            }
            terminar(resumen,18,fr);terminar(hojaReq,9,fq);terminar(hojaNotas,10,fn);terminar(hojaBit,12,fb);
            libro.write(salida);
            String nombre="practicas-ubb-"+LocalDate.now(ReglasSeguimiento.ZONA)+".xlsx";
            return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(nombre).build().toString())
                .body(salida.toByteArray());
        }
    }

    /**
     * S3-24: exporta a Excel las demás listas del sistema (estudiantes, centros, cuentas y bitácora).
     * "q" deja solo las filas que contienen ese texto (lo mismo que se escribió en "Buscar en esta tabla").
     */
    @GetMapping("/exportar/{lista}.xlsx")
    public ResponseEntity<byte[]> exportarLista(@PathVariable String lista,@RequestParam(required=false) String q) throws IOException {
        List<String> columnas;List<List<Object>> filas=new ArrayList<>();String hoja;
        switch(lista) {
            case "estudiantes" -> {
                acceso.exigir("COORDINADOR");hoja="Estudiantes";
                columnas=List.of("Identificador","Nombres","Apellidos","Correo","Prácticas","Cuenta de acceso");
                for(var e:db.queryForList("""
                    SELECT e.identificador,e.nombres,e.apellidos,e.correo,(SELECT COUNT(*) FROM practicas p WHERE p.estudiante_id=e.id) practicas,
                      (SELECT CASE WHEN u.activo THEN 'Activa' ELSE 'Desactivada' END FROM usuarios u WHERE u.estudiante_id=e.id) cuenta
                    FROM estudiantes e ORDER BY e.apellidos,e.nombres"""))
                    filas.add(Arrays.asList(e.get("identificador"),e.get("nombres"),e.get("apellidos"),e.get("correo"),e.get("practicas"),e.get("cuenta")==null?"Sin cuenta":e.get("cuenta")));
            }
            case "centros" -> {
                acceso.exigir("COORDINADOR");hoja="Centros de práctica";
                columnas=List.of("Establecimiento","Comuna","Dirección","Contacto","Correo","Teléfono","Prácticas");
                for(var c:db.queryForList("SELECT c.*,(SELECT COUNT(*) FROM practicas p WHERE p.centro_id=c.id) practicas FROM centros c ORDER BY c.nombre"))
                    filas.add(Arrays.asList(c.get("nombre"),c.get("comuna"),c.get("direccion"),c.get("contacto"),c.get("correo"),c.get("telefono"),c.get("practicas")));
            }
            case "usuarios" -> {
                acceso.exigir("COORDINADOR");hoja="Cuentas y roles";
                columnas=List.of("Nombre","Correo de acceso","Rol","Estado","Estudiante vinculado");
                var roles=Map.of("COORDINADOR","Coordinación","PROFESOR_UBB","Profesor UBB","PROFESOR_COLEGIO","Profesor de colegio","ESTUDIANTE","Estudiante");
                for(var u:db.queryForList("SELECT u.nombre,u.correo,u.rol,u.activo,e.identificador FROM usuarios u LEFT JOIN estudiantes e ON e.id=u.estudiante_id ORDER BY u.rol,u.nombre"))
                    filas.add(Arrays.asList(u.get("nombre"),u.get("correo"),roles.getOrDefault(String.valueOf(u.get("rol")),String.valueOf(u.get("rol"))),
                        Boolean.TRUE.equals(u.get("activo"))?"Activa":"Desactivada",u.get("identificador")==null?"—":u.get("identificador")));
            }
            case "bitacora" -> {
                hoja="Bitácora";var f=acceso.filtroPracticas();
                columnas=List.of("Estudiante","Asignatura","Fecha","Inicio","Término","Horas","Curso","Tipo","Actividades","Reflexión","Estado","Visada por","Comentario del colegio","Retroalimentación UBB");
                for(var j:db.queryForList("""
                    SELECT CONCAT(e.nombres,' ',e.apellidos) estudiante,a.nombre asignatura,j.fecha,TIME_FORMAT(j.hora_inicio,'%H:%i') inicio,TIME_FORMAT(j.hora_fin,'%H:%i') fin,
                      TIMESTAMPDIFF(MINUTE,j.hora_inicio,j.hora_fin) minutos,j.curso,j.tipo,j.actividades,j.reflexion,j.estado,v.nombre visador,j.comentario_colegio,j.comentario_ubb
                    FROM bitacora_jornadas j JOIN practicas p ON p.id=j.practica_id JOIN estudiantes e ON e.id=p.estudiante_id JOIN asignaturas a ON a.id=p.asignatura_id
                    LEFT JOIN usuarios v ON v.id=j.visado_por WHERE"""+" "+f.sql()+" ORDER BY e.apellidos,j.fecha,j.hora_inicio",f.args()))
                    filas.add(Arrays.asList(j.get("estudiante"),j.get("asignatura"),ReglasSeguimiento.fecha(j.get("fecha")),j.get("inicio"),j.get("fin"),((Number)j.get("minutos")).doubleValue()/60,
                        j.get("curso"),TIPOS_JORNADA.getOrDefault(String.valueOf(j.get("tipo")),String.valueOf(j.get("tipo"))),j.get("actividades"),j.get("reflexion"),
                        ESTADOS_JORNADA.getOrDefault(String.valueOf(j.get("estado")),String.valueOf(j.get("estado"))),j.get("visador")==null?"—":j.get("visador"),j.get("comentario_colegio"),j.get("comentario_ubb")));
            }
            default -> throw Problema.noExiste("No existe esa lista para exportar.");
        }
        if(q!=null && !q.isBlank()) {
            String buscado=BuscadorMaterial.sinTildes(q.strip());
            filas.removeIf(fila -> !BuscadorMaterial.sinTildes(fila.stream().map(v -> v==null?"":String.valueOf(v)).reduce("",(a,b)->a+" "+b)).contains(buscado));
        }
        return libroSimple(hoja,columnas,filas,lista);
    }

    /** Libro de una hoja con encabezado, filtros y formatos (números con un decimal y fechas). */
    private ResponseEntity<byte[]> libroSimple(String nombreHoja,List<String> columnas,List<List<Object>> filas,String archivo) throws IOException {
        try(var libro=new XSSFWorkbook();var salida=new ByteArrayOutputStream()) {
            var estilos=new Estilos(libro);var hoja=hoja(libro,estilos,nombreHoja,columnas);int r=1;
            for(var fila:filas) {
                var row=hoja.createRow(r++);
                for(int i=0;i<fila.size();i++) {
                    Object v=fila.get(i);
                    if(v instanceof LocalDate d){var c=row.createCell(i);c.setCellValue(d);c.setCellStyle(estilos.fecha);}
                    else if(v instanceof Double n)numero(row,i,n,estilos.nota);
                    else if(v instanceof Number n)numero(row,i,n.doubleValue(),null);
                    else texto(row,i,v);
                }
            }
            terminar(hoja,columnas.size(),r);
            libro.write(salida);
            String nombre=archivo+"-"+LocalDate.now(ReglasSeguimiento.ZONA)+".xlsx";
            return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(nombre).build().toString()).body(salida.toByteArray());
        }
    }

    /** Estilos del libro: encabezado verde, notas con un decimal (rojas bajo 4,0), porcentajes y fechas. */
    private static final class Estilos {
        final CellStyle encabezado,nota,notaRoja,porcentaje,fecha,alerta,textoAlerta;
        Estilos(Workbook libro) {
            var formato=libro.createDataFormat();
            var negrita=libro.createFont();negrita.setBold(true);negrita.setColor(IndexedColors.WHITE.getIndex());
            encabezado=libro.createCellStyle();encabezado.setFont(negrita);encabezado.setFillForegroundColor(IndexedColors.SEA_GREEN.getIndex());
            encabezado.setFillPattern(FillPatternType.SOLID_FOREGROUND);encabezado.setVerticalAlignment(VerticalAlignment.CENTER);encabezado.setWrapText(true);
            var roja=libro.createFont();roja.setColor(IndexedColors.RED.getIndex());roja.setBold(true);
            nota=libro.createCellStyle();nota.setDataFormat(formato.getFormat("0.0"));
            notaRoja=libro.createCellStyle();notaRoja.setDataFormat(formato.getFormat("0.0"));notaRoja.setFont(roja);
            porcentaje=libro.createCellStyle();porcentaje.setDataFormat(formato.getFormat("0%"));
            fecha=libro.createCellStyle();fecha.setDataFormat(formato.getFormat("dd-mm-yyyy"));
            alerta=libro.createCellStyle();alerta.setFont(roja);
            textoAlerta=libro.createCellStyle();textoAlerta.setFont(roja);
        }
    }
    private static Sheet hoja(Workbook libro,Estilos estilos,String nombre,List<String> columnas) {
        var hoja=libro.createSheet(nombre);var fila=hoja.createRow(0);fila.setHeightInPoints(30);
        for(int i=0;i<columnas.size();i++){var c=fila.createCell(i);c.setCellValue(columnas.get(i));c.setCellStyle(estilos.encabezado);}
        hoja.createFreezePane(0,1);
        return hoja;
    }
    /** Filtro en el encabezado y ancho de columnas según el texto (sin autoSizeColumn, que necesita fuentes gráficas en el servidor). */
    private static void terminar(Sheet hoja,int columnas,int filas) {
        hoja.setAutoFilter(new CellRangeAddress(0,Math.max(filas-1,0),0,columnas-1));
        int[] ancho=new int[columnas];
        for(var fila:hoja)for(var celda:fila)if(celda.getColumnIndex()<columnas) {
            int largo=celda.getCellType()==CellType.STRING?celda.getStringCellValue().length():10;
            if(fila.getRowNum()==0)largo=Math.min(largo,18);
            ancho[celda.getColumnIndex()]=Math.max(ancho[celda.getColumnIndex()],largo);
        }
        for(int i=0;i<columnas;i++)hoja.setColumnWidth(i,Math.min(Math.max(ancho[i]+4,11),60)*256);
    }
    private static void texto(Row fila,int col,Object valor) { fila.createCell(col).setCellValue(valor==null?"":String.valueOf(valor)); }
    private static void numero(Row fila,int col,double valor,CellStyle estilo) { var c=fila.createCell(col);c.setCellValue(valor);if(estilo!=null)c.setCellStyle(estilo); }
    private static long contar(List<Map<String,Object>> reqs,String estado) { return reqs.stream().filter(r -> estado.equals(r.get("estado"))).count(); }
    private static long id(Map<String,Object> m) { return id(m,"id"); }
    private static long id(Map<String,Object> m,String clave) { return ((Number)m.get(clave)).longValue(); }
}
