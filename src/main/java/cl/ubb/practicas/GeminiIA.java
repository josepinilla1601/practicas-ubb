package cl.ubb.practicas;

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
import java.util.*;

/**
 * Conexión con la API de Google Gemini. La clave se lee de GEMINI_API_KEY (archivo .env o variable de entorno)
 * y nunca se envía a la interfaz. Sin clave, el asistente sigue funcionando con la búsqueda por palabras clave.
 */
@Service
public class GeminiIA implements ModeloIA {
    private static final Logger log=LoggerFactory.getLogger(GeminiIA.class);
    private final String clave;
    private final String modelo;
    private final ObjectMapper json;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public GeminiIA(@Value("${GEMINI_API_KEY:}") String clave,@Value("${GEMINI_MODEL:gemini-flash-lite-latest}") String modelo,ObjectMapper json) {
        this.clave=clave==null?"":clave.strip();
        this.modelo=modelo==null || modelo.isBlank()?"gemini-flash-lite-latest":modelo.strip();
        this.json=json;
    }

    @Override public boolean disponible() { return !clave.isEmpty(); }

    @Override public String generar(String instrucciones,String contenido) {
        if(!disponible())throw new Falla("El asistente con IA no está configurado.",false);
        var cuerpo=Map.of(
            "systemInstruction",Map.of("parts",List.of(Map.of("text",instrucciones))),
            "contents",List.of(Map.of("role","user","parts",List.of(Map.of("text",contenido)))),
            "generationConfig",Map.of("temperature",0.3,"maxOutputTokens",4096));
        try {
            var url=URI.create("https://generativelanguage.googleapis.com/v1beta/models/"+URLEncoder.encode(modelo,StandardCharsets.UTF_8)+":generateContent");
            var pedido=HttpRequest.newBuilder(url).timeout(Duration.ofSeconds(30))
                .header("Content-Type","application/json").header("x-goog-api-key",clave)
                .POST(HttpRequest.BodyPublishers.ofByteArray(json.writeValueAsBytes(cuerpo))).build();
            var respuesta=http.send(pedido,HttpResponse.BodyHandlers.ofString());
            if(respuesta.statusCode()==429)throw new Falla("La IA alcanzó su límite de uso gratuito por ahora.",true);
            if(respuesta.statusCode()>=300) {
                log.warn("Gemini respondió {}: {}",respuesta.statusCode(),respuesta.body().substring(0,Math.min(400,respuesta.body().length())));
                throw new Falla("El servicio de IA no respondió correctamente.",false);
            }
            var texto=new StringBuilder();
            for(var parte:json.readTree(respuesta.body()).path("candidates").path(0).path("content").path("parts"))texto.append(parte.path("text").asText(""));
            if(texto.toString().isBlank())throw new Falla("La IA no entregó una respuesta. Intenta con otras palabras.",false);
            return texto.toString().strip();
        } catch(IOException ex) {
            log.warn("No se pudo conectar con Gemini: {}",ex.getMessage());
            throw new Falla("No se pudo conectar con el servicio de IA.",false);
        } catch(InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new Falla("La consulta a la IA se interrumpió.",false);
        }
    }
}
