package cl.ubb.practicas;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * S3-09: la coordinación importa estudiantes desde Excel (.xlsx) o CSV y, si lo pide, crea sus cuentas
 * con una contraseña inicial. Informa fila por fila lo creado, lo omitido (ya existía) y los errores.
 */
@RestController
@RequestMapping("/api")
public class ImportarEstudiantesController {
    private final JdbcTemplate db;
    private final Acceso acceso;
    private final PasswordEncoder encoder;
    public ImportarEstudiantesController(JdbcTemplate db,Acceso acceso,PasswordEncoder encoder) {this.db=db;this.acceso=acceso;this.encoder=encoder;}

    static final int MAXIMO_FILAS=1000;
    private static final Map<String,String> COLUMNAS=Map.of("identificador","identificador","rut","identificador","run","identificador",
        "nombres","nombres","nombre","nombres","apellidos","apellidos","apellido","apellidos","correo","correo","email","correo","correo electronico","correo");

    @GetMapping("/estudiantes/plantilla.xlsx") public ResponseEntity<byte[]> plantilla() throws IOException {
        acceso.exigir("COORDINADOR");
        try(var libro=new XSSFWorkbook();var salida=new ByteArrayOutputStream()) {
            var hoja=libro.createSheet("Estudiantes");
            var negrita=libro.createFont();negrita.setBold(true);var estilo=libro.createCellStyle();estilo.setFont(negrita);
            String[] titulos={"Identificador","Nombres","Apellidos","Correo"};
            var fila=hoja.createRow(0);for(int i=0;i<titulos.length;i++){var c=fila.createCell(i);c.setCellValue(titulos[i]);c.setCellStyle(estilo);hoja.setColumnWidth(i,28*256);}
            var ejemplo=hoja.createRow(1);String[] datos={"20.111.222-3","Ana María","Pérez Soto","ana.perez@alumnos.ubiobio.cl"};
            for(int i=0;i<datos.length;i++)ejemplo.createCell(i).setCellValue(datos[i]);
            libro.write(salida);
            return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename("plantilla-estudiantes.xlsx").build().toString()).body(salida.toByteArray());
        }
    }

    @PostMapping(value="/estudiantes/importar",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String,Object> importar(@RequestPart("archivo") MultipartFile archivo,@RequestParam(defaultValue="false") boolean crearCuentas,
        @RequestParam(required=false) String claveInicial) throws IOException {
        acceso.exigir("COORDINADOR");
        if(crearCuentas)CuentaController.validarClave(claveInicial);
        var filas=leer(archivo);
        if(filas.isEmpty())throw Problema.invalido("El archivo no tiene estudiantes. Usa la plantilla: una fila de títulos y luego un estudiante por fila.");
        if(filas.size()>MAXIMO_FILAS)throw Problema.invalido("El archivo tiene más de "+MAXIMO_FILAS+" filas. Divídelo en partes más pequeñas.");
        String hash=crearCuentas?encoder.encode(claveInicial):null;
        int creados=0,cuentas=0;List<Map<String,Object>> omitidos=new ArrayList<>(),errores=new ArrayList<>();
        for(var f:filas) {
            int numero=(int)f.get("_fila");
            String id=valor(f,"identificador").toUpperCase(Locale.ROOT),nombres=valor(f,"nombres"),apellidos=valor(f,"apellidos"),correo=valor(f,"correo").toLowerCase(Locale.ROOT);
            String error=id.isEmpty()?"falta el identificador":nombres.isEmpty()?"faltan los nombres":apellidos.isEmpty()?"faltan los apellidos":correo.isEmpty()?"falta el correo"
                :!correo.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")?"el correo no es válido":id.length()>30 || nombres.length()>100 || apellidos.length()>100 || correo.length()>150?"un dato es demasiado largo":null;
            if(error!=null){errores.add(Map.of("fila",numero,"motivo",error));continue;}
            if(db.queryForObject("SELECT COUNT(*) FROM estudiantes WHERE identificador=?",Long.class,id)>0){omitidos.add(Map.of("fila",numero,"motivo","el estudiante "+id+" ya estaba registrado"));continue;}
            db.update("INSERT INTO estudiantes(identificador,nombres,apellidos,correo) VALUES(?,?,?,?)",id,nombres,apellidos,correo);creados++;
            if(crearCuentas) {
                if(db.queryForObject("SELECT COUNT(*) FROM usuarios WHERE correo=?",Long.class,correo)>0){omitidos.add(Map.of("fila",numero,"motivo","ya existía una cuenta con el correo "+correo));continue;}
                long estudiante=db.queryForObject("SELECT id FROM estudiantes WHERE identificador=?",Long.class,id);
                db.update("INSERT INTO usuarios(nombre,correo,clave_hash,rol,estudiante_id) VALUES(?,?,?,'ESTUDIANTE',?)",nombres+" "+apellidos,correo,hash,estudiante);cuentas++;
            }
        }
        String mensaje="Importación terminada: "+creados+(creados==1?" estudiante nuevo":" estudiantes nuevos")+(crearCuentas?" y "+cuentas+(cuentas==1?" cuenta":" cuentas"):"")+
            (omitidos.isEmpty()?"":", "+omitidos.size()+" omitido(s)")+(errores.isEmpty()?".":", "+errores.size()+" fila(s) con errores.");
        return Map.of("mensaje",mensaje,"creados",creados,"cuentas",cuentas,"omitidos",omitidos,"errores",errores);
    }

    /** Lee las filas del archivo como mapas columna → valor, reconociendo los títulos sin importar mayúsculas ni tildes. */
    static List<Map<String,Object>> leer(MultipartFile archivo) throws IOException {
        String nombre=Optional.ofNullable(archivo.getOriginalFilename()).orElse("").toLowerCase(Locale.ROOT);
        List<List<String>> tabla=new ArrayList<>();
        if(nombre.endsWith(".xlsx")) {
            try(var libro=WorkbookFactory.create(archivo.getInputStream())) {
                var formato=new DataFormatter();var hoja=libro.getSheetAt(0);
                for(var fila:hoja) {
                    List<String> celdas=new ArrayList<>();
                    for(int i=0;i<Math.max(fila.getLastCellNum(),0);i++)celdas.add(formato.formatCellValue(fila.getCell(i)).strip());
                    tabla.add(celdas);
                }
            } catch(Exception ex) { throw Problema.invalido("No se pudo leer el Excel. Guárdalo como .xlsx e inténtalo de nuevo."); }
        } else if(nombre.endsWith(".csv")) {
            String texto=new String(archivo.getBytes(),StandardCharsets.UTF_8).replace("﻿","");
            for(String linea:texto.split("\\r?\\n")) {
                if(linea.isBlank())continue;
                String separador=linea.contains(";")?";":",";
                tabla.add(Arrays.stream(linea.split(separador,-1)).map(s -> s.strip().replaceAll("^\"|\"$","")).toList());
            }
        } else throw Problema.invalido("Formatos permitidos: Excel (.xlsx) o CSV.");
        if(tabla.isEmpty())return List.of();
        List<String> titulos=tabla.get(0).stream().map(t -> COLUMNAS.getOrDefault(BuscadorMaterial.sinTildes(t).strip(),"")).toList();
        for(String requerida:List.of("identificador","nombres","apellidos","correo"))
            if(!titulos.contains(requerida))throw Problema.invalido("Falta la columna \""+requerida+"\". Los títulos deben ser: Identificador, Nombres, Apellidos y Correo.");
        List<Map<String,Object>> filas=new ArrayList<>();
        for(int r=1;r<tabla.size();r++) {
            var celdas=tabla.get(r);if(celdas.stream().allMatch(String::isBlank))continue;
            var m=new HashMap<String,Object>();m.put("_fila",r+1);
            for(int i=0;i<titulos.size() && i<celdas.size();i++)if(!titulos.get(i).isEmpty())m.put(titulos.get(i),celdas.get(i));
            filas.add(m);
        }
        return filas;
    }
    private static String valor(Map<String,Object> f,String clave) { return String.valueOf(f.getOrDefault(clave,"")).strip(); }
}
