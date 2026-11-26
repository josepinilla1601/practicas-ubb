package cl.ubb.practicas;

import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * S3-08: genera la carta de presentación del practicante para el centro de práctica, en PDF,
 * con los datos de la práctica (estudiante, centro, asignatura, período, profesor supervisor y colaborador).
 */
@RestController
@RequestMapping("/api")
public class CartaPresentacionController {
    private final JdbcTemplate db;
    private final Acceso acceso;
    private final PracticasService servicio;
    public CartaPresentacionController(JdbcTemplate db,Acceso acceso,PracticasService servicio) {this.db=db;this.acceso=acceso;this.servicio=servicio;}

    private static final String[] MESES={"enero","febrero","marzo","abril","mayo","junio","julio","agosto","septiembre","octubre","noviembre","diciembre"};

    @GetMapping("/practicas/{id}/carta.pdf") public ResponseEntity<byte[]> carta(@PathVariable long id) throws IOException {
        acceso.exigir("COORDINADOR","PROFESOR_UBB","ESTUDIANTE");
        var p=servicio.practica(id);
        if(p.get("centro_id")==null)throw Problema.invalido("Esta práctica no tiene centro asignado, así que no requiere carta de presentación.");
        var centro=db.queryForMap("SELECT nombre,comuna,direccion,contacto FROM centros WHERE id=?",p.get("centro_id"));
        var estudiante=db.queryForMap("SELECT identificador,nombres,apellidos,correo FROM estudiantes WHERE id=?",p.get("estudiante_id"));
        String colegio=p.get("profesor_colegio_id")==null?null:db.queryForObject("SELECT nombre FROM usuarios WHERE id=?",String.class,p.get("profesor_colegio_id"));
        String supervisor=String.valueOf(p.get("profesor_responsable"));
        String correoSupervisor=p.get("profesor_ubb_id")==null?null:db.queryForObject("SELECT correo FROM usuarios WHERE id=?",String.class,p.get("profesor_ubb_id"));
        var ficha=db.queryForList("SELECT curso,nivel,dias,horario,fecha_inicio,fecha_termino FROM fichas_inscripcion WHERE practica_id=? AND estado='APROBADA'",id);
        LocalDate hoy=ReglasSeguimiento.hoy();
        String nombre=estudiante.get("nombres")+" "+estudiante.get("apellidos");

        List<String> parrafos=new ArrayList<>();
        parrafos.add("Junto con saludar, la Coordinación de Prácticas de la carrera de Pedagogía en Educación Matemática presenta a "+nombre+
            ", identificador "+estudiante.get("identificador")+", estudiante de nuestra carrera, quien realizará la asignatura "+p.get("asignatura")+
            " durante el período "+p.get("anio")+"-"+p.get("semestre")+" en su establecimiento.");
        if(!ficha.isEmpty()) {
            var f=ficha.get(0);
            parrafos.add("Según la ficha de inscripción aprobada, la práctica se desarrollará en el curso "+f.get("curso")+" ("+f.get("nivel")+"), los días "+f.get("dias")+
                " en horario "+f.get("horario")+", entre el "+fecha(ReglasSeguimiento.fecha(f.get("fecha_inicio")))+" y el "+fecha(ReglasSeguimiento.fecha(f.get("fecha_termino")))+".");
        }
        parrafos.add("Durante este período, la práctica será supervisada por "+supervisor+(correoSupervisor==null?"":" ("+correoSupervisor+")")+
            (colegio==null?", en conjunto con el profesor colaborador que el establecimiento designe.":", en conjunto con "+colegio+" como profesor(a) colaborador(a) del establecimiento."));
        parrafos.add("Solicitamos su colaboración para facilitar la observación de clases, la participación en actividades del curso y la aplicación de las actividades pedagógicas que contempla la asignatura. El seguimiento de las entregas, la bitácora y la evaluación del practicante se realiza en el sistema Prácticas UBB.");
        parrafos.add("Agradecemos de antemano su disposición y el aporte de su comunidad educativa a la formación de futuros profesores de matemática.");

        try(var doc=new PDDocument();var salida=new ByteArrayOutputStream()) {
            var pagina=new PDPage(PDRectangle.A4);doc.addPage(pagina);
            var normal=new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            var negrita=new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            float margen=72,ancho=PDRectangle.A4.getWidth()-2*margen,y=PDRectangle.A4.getHeight()-72;
            try(var c=new PDPageContentStream(doc,pagina)) {
                y=linea(c,negrita,13,margen,y,"Pedagogía en Educación Matemática");
                y=linea(c,normal,10,margen,y-2,"Coordinación de Prácticas · Universidad del Bío-Bío");
                c.setLineWidth(0.8f);c.moveTo(margen,y-6);c.lineTo(margen+ancho,y-6);c.stroke();
                y-=34;
                y=linea(c,normal,11,margen+ancho-normal.getStringWidth("Chillán, "+fecha(hoy))/1000*11,y,"Chillán, "+fecha(hoy));
                y-=18;
                y=linea(c,negrita,11,margen,y,"Señor(a) Director(a)");
                y=linea(c,normal,11,margen,y,String.valueOf(centro.get("nombre")));
                if(centro.get("direccion")!=null && !String.valueOf(centro.get("direccion")).isBlank())y=linea(c,normal,11,margen,y,centro.get("direccion")+", "+centro.get("comuna"));
                else y=linea(c,normal,11,margen,y,String.valueOf(centro.get("comuna")));
                if(centro.get("contacto")!=null && !String.valueOf(centro.get("contacto")).isBlank())y=linea(c,normal,11,margen,y,"At.: "+centro.get("contacto"));
                y-=14;
                y=linea(c,negrita,11,margen,y,"Ref.: Presentación de estudiante en práctica");
                y-=12;
                for(String parrafo:parrafos){for(String l:envolver(parrafo,normal,11,ancho))y=linea(c,normal,11,margen,y,l);y-=9;}
                y-=6;y=linea(c,normal,11,margen,y,"Atentamente,");
                y-=52;
                c.moveTo(margen,y+12);c.lineTo(margen+200,y+12);c.stroke();
                y=linea(c,negrita,11,margen,y,"Coordinación de Prácticas");
                linea(c,normal,10,margen,y,"Pedagogía en Educación Matemática");
                linea(c,normal,8,margen,48,"Documento generado por el sistema Prácticas UBB el "+fecha(hoy)+".");
            }
            doc.save(salida);
            String archivo="carta-presentacion-"+String.valueOf(estudiante.get("apellidos")).split(" ")[0].toLowerCase(Locale.ROOT)+".pdf";
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.inline().filename(archivo).build().toString()).body(salida.toByteArray());
        }
    }

    private static String fecha(LocalDate d) { return d.getDayOfMonth()+" de "+MESES[d.getMonthValue()-1]+" de "+d.getYear(); }
    private static float linea(PDPageContentStream c,PDType1Font fuente,float tam,float x,float y,String texto) throws IOException {
        c.beginText();c.setFont(fuente,tam);c.newLineAtOffset(x,y);c.showText(limpiar(texto));c.endText();
        return y-tam*1.45f;
    }
    /** Divide un párrafo en líneas que caben en el ancho disponible. */
    static List<String> envolver(String texto,PDType1Font fuente,float tam,float ancho) throws IOException {
        List<String> lineas=new ArrayList<>();StringBuilder actual=new StringBuilder();
        for(String palabra:limpiar(texto).split(" ")) {
            String prueba=actual.isEmpty()?palabra:actual+" "+palabra;
            if(fuente.getStringWidth(prueba)/1000*tam>ancho && !actual.isEmpty()){lineas.add(actual.toString());actual=new StringBuilder(palabra);}
            else {actual.setLength(0);actual.append(prueba);}
        }
        if(!actual.isEmpty())lineas.add(actual.toString());
        return lineas;
    }
    /** Las fuentes estándar del PDF usan WinAnsi: se reemplazan los caracteres que no puede dibujar. */
    private static String limpiar(String s) {
        var b=new StringBuilder();
        for(char ch:s.toCharArray())b.append(ch=='\n' || ch=='\t'?' ':(ch<256 || "–—‘’“”•…€".indexOf(ch)>=0)?ch:'?');
        return b.toString();
    }
}
