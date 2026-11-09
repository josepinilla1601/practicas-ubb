package cl.ubb.practicas;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.*;

/**
 * S2-18: responde con el material publicado y cita sus fuentes (redactado con IA si está configurada).
 * S2-19: informa los pendientes y plazos reales del estudiante.
 * S2-20: guarda y muestra el historial personal de cada cuenta.
 * Explicar documento: la IA explica un archivo subido o un material publicado.
 */
@RestController
@RequestMapping("/api")
public class AsistenteController {
    private final JdbcTemplate db;
    private final Acceso acceso;
    private final PracticasService servicio;
    private final ObjectMapper json;
    private final ModeloIA ia;
    public AsistenteController(JdbcTemplate db,Acceso acceso,PracticasService servicio,ObjectMapper json,ModeloIA ia) {this.db=db;this.acceso=acceso;this.servicio=servicio;this.json=json;this.ia=ia;}
    public record Pregunta(@NotBlank @Size(max=1000) String pregunta) { }

    /** Máximo de texto de un documento que se envía a la IA (unas 15 a 20 páginas). */
    static final int LIMITE_DOCUMENTO=60_000;
    static final String SIN_INFORMACION="No encontré esa información en el material publicado.";
    private static final String INSTRUCCIONES_MATERIAL="""
        Eres el asistente del sistema Prácticas UBB (Pedagogía en Educación Matemática, Universidad del Bío-Bío).
        Responde en español de Chile, con un tono cercano y claro, en máximo seis oraciones o una lista corta. No saludes ni te presentes: ve directo a la respuesta.
        Usa SOLO la información de los fragmentos numerados que se entregan. Después de cada dato indica entre corchetes el número del fragmento de donde lo sacaste, por ejemplo [1]. Usa solo números de fragmentos que existan.
        Si los fragmentos no permiten responder la pregunta, responde exactamente: "%s"
        No inventes datos, fechas ni porcentajes. No uses formato Markdown (ni asteriscos ni #); para listas usa "• ".
        """.formatted(SIN_INFORMACION);
    private static final String INSTRUCCIONES_DOCUMENTO="""
        Eres el asistente del sistema Prácticas UBB y ayudas a estudiantes de Pedagogía en Educación Matemática a entender documentos.
        No saludes ni te presentes. Explica el documento en español de Chile, con palabras simples y en este orden:
        1. De qué trata, en dos o tres oraciones.
        2. Las ideas o instrucciones principales, como lista con "• ".
        3. Qué debe hacer el estudiante (entregas, plazos o requisitos), solo si el documento lo indica.
        4. Los conceptos difíciles, explicados de forma sencilla, si los hay.
        Si el estudiante hace una pregunta, respóndela primero usando solo el documento. Si la respuesta no está en el documento, dilo.
        No inventes información. No uses formato Markdown (ni asteriscos ni #). Sé breve: máximo unas 250 palabras.
        """;

    /** Guía del sistema: la IA la usa para responder preguntas sobre cómo usar la plataforma y el propio asistente. */
    private static final String GUIA_SISTEMA="""
        Guía de uso de Prácticas UBB (plataforma de seguimiento de prácticas pedagógicas):
        - Mi panel (estudiantes): muestra requisitos, plazos, atrasos y estados (Pendiente, En revisión, Corrección solicitada, Aprobado). Los requisitos están ordenados por urgencia.
        - Entregar un documento: botón "Entregar documento" o "Entregar" en el requisito; se sube un PDF, DOCX o XLSX de hasta 10 MB. Si te equivocas, aparece "Deshacer" o puedes usar "Eliminar entrega" mientras no esté revisada.
        - Corrección solicitada: el profesor dejó un comentario; se sube una versión corregida en el mismo requisito.
        - Profesores UBB: revisan entregas en el seguimiento de cada práctica (aprobar o solicitar corrección, con confirmación y opción de deshacer).
        - Profesores de colegio: registran fortalezas y aspectos por mejorar de sus practicantes.
        - Asistente: responde preguntas con el material publicado por los profesores y cita la fuente. Si eres estudiante, también te dice tus requisitos pendientes y plazos (pregunta "¿Qué tengo pendiente?").
        - Explicar un documento: en el asistente flotante, pulsa el clip (a la izquierda del cuadro de texto) y elige un PDF, Word (DOCX), TXT o MD; si quieres algo específico, escribe tu pregunta antes de adjuntarlo. En la página "Asistente" también está la sección "Explicar un documento", donde puedes elegir un material publicado. El archivo no se guarda.
        - Dictado por voz: en los campos de texto largos y en el asistente hay un micrófono; púlsalo, permite el micrófono y habla. Puedes decir "coma", "punto y seguido" o "punto y aparte". Funciona en Chrome y Edge.
        - Ayuda: el botón "? Ayuda" (arriba) explica cada página paso a paso; la tecla ? también la abre. La guía de bienvenida está en tu nombre, arriba a la derecha.
        - Contraseña olvidada: la coordinación de prácticas asigna una nueva; luego se cambia en tu nombre → Cambiar contraseña.
        """;
    private static final String INSTRUCCIONES_GENERAL="""
        Eres el asistente del sistema Prácticas UBB (Pedagogía en Educación Matemática, Universidad del Bío-Bío).
        Responde en español de Chile, con tono cercano y breve (máximo cinco oraciones o una lista corta). No uses Markdown; para listas usa "• ".
        Si la pregunta es un saludo, un agradecimiento o trata sobre cómo usar la plataforma o el asistente, respóndela usando SOLO la guía entregada.
        Saluda solo si el usuario saludó. Si pregunta qué puedes hacer, menciona todas las funciones del asistente que aparecen en la guía (material con fuentes, pendientes y plazos, explicar documentos y dictado).
        Si pregunta por reglamentos, requisitos académicos, fechas, porcentajes, notas u otra información de las prácticas que no está en la guía,
        o si no tiene relación con las prácticas, responde exactamente: "%s"
        No inventes funciones que no estén en la guía.
        """.formatted(SIN_INFORMACION);

    private static final Set<String> PALABRAS_PLAZO=Set.of("pendiente","plazo","vence","vencen","vencido","vencida","atrasado","atrasada","atraso");
    private static final Set<String> PALABRAS_PERSONALES=Set.of("mis","me","tengo","debo","falta","faltan","yo");
    private static final Set<String> PALABRAS_ENTREGA=Set.of("entregar","entrega","entregas","requisito","requisitos","documento","documentos","falta","faltan");

    static boolean preguntaPorPlazos(String pregunta) {
        var palabras=new HashSet<>(Arrays.asList(BuscadorMaterial.sinTildes(pregunta).split("[^a-z0-9ñ]+")));
        if(palabras.stream().anyMatch(p -> PALABRAS_PLAZO.contains(p) || p.startsWith("pendiente") || p.startsWith("plazo")))return true;
        return palabras.stream().anyMatch(PALABRAS_PERSONALES::contains) && palabras.stream().anyMatch(PALABRAS_ENTREGA::contains);
    }

    /** Indica a la interfaz si la IA está activa (sin revelar la clave). */
    @GetMapping("/asistente/estado") public Map<String,Object> estado() { acceso.actual(); return Map.of("ia",ia.disponible()); }

    @PostMapping("/asistente") public Map<String,Object> preguntar(@Valid @RequestBody Pregunta datos) {
        var usuario=acceso.actual();
        String pregunta=datos.pregunta().strip();
        Map<String,Object> respuesta="ESTUDIANTE".equals(usuario.get("rol")) && preguntaPorPlazos(pregunta)?plazos():material(usuario,pregunta);
        return guardar(usuario,pregunta,respuesta);
    }

    @GetMapping("/asistente/historial") public List<Map<String,Object>> historial() {
        var mensajes=db.queryForList("SELECT id,pregunta,respuesta,tipo,fuentes,creado_en FROM asistente_mensajes WHERE usuario_id=? ORDER BY id DESC LIMIT 50",acceso.id());
        for(var m:mensajes) {
            try { m.put("fuentes",json.readValue(String.valueOf(m.get("fuentes")),new TypeReference<List<Map<String,Object>>>() { })); }
            catch(JsonProcessingException ex) { m.put("fuentes",List.of()); }
        }
        Collections.reverse(mensajes);
        return mensajes;
    }

    /** Explica con IA un archivo subido (PDF, DOCX, TXT o MD) o un material publicado. El archivo no se guarda. */
    @PostMapping(value="/asistente/explicar",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String,Object> explicar(@RequestPart(value="archivo",required=false) MultipartFile archivo,
        @RequestParam(required=false) Long materialId,@RequestParam(required=false) String pregunta) throws IOException {
        var usuario=acceso.actual();
        if(!ia.disponible())throw new Problema(HttpStatus.SERVICE_UNAVAILABLE,"La explicación de documentos con IA todavía no está activada. Pide a la coordinación que configure la clave de Gemini.");
        String consulta=pregunta==null?"":pregunta.strip();
        if(consulta.length()>1000)throw Problema.invalido("La pregunta puede tener como máximo 1000 caracteres.");
        String nombre,texto;
        if(archivo!=null && !archivo.isEmpty()) {
            nombre=Optional.ofNullable(archivo.getOriginalFilename()).orElse("documento").replace('\\','/');
            nombre=nombre.substring(nombre.lastIndexOf('/')+1).replaceAll("\\p{Cntrl}","_");
            texto=TextoMaterial.textoDocumento(nombre,archivo.getBytes());
        } else if(materialId!=null) {
            nombre=materialVisible(usuario,materialId);
            texto=String.join("\n\n",db.queryForList("SELECT texto FROM material_fragmentos WHERE material_id=? ORDER BY orden",String.class,materialId));
        } else throw Problema.invalido("Adjunta un archivo o elige un material para explicar.");
        boolean recortado=texto.length()>LIMITE_DOCUMENTO;
        if(recortado)texto=texto.substring(0,LIMITE_DOCUMENTO);
        String contenido="Documento: "+nombre+(recortado?" (solo se incluye la primera parte)":"")+"\n\n"+texto+
            "\n\nPregunta del estudiante: "+(consulta.isEmpty()?"Explícame este documento.":consulta);
        String explicacion;
        try { explicacion=ia.generar(INSTRUCCIONES_DOCUMENTO,contenido); }
        catch(ModeloIA.Falla f) { throw new Problema(f.cupo?HttpStatus.TOO_MANY_REQUESTS:HttpStatus.BAD_GATEWAY,f.getMessage()+" Inténtalo de nuevo en unos minutos."); }
        if(recortado)explicacion+="\n\n(El documento es largo: la explicación considera solo su primera parte.)";
        String registro="📄 "+nombre+(consulta.isEmpty()?"":" — "+consulta);
        if(registro.length()>1000)registro=registro.substring(0,997)+"…";
        return guardar(usuario,registro,Map.of("tipo","ARCHIVO","respuesta",explicacion,"fuentes",List.of(Map.of("titulo",nombre,"tipo","ARCHIVO"))));
    }

    private Map<String,Object> guardar(Map<String,Object> usuario,String pregunta,Map<String,Object> respuesta) {
        String fuentes;
        try { fuentes=json.writeValueAsString(respuesta.get("fuentes")); } catch(JsonProcessingException ex) { fuentes="[]"; }
        db.update("INSERT INTO asistente_mensajes(usuario_id,pregunta,respuesta,tipo,fuentes) VALUES(?,?,?,?,?)",usuario.get("id"),pregunta,respuesta.get("respuesta"),respuesta.get("tipo"),fuentes);
        var r=new LinkedHashMap<String,Object>(respuesta);r.put("pregunta",pregunta);
        return r;
    }

    /** Material publicado que la cuenta puede consultar (mismas reglas que el asistente). Devuelve su título. */
    private String materialVisible(Map<String,Object> usuario,long id) {
        String sql="SELECT m.titulo FROM materiales m WHERE m.id=? AND m.estado='PUBLICADO'";
        List<Object> args=new ArrayList<>(List.of(id));
        if("ESTUDIANTE".equals(usuario.get("rol")) || "PROFESOR_COLEGIO".equals(usuario.get("rol"))) {
            var filtro=acceso.filtroPracticas();
            sql+=" AND EXISTS(SELECT 1 FROM practicas p WHERE p.asignatura_id=m.asignatura_id AND p.periodo_id=m.periodo_id AND "+filtro.sql()+")";
            args.addAll(Arrays.asList(filtro.args()));
        }
        return db.queryForList(sql,String.class,args.toArray()).stream().findFirst()
            .orElseThrow(() -> Problema.noExiste("El material no existe, no está publicado o no corresponde a tus asignaturas."));
    }

    /** Materiales publicados que la cuenta puede pedir que se expliquen. */
    @GetMapping("/asistente/materiales") public List<Map<String,Object>> materialesDisponibles() {
        var usuario=acceso.actual();
        String sql="SELECT m.id,m.titulo,a.nombre asignatura FROM materiales m JOIN asignaturas a ON a.id=m.asignatura_id WHERE m.estado='PUBLICADO'";
        List<Object> args=new ArrayList<>();
        if("ESTUDIANTE".equals(usuario.get("rol")) || "PROFESOR_COLEGIO".equals(usuario.get("rol"))) {
            var filtro=acceso.filtroPracticas();
            sql+=" AND EXISTS(SELECT 1 FROM practicas p WHERE p.asignatura_id=m.asignatura_id AND p.periodo_id=m.periodo_id AND "+filtro.sql()+")";
            args.addAll(Arrays.asList(filtro.args()));
        }
        return db.queryForList(sql+" ORDER BY m.titulo",args.toArray());
    }

    private static final Map<String,Object> FUENTE_GUIA=Map.of("titulo","Guía de uso de Prácticas UBB","tipo","GUIA");
    private static Map<String,Object> sinRespaldo() {
        return Map.of("tipo","SIN_RESPALDO","fuentes",List.of(),"respuesta","No encontré información suficiente en el material publicado para responder con seguridad. "+
            "Prueba con otras palabras o consulta directamente a tu profesor.");
    }

    /** Preguntas que no aparecen en el material: si tratan sobre cómo usar el sistema o el asistente, se responden con la guía. */
    private Map<String,Object> sinFragmentos(Map<String,Object> usuario,String pregunta) {
        if(ia.disponible()) {
            try {
                String respuesta=ia.generar(INSTRUCCIONES_GENERAL,GUIA_SISTEMA+"\nRol de quien pregunta: "+usuario.get("rol")+"\n\nPregunta: "+pregunta);
                return respuesta.startsWith(SIN_INFORMACION)?sinRespaldo():Map.of("tipo","AYUDA","respuesta",respuesta,"fuentes",List.of(FUENTE_GUIA));
            } catch(ModeloIA.Falla f) { /* sin IA disponible: se usan las respuestas fijas */ }
        }
        String fija=respuestaFija(pregunta,ia.disponible());
        return fija==null?sinRespaldo():Map.of("tipo","AYUDA","respuesta",fija,"fuentes",List.of(FUENTE_GUIA));
    }

    /** Respuestas fijas a las preguntas más comunes sobre el asistente; funcionan aunque la IA no esté disponible. */
    static String respuestaFija(String pregunta,boolean iaActiva) {
        String t=BuscadorMaterial.sinTildes(pregunta).replaceAll("[¿?¡!.,]"," ").replaceAll("\\s+"," ").strip();
        if(t.matches(".*\\b(explic|resum|lee|leer|revis|analiz)\\w*\\b.*") && t.matches(".*\\b(archivo|archivos|documento|documentos|pdf|word|docx)\\b.*"))
            return iaActiva
                ?"Sí. Pulsa el clip que está a la izquierda del cuadro de texto, elige un PDF, Word (DOCX), TXT o MD y te lo explico. Si buscas algo específico, escribe tu pregunta antes de adjuntarlo, por ejemplo: \"¿Qué tengo que entregar?\". El archivo no se guarda en el sistema."
                :"Por ahora la explicación de documentos con IA no está activada. Cuando la coordinación configure la clave de Gemini, podrás adjuntar un PDF, Word, TXT o MD con el clip del asistente.";
        if(t.matches(".*\\b(dict\\w*|voz|microfono)\\b.*"))
            return "Sí. En los campos de texto largos y aquí en el asistente hay un botón de micrófono. Púlsalo, permite el micrófono y habla: el texto se escribe solo. Puedes decir \"coma\", \"punto y seguido\" o \"punto y aparte\". Para terminar, vuelve a pulsarlo o presiona Esc. Funciona en Chrome y Edge.";
        if(t.matches(".*\\b(que puedes hacer|que sabes hacer|como funcionas|para que sirves|como te uso|como funciona el asistente|en que me puedes ayudar)\\b.*") || t.equals("ayuda"))
            return "Puedo ayudarte con esto:\n• Responder preguntas con el material publicado por tus profesores, citando la fuente.\n• Decirte tus requisitos pendientes y plazos (pregunta \"¿Qué tengo pendiente?\").\n"+
                (iaActiva?"• Explicarte un documento: pulsa el clip y adjunta un PDF, Word, TXT o MD.\n":"")+"• También puedes dictarme con el micrófono. Para ayuda de cada página, usa el botón \"? Ayuda\".";
        if(t.matches("(muchas )?gracias\\b.*") || t.matches("(ok|vale|perfecto) gracias\\b.*"))
            return "¡De nada! Si necesitas algo más, aquí estoy.";
        if(t.matches("(hola|holi|buenas|buenos dias|buenas tardes|buenas noches)\\b.*"))
            return "¡Hola! Pregúntame por requisitos, plazos o el material publicado por tus profesores. Por ejemplo: \"¿Cuál es la asistencia mínima?\" o \"¿Qué tengo pendiente?\".";
        return null;
    }

    /** S2-19: solo usa las prácticas del propio estudiante, filtradas en el servidor. */
    private Map<String,Object> plazos() {
        var f=acceso.filtroPracticas();
        var asignaturas=new HashMap<Long,String>();
        for(var p:servicio.practicas())asignaturas.put(((Number)p.get("id")).longValue(),p.get("asignatura")+" "+p.get("anio")+"-"+p.get("semestre"));
        var pendientes=servicio.requisitos(f.sql(),f.args()).stream().filter(r -> ReglasSeguimiento.pendiente(String.valueOf(r.get("estado"))))
            .sorted(Comparator.comparing(r -> r.get("fecha_limite")==null?"9999-12-31":r.get("fecha_limite").toString())).toList();
        StringBuilder texto=new StringBuilder();
        if(asignaturas.isEmpty())texto.append("Todavía no tienes prácticas registradas. Consulta con la coordinación.");
        else if(pendientes.isEmpty())texto.append("No tienes requisitos pendientes ni correcciones solicitadas. Las entregas en revisión aparecen en tu panel.");
        else {
            texto.append("Tienes ").append(pendientes.size()).append(pendientes.size()==1?" requisito pendiente:":" requisitos pendientes:");
            for(var r:pendientes) {
                texto.append("\n• ").append(r.get("nombre")).append(" (").append(asignaturas.get(((Number)r.get("practica_id")).longValue())).append(")");
                texto.append(r.get("fecha_limite")==null?" — sin fecha límite":" — plazo: "+r.get("fecha_limite"));
                if(Boolean.TRUE.equals(r.get("vencido")))texto.append(" — FUERA DE PLAZO");
                if("OBSERVADO".equals(r.get("estado")))texto.append(" — debes subir una versión corregida");
            }
        }
        return Map.of("tipo","PLAZOS","respuesta",texto.toString(),"fuentes",List.of(Map.of("titulo","Tu seguimiento de prácticas","tipo","SEGUIMIENTO")));
    }

    /** S2-18: el estudiante solo consulta material publicado de las asignaturas y períodos de sus prácticas. */
    private Map<String,Object> material(Map<String,Object> usuario,String pregunta) {
        String sql="SELECT f.material_id,m.titulo,f.orden,f.texto FROM material_fragmentos f JOIN materiales m ON m.id=f.material_id WHERE m.estado='PUBLICADO'";
        List<Object> args=new ArrayList<>();
        if("ESTUDIANTE".equals(usuario.get("rol")) || "PROFESOR_COLEGIO".equals(usuario.get("rol"))) {
            var filtro=acceso.filtroPracticas();
            sql+=" AND EXISTS(SELECT 1 FROM practicas p WHERE p.asignatura_id=m.asignatura_id AND p.periodo_id=m.periodo_id AND "+filtro.sql()+")";
            args.addAll(Arrays.asList(filtro.args()));
        }
        var fragmentos=db.query(sql,(rs,n) -> new BuscadorMaterial.Fragmento(rs.getLong(1),rs.getString(2),rs.getInt(3),rs.getString(4)),args.toArray());
        var resultados=BuscadorMaterial.buscar(pregunta,fragmentos,3);
        if(resultados.isEmpty())return sinFragmentos(usuario,pregunta);
        List<Map<String,Object>> fuentes=new ArrayList<>();
        for(int i=0;i<resultados.size();i++) {
            var r=resultados.get(i);
            fuentes.add(Map.of("numero",i+1,"titulo",r.fragmento().titulo(),"materialId",r.fragmento().materialId(),"fragmento",r.fragmento().orden(),"tipo","MATERIAL"));
        }
        String nota="";
        if(ia.disponible()) {
            // La IA redacta la respuesta usando solo los fragmentos encontrados, y debe citarlos.
            var contexto=new StringBuilder("Fragmentos del material publicado:\n");
            for(int i=0;i<resultados.size();i++)
                contexto.append("\n[").append(i+1).append("] (").append(resultados.get(i).fragmento().titulo()).append(")\n").append(resultados.get(i).fragmento().texto()).append('\n');
            contexto.append("\nPregunta: ").append(pregunta);
            try {
                String respuesta=ia.generar(INSTRUCCIONES_MATERIAL,contexto.toString());
                if(respuesta.startsWith(SIN_INFORMACION))
                    return Map.of("tipo","SIN_RESPALDO","fuentes",List.of(),"respuesta",SIN_INFORMACION+" Prueba con otras palabras o consulta directamente a tu profesor.");
                return Map.of("tipo","IA","respuesta",respuesta,"fuentes",fuentes);
            } catch(ModeloIA.Falla f) {
                nota="\n\n("+f.getMessage()+" Te muestro los fragmentos más relevantes del material.)";
            }
        }
        // Sin IA (o si falla): respuesta con los fragmentos más relevantes.
        StringBuilder texto=new StringBuilder("Según el material publicado por tus profesores:");
        for(int i=0;i<resultados.size();i++)texto.append("\n\n[").append(i+1).append("] ").append(resultados.get(i).extracto());
        texto.append("\n\nRevisa la fuente completa si necesitas más detalle.").append(nota);
        return Map.of("tipo","MATERIAL","respuesta",texto.toString(),"fuentes",fuentes);
    }
}
