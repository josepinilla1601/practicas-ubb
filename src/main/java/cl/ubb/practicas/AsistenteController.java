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
 * Retroalimentación de trabajos: la IA primero hace preguntas de reflexión y luego retroalimenta el archivo subido.
 * Solo responde temas de la práctica, la asignatura, la matemática y la plataforma.
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
        Usa SOLO la información de los fragmentos numerados que se entregan. Después de cada dato indica entre corchetes el número del fragmento de donde lo sacaste, por ejemplo [1].
        Usa solo números de fragmentos que existan; los números de artículos o secciones del documento no son fragmentos.
        Si los fragmentos no permiten responder la pregunta, responde exactamente: "%s"
        No inventes datos, fechas ni porcentajes. No uses formato Markdown (ni asteriscos ni #); para listas usa "• ".
        """.formatted(SIN_INFORMACION);
    /** Respuesta de la IA cuando el documento o la pregunta no tienen relación con la práctica. */
    static final String FUERA_DE_TEMA="[FUERA_DE_TEMA]";
    /** Título del apartado donde la IA separa lo que no viene del documento. */
    static final String APARTADO_INTERNET="Información complementada desde internet:";
    static final String APARTADO_SIN_FUENTE="Información complementaria de la IA (no viene de tu documento ni de una fuente verificada; confírmala con tu profesor):";
    private static final String TEMAS_PERMITIDOS="""
        Temas permitidos: la práctica pedagógica y sus documentos (planificaciones, informes, reflexiones, bitácoras, evaluaciones),
        la asignatura y la didáctica de la matemática escolar, la matemática en sí, el currículum y el Marco para la Buena Enseñanza,
        y el uso de la plataforma Prácticas UBB. Cualquier otro tema (noticias, deportes, entretenimiento, tareas de otras áreas,
        datos curiosos, programación, consejos personales, etc.) está fuera de tema.
        """;
    private static final String REGLA_INTERNET="""
        Tu base principal es el documento del estudiante. Si el documento no basta y necesitas información externa (por ejemplo,
        el currículum nacional, las Bases Curriculares o el Marco para la Buena Enseñanza), puedes buscar en internet.
        Todo dato que NO venga del documento va solo en un apartado final que empiece exactamente con "%s",
        donde explicas qué agregaste y nombras la fuente (institución o sitio). Si no usaste información externa, no escribas ese apartado.
        """.formatted(APARTADO_INTERNET);
    /** Primera etapa: antes de dar retroalimentación, la IA hace preguntas para que el estudiante reflexione sobre su trabajo. */
    private static final String INSTRUCCIONES_REFLEXION="""
        Eres un tutor de práctica pedagógica de Pedagogía en Educación Matemática (Universidad del Bío-Bío).
        Un estudiante subió un trabajo suyo para recibir retroalimentación. TODAVÍA NO des retroalimentación ni expliques el documento.
        Primero escribe una oración que diga qué tipo de documento identificas (por ejemplo, una planificación de clase sobre fracciones).
        Luego escribe exactamente tres preguntas de reflexión, numeradas 1., 2. y 3., que lo hagan pensar sobre su propio trabajo:
        para qué sirve, qué buscaba lograr con sus alumnos, por qué tomó ciertas decisiones y qué podría mejorar.
        Las preguntas deben ser abiertas, breves y referirse a partes concretas del documento. Si el estudiante hizo una pregunta, oriéntalas hacia ella.
        Termina invitándolo a responder para darle una retroalimentación más precisa.
        %s
        Si el documento no tiene relación con estos temas, responde exactamente: "%s"
        Español de Chile, tono cercano. No saludes. No uses Markdown (ni asteriscos ni #).
        """.formatted(TEMAS_PERMITIDOS,FUERA_DE_TEMA);
    /** Segunda etapa: retroalimentación concreta, considerando las reflexiones del estudiante. */
    private static final String INSTRUCCIONES_RETROALIMENTACION="""
        Eres un tutor de práctica pedagógica de Pedagogía en Educación Matemática (Universidad del Bío-Bío).
        Da retroalimentación formativa y concreta al trabajo del estudiante, en español de Chile, con tono cercano y respetuoso. No saludes.
        Usa este orden, con estos títulos en líneas separadas:
        Sobre tus reflexiones: comenta en una o dos oraciones lo que respondió el estudiante (omite este apartado si no respondió).
        Fortalezas: lista con "• ".
        Aspectos por mejorar: lista con "• ", indicando la parte del documento a la que te refieres.
        Sugerencias concretas: lista con "• " de acciones que pueda aplicar.
        Si el estudiante hizo una pregunta, respóndela dentro de la retroalimentación.
        %s
        %s
        Si el documento no tiene relación con estos temas, responde exactamente: "%s"
        No inventes datos. No uses Markdown (ni asteriscos ni #). Máximo unas 350 palabras.
        """.formatted(REGLA_INTERNET,TEMAS_PERMITIDOS,FUERA_DE_TEMA);

    /** Guía del sistema: la IA la usa para responder preguntas sobre cómo usar la plataforma y el propio asistente. */
    private static final String GUIA_SISTEMA="""
        Guía de uso de Prácticas UBB (plataforma de seguimiento de prácticas pedagógicas):
        - Mi panel (estudiantes): muestra requisitos, plazos, atrasos y estados (Pendiente, En revisión, Corrección solicitada, Aprobado). Los requisitos están ordenados por urgencia.
        - Entregar un documento: botón "Entregar documento" o "Entregar" en el requisito; se sube un PDF, DOCX o XLSX de hasta 10 MB. Si te equivocas, aparece "Deshacer" o puedes usar "Eliminar entrega" mientras no esté revisada.
        - Corrección solicitada: el profesor dejó un comentario; se sube una versión corregida en el mismo requisito.
        - Profesores UBB: revisan entregas en el seguimiento de cada práctica (aprobar o solicitar corrección, con confirmación y opción de deshacer).
        - Profesores de colegio: registran fortalezas y aspectos por mejorar de sus practicantes.
        - Asistente: responde preguntas con el material publicado por los profesores y cita la fuente. Si eres estudiante, también te dice tus requisitos pendientes y plazos (pregunta "¿Qué tengo pendiente?").
        - Retroalimentación de un trabajo: en el asistente flotante, pulsa el clip (a la izquierda del cuadro de texto) y elige tu PDF, Word (DOCX), TXT o MD; en la página "Asistente" está la sección "Retroalimentación de tu trabajo". Primero el asistente te hace tres preguntas para que reflexiones sobre tu trabajo; cuando las respondas (o si eliges omitirlas), te da la retroalimentación: fortalezas, aspectos por mejorar y sugerencias. Si usa información de internet, lo indica con su fuente. El archivo no se guarda.
        - Apoyo matemático: el asistente resuelve y explica paso a paso ejercicios de matemática y da ideas para enseñar un contenido en el aula.
        - Dictado por voz: en los campos de texto largos y en el asistente hay un micrófono; púlsalo, permite el micrófono y habla. Puedes decir "coma", "punto y seguido" o "punto y aparte". Funciona en Chrome y Edge.
        - Ayuda: el botón "? Ayuda" (arriba) explica cada página paso a paso; la tecla ? también la abre. La guía de bienvenida está en tu nombre, arriba a la derecha.
        - Contraseña olvidada: la coordinación de prácticas asigna una nueva; luego se cambia en tu nombre → Cambiar contraseña.
        """;
    private static final String INSTRUCCIONES_GENERAL="""
        Eres el asistente del sistema Prácticas UBB, para estudiantes y profesores de Pedagogía en Educación Matemática (Universidad del Bío-Bío).
        Responde en español de Chile, con tono cercano. No uses Markdown (ni asteriscos ni #); para listas usa "• ". Saluda solo si el usuario saludó.
        Clasifica la pregunta y empieza tu respuesta con la etiqueta que corresponda:
        [AYUDA] Saludos, agradecimientos o cómo usar la plataforma o el asistente. Responde SOLO con la guía entregada, en máximo cinco oraciones.
          Si pregunta qué puedes hacer, menciona todas las funciones de la guía (material con fuentes, pendientes y plazos, retroalimentación de trabajos, apoyo matemático y dictado).
        [MATEMATICA] Ejercicios o conceptos de matemática, o cómo enseñar un contenido matemático en el aula (didáctica, actividades, errores frecuentes de los alumnos).
          Resuelve paso a paso y explica el porqué de cada paso, de forma clara y verificable. Escribe las expresiones en texto simple (por ejemplo x^2, raíz(2), 3/4).
          Si es sobre enseñanza, da ideas concretas para el aula escolar chilena. Máximo unas 250 palabras.
        [PRACTICA] Dudas pedagógicas generales de la práctica: cómo planificar una clase, gestionar el aula, evaluar, reflexionar sobre la propia práctica,
          el currículum o el Marco para la Buena Enseñanza. Responde con ideas concretas, en máximo unas 200 palabras.
        [FUERA_DE_TEMA] Cualquier pregunta que no tenga relación con la práctica, la asignatura, la matemática ni la plataforma. Escribe solo la etiqueta, sin responder la pregunta.
        En [MATEMATICA] y [PRACTICA] puedes buscar en internet si lo necesitas; si usas información de internet, ponla en un apartado final
        que empiece exactamente con "%s" y nombra la fuente. Si no la usaste, no escribas ese apartado.
        Si pregunta por reglamentos, requisitos académicos, fechas, porcentajes, notas u otra información institucional de las prácticas,
        responde exactamente: "%s" (esa información solo puede venir del material publicado por la universidad, nunca de internet).
        No inventes funciones de la plataforma ni información institucional.
        """.formatted(APARTADO_INTERNET,SIN_INFORMACION);

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

    /**
     * Retroalimentación con IA de un trabajo del estudiante (PDF, DOCX, TXT o MD). El archivo no se guarda.
     * Etapa PREGUNTAS: la IA hace tres preguntas de reflexión, sin dar todavía la retroalimentación.
     * Etapa RETROALIMENTACION: la IA da la retroalimentación considerando las respuestas del estudiante.
     */
    @PostMapping(value="/asistente/explicar",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String,Object> explicar(@RequestPart(value="archivo",required=false) MultipartFile archivo,
        @RequestParam(required=false) String pregunta,@RequestParam(defaultValue="PREGUNTAS") String etapa,
        @RequestParam(required=false) String respuestas) throws IOException {
        var usuario=acceso.actual();
        if(!ia.disponible())throw new Problema(HttpStatus.SERVICE_UNAVAILABLE,"La retroalimentación de documentos con IA todavía no está activada. Pide a la coordinación que configure la clave de Gemini.");
        if(!etapa.equals("PREGUNTAS") && !etapa.equals("RETROALIMENTACION"))throw Problema.invalido("Etapa no válida.");
        String consulta=pregunta==null?"":pregunta.strip(),reflexion=respuestas==null?"":respuestas.strip();
        if(consulta.length()>1000)throw Problema.invalido("La pregunta puede tener como máximo 1000 caracteres.");
        if(reflexion.length()>3000)throw Problema.invalido("Tus respuestas pueden tener como máximo 3000 caracteres.");
        if(archivo==null || archivo.isEmpty())throw Problema.invalido("Adjunta el trabajo que quieres que revise.");
        String nombre=Optional.ofNullable(archivo.getOriginalFilename()).orElse("documento").replace('\\','/');
        nombre=nombre.substring(nombre.lastIndexOf('/')+1).replaceAll("\\p{Cntrl}","_");
        String texto=TextoMaterial.textoDocumento(nombre,archivo.getBytes());
        boolean recortado=texto.length()>LIMITE_DOCUMENTO;
        if(recortado)texto=texto.substring(0,LIMITE_DOCUMENTO);
        var contenido=new StringBuilder("Trabajo del estudiante: ").append(nombre).append(recortado?" (solo se incluye la primera parte)":"").append("\n\n").append(texto);
        if(!consulta.isEmpty())contenido.append("\n\nPregunta del estudiante: ").append(consulta);
        boolean preguntas=etapa.equals("PREGUNTAS");
        if(!preguntas)contenido.append("\n\nRespuestas del estudiante a las preguntas de reflexión: ").append(reflexion.isEmpty()?"(prefirió no responderlas)":reflexion);
        var fuentes=new ArrayList<Map<String,Object>>(List.of(Map.of("titulo",nombre,"tipo","ARCHIVO")));
        String salida;
        try {
            if(preguntas)salida=ia.generar(INSTRUCCIONES_REFLEXION,contenido.toString()).strip();
            else {
                var r=ia.generarConInternet(INSTRUCCIONES_RETROALIMENTACION,contenido.toString());
                salida=marcarInternet(r.texto().strip(),r.web());
                r.web().forEach(w -> fuentes.add(Map.of("titulo",w.titulo(),"url",w.url(),"tipo","WEB")));
            }
        } catch(ModeloIA.Falla f) { throw new Problema(f.cupo?HttpStatus.TOO_MANY_REQUESTS:HttpStatus.BAD_GATEWAY,f.getMessage()+" Inténtalo de nuevo en unos minutos."); }
        String registro="📄 "+nombre+(preguntas?(consulta.isEmpty()?"":" — "+consulta):" · Mis reflexiones: "+(reflexion.isEmpty()?"(las omití)":reflexion));
        if(registro.length()>1000)registro=registro.substring(0,997)+"…";
        if(salida.startsWith(FUERA_DE_TEMA))
            return guardar(usuario,registro,Map.of("tipo","FUERA_DE_TEMA","fuentes",List.of(),"respuesta",
                "Este documento no parece estar relacionado con tu práctica, la asignatura o la matemática, así que no puedo revisarlo. Sube un trabajo de tu práctica, como una planificación, un informe o una reflexión."));
        if(recortado)salida+="\n\n(El documento es largo: solo consideré su primera parte.)";
        return guardar(usuario,registro,Map.of("tipo",preguntas?"REFLEXION":"ARCHIVO","respuesta",salida,"fuentes",fuentes));
    }

    /**
     * Deja claro qué parte de la respuesta no viene del documento ni del material: si la IA buscó en internet,
     * debe existir el apartado de internet; si no buscó, ese apartado se presenta como información sin fuente verificada.
     */
    static String marcarInternet(String texto,List<ModeloIA.FuenteWeb> web) {
        var apartado=java.util.regex.Pattern.compile("(?im)^\\s*informaci[oó]n complementada desde internet\\s*:?").matcher(texto);
        boolean tiene=apartado.find();
        if(web.isEmpty())return tiene?texto.substring(0,apartado.start())+(apartado.start()>0?"\n":"")+APARTADO_SIN_FUENTE+texto.substring(apartado.end()):texto;
        if(!tiene)texto+="\n\nParte de esta respuesta se complementó con información de internet.";
        var lista=new StringBuilder("\nFuentes de internet:");
        web.forEach(w -> lista.append("\n• ").append(w.titulo()));
        return texto+lista;
    }

    private Map<String,Object> guardar(Map<String,Object> usuario,String pregunta,Map<String,Object> respuesta) {
        String fuentes;
        try { fuentes=json.writeValueAsString(respuesta.get("fuentes")); } catch(JsonProcessingException ex) { fuentes="[]"; }
        db.update("INSERT INTO asistente_mensajes(usuario_id,pregunta,respuesta,tipo,fuentes) VALUES(?,?,?,?,?)",usuario.get("id"),pregunta,respuesta.get("respuesta"),respuesta.get("tipo"),fuentes);
        var r=new LinkedHashMap<String,Object>(respuesta);r.put("pregunta",pregunta);
        return r;
    }

    /** Quita las citas a fuentes que no existen (por ejemplo, la IA confunde "Artículo 3" con la fuente [3]). */
    static String citasValidas(String texto,int fuentes) {
        return java.util.regex.Pattern.compile(" ?\\[(\\d+)\\]").matcher(texto)
            .replaceAll(m -> { int n=Integer.parseInt(m.group(1));return n>=1 && n<=fuentes?java.util.regex.Matcher.quoteReplacement(m.group()):""; });
    }

    private static final Map<String,Object> FUENTE_GUIA=Map.of("titulo","Guía de uso de Prácticas UBB","tipo","GUIA");
    private static Map<String,Object> fueraDeTema() {
        return Map.of("tipo","FUERA_DE_TEMA","fuentes",List.of(),"respuesta","Solo puedo ayudarte con temas de tu práctica: tus trabajos y documentos, la asignatura, "+
            "la matemática y su enseñanza, y el uso de la plataforma. Esa pregunta no tiene relación con eso, así que prefiero no responderla. ¿Hay algo de tu práctica en que te pueda ayudar?");
    }
    private static Map<String,Object> sinRespaldo() {
        return Map.of("tipo","SIN_RESPALDO","fuentes",List.of(),"respuesta","No encontré información suficiente en el material publicado para responder con seguridad. "+
            "Prueba con otras palabras o consulta directamente a tu profesor.");
    }

    /** Preguntas que no aparecen en el material: si tratan sobre cómo usar el sistema o el asistente, se responden con la guía. */
    private Map<String,Object> sinFragmentos(Map<String,Object> usuario,String pregunta) {
        if(ia.disponible()) {
            try {
                var r=ia.generarConInternet(INSTRUCCIONES_GENERAL,GUIA_SISTEMA+"\nRol de quien pregunta: "+usuario.get("rol")+"\n\nPregunta: "+pregunta);
                String respuesta=r.texto().strip();
                if(respuesta.startsWith(FUERA_DE_TEMA))return fueraDeTema();
                List<Map<String,Object>> web=r.web().stream().<Map<String,Object>>map(w -> Map.of("titulo",w.titulo(),"url",w.url(),"tipo","WEB")).toList();
                if(respuesta.startsWith("[MATEMATICA]"))
                    return Map.of("tipo","MATEMATICA","respuesta",marcarInternet(respuesta.substring(12).strip(),r.web())+"\n\nRespuesta generada con IA: revisa los pasos antes de usarla en clases o en una evaluación.","fuentes",web);
                if(respuesta.startsWith("[PRACTICA]"))
                    return Map.of("tipo","PRACTICA","respuesta",marcarInternet(respuesta.substring(10).strip(),r.web())+"\n\nRespuesta generada con IA: compárala con lo que indique tu profesor.","fuentes",web);
                if(respuesta.startsWith("[AYUDA]"))respuesta=respuesta.substring(7).strip();
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
                ?"Sí. Pulsa el clip que está a la izquierda del cuadro de texto y elige tu trabajo (PDF, Word, TXT o MD). Primero te haré tres preguntas para que reflexiones sobre él y, cuando las respondas, te daré la retroalimentación. Si buscas algo específico, escríbelo antes de adjuntarlo. El archivo no se guarda en el sistema."
                :"Por ahora la retroalimentación de documentos con IA no está activada. Cuando la coordinación configure la clave de Gemini, podrás adjuntar tu trabajo (PDF, Word, TXT o MD) con el clip del asistente.";
        if(t.matches(".*\\b(dict\\w*|voz|microfono)\\b.*"))
            return "Sí. En los campos de texto largos y aquí en el asistente hay un botón de micrófono. Púlsalo, permite el micrófono y habla: el texto se escribe solo. Puedes decir \"coma\", \"punto y seguido\" o \"punto y aparte\". Para terminar, vuelve a pulsarlo o presiona Esc. Funciona en Chrome y Edge.";
        if(t.matches(".*\\b(que puedes hacer|que sabes hacer|como funcionas|para que sirves|como te uso|como funciona el asistente|en que me puedes ayudar)\\b.*") || t.equals("ayuda"))
            return "Puedo ayudarte con esto:\n• Responder preguntas con el material publicado por tus profesores, citando la fuente.\n• Decirte tus requisitos pendientes y plazos (pregunta \"¿Qué tengo pendiente?\").\n"+
                (iaActiva?"• Darte retroalimentación de un trabajo tuyo: pulsa el clip y adjunta un PDF, Word, TXT o MD. Antes te haré preguntas para que reflexiones.\n• Resolver y explicar ejercicios de matemática, o darte ideas para enseñar un contenido.\n":"")+"• También puedes dictarme con el micrófono. Para ayuda de cada página, usa el botón \"? Ayuda\".";
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
        for(var p:servicio.practicasTodas())asignaturas.put(((Number)p.get("id")).longValue(),p.get("asignatura")+" "+p.get("anio")+"-"+p.get("semestre"));
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
                // El material no responde la pregunta: puede ser de matemática o sobre la plataforma.
                if(respuesta.startsWith(SIN_INFORMACION))return sinFragmentos(usuario,pregunta);
                return Map.of("tipo","IA","respuesta",citasValidas(respuesta,fuentes.size()),"fuentes",fuentes);
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
