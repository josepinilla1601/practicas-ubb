package cl.ubb.practicas;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Conexión con la API de Google Gemini. La clave se lee de GEMINI_API_KEY (archivo .env o variable de entorno)
 * y nunca se envía a la interfaz. Sin clave, el asistente sigue funcionando con la búsqueda por palabras clave.
 * La búsqueda en internet (Google Search) se usa si el plan de la clave la incluye; si no, se responde sin ella.
 */
@Service
public class GeminiIA implements ModeloIA {
    private static final Logger log=LoggerFactory.getLogger(GeminiIA.class);
    /** Si la búsqueda en internet no está disponible, se vuelve a intentar después de este tiempo. */
    private static final Duration PAUSA_BUSQUEDA=Duration.ofHours(1);
    private final String clave;
    private final String modelo;
    private final boolean busqueda;
    private final ObjectMapper json;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private volatile Instant busquedaPausadaHasta=Instant.MIN;

    public GeminiIA(@Value("${GEMINI_API_KEY:}") String clave,@Value("${GEMINI_MODEL:gemini-flash-lite-latest}") String modelo,
                    @Value("${GEMINI_BUSQUEDA_WEB:true}") boolean busqueda,ObjectMapper json) {
        this.clave=clave==null?"":clave.strip();
        this.modelo=modelo==null || modelo.isBlank()?"gemini-flash-lite-latest":modelo.strip();
        this.busqueda=busqueda;
        this.json=json;
    }

    @Override public boolean disponible() { return !clave.isEmpty(); }

    @Override public String generar(String instrucciones,String contenido) {
        return texto(llamar(instrucciones,contenido,false));
    }

    @Override public Resultado generarConInternet(String instrucciones,String contenido) {
        if(busqueda && Instant.now().isAfter(busquedaPausadaHasta)) {
            try {
                var candidato=llamar(instrucciones,contenido,true);
                return new Resultado(texto(candidato),fuentesWeb(candidato));
            } catch(Falla f) {
                if(!f.cupo)throw f;
                // El plan gratuito puede no incluir la búsqueda: se responde sin internet y se reintenta más tarde.
                busquedaPausadaHasta=Instant.now().plus(PAUSA_BUSQUEDA);
                log.info("La búsqueda en internet de Gemini no está disponible con esta clave; se responde sin ella.");
            }
        }
        return new Resultado(generar(instrucciones,contenido),List.of());
    }

    private JsonNode llamar(String instrucciones,String contenido,boolean conBusqueda) {
        if(!disponible())throw new Falla("El asistente con IA no está configurado.",false);
        var cuerpo=new LinkedHashMap<String,Object>();
        cuerpo.put("systemInstruction",Map.of("parts",List.of(Map.of("text",instrucciones))));
        cuerpo.put("contents",List.of(Map.of("role","user","parts",List.of(Map.of("text",contenido)))));
        if(conBusqueda)cuerpo.put("tools",List.of(Map.of("google_search",Map.of())));
        cuerpo.put("generationConfig",Map.of("temperature",0.3,"maxOutputTokens",4096));
        try {
            var url=URI.create("https://generativelanguage.googleapis.com/v1beta/models/"+URLEncoder.encode(modelo,StandardCharsets.UTF_8)+":generateContent");
            var pedido=HttpRequest.newBuilder(url).timeout(Duration.ofSeconds(conBusqueda?45:30))
                .header("Content-Type","application/json").header("x-goog-api-key",clave)
                .POST(HttpRequest.BodyPublishers.ofByteArray(json.writeValueAsBytes(cuerpo))).build();
            var respuesta=http.send(pedido,HttpResponse.BodyHandlers.ofString());
            if(respuesta.statusCode()==429)throw new Falla("La IA alcanzó su límite de uso gratuito por ahora.",true);
            if(respuesta.statusCode()>=300) {
                log.warn("Gemini respondió {}: {}",respuesta.statusCode(),respuesta.body().substring(0,Math.min(400,respuesta.body().length())));
                throw new Falla("El servicio de IA no respondió correctamente.",false);
            }
            return json.readTree(respuesta.body()).path("candidates").path(0);
        } catch(IOException ex) {
            log.warn("No se pudo conectar con Gemini: {}",ex.getMessage());
            throw new Falla("No se pudo conectar con el servicio de IA.",false);
        } catch(InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new Falla("La consulta a la IA se interrumpió.",false);
        }
    }

    private static String texto(JsonNode candidato) {
        var texto=new StringBuilder();
        for(var parte:candidato.path("content").path("parts"))texto.append(parte.path("text").asText(""));
        if(texto.toString().isBlank())throw new Falla("La IA no entregó una respuesta. Intenta con otras palabras.",false);
        return texto.toString().strip();
    }

    /** Páginas que la IA consultó en internet (sin repetir). */
    private static List<FuenteWeb> fuentesWeb(JsonNode candidato) {
        var fuentes=new LinkedHashMap<String,FuenteWeb>();
        for(var trozo:candidato.path("groundingMetadata").path("groundingChunks")) {
            var web=trozo.path("web");
            String url=web.path("uri").asText(""),titulo=web.path("title").asText("");
            if(url.startsWith("https://"))fuentes.putIfAbsent(url,new FuenteWeb(titulo.isBlank()?"Página web":titulo,url));
        }
        return List.copyOf(fuentes.values());
    }
}
