package cl.ubb.practicas;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.*;

/** S2-16: extrae el texto de un material y lo divide en fragmentos que el asistente puede citar. */
public final class TextoMaterial {
    private TextoMaterial() { }
    public static final int MAXIMO_FRAGMENTO=900;

    public record Extraccion(String tipo,String texto) { }

    public static Extraccion extraer(String nombre,byte[] bytes) {
        String lower=nombre.toLowerCase(Locale.ROOT);
        String tipo,texto;
        if(lower.endsWith(".pdf")) {
            if(bytes.length<5 || !new String(bytes,0,5,StandardCharsets.US_ASCII).equals("%PDF-"))throw Problema.invalido("El archivo no tiene una cabecera PDF válida.");
            try(var pdf=Loader.loadPDF(bytes)) {
                if(pdf.isEncrypted())throw Problema.invalido("El PDF está protegido; súbelo sin contraseña.");
                texto=new PDFTextStripper().getText(pdf);
            } catch(IOException ex) { throw Problema.invalido("No se pudo leer el PDF. Comprueba que no esté dañado."); }
            tipo="application/pdf";
        } else if(lower.endsWith(".txt") || lower.endsWith(".md")) {
            try { texto=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString(); }
            catch(CharacterCodingException ex) { throw Problema.invalido("El archivo de texto debe estar guardado en UTF-8."); }
            tipo=lower.endsWith(".md")?"text/markdown":"text/plain";
        } else throw Problema.invalido("Formatos permitidos para el material: PDF, TXT y MD.");
        texto=normalizar(texto);
        if(texto.replaceAll("\\s","").length()<30)
            throw Problema.invalido("No se encontró texto suficiente. Si es un PDF escaneado, súbelo en una versión con texto seleccionable.");
        return new Extraccion(tipo,texto);
    }

    /** Texto de un documento para que el asistente lo explique: PDF, TXT, MD o Word (DOCX). */
    public static String textoDocumento(String nombre,byte[] bytes) {
        if(!nombre.toLowerCase(Locale.ROOT).endsWith(".docx")) {
            String lower=nombre.toLowerCase(Locale.ROOT);
            if(!(lower.endsWith(".pdf") || lower.endsWith(".txt") || lower.endsWith(".md")))
                throw Problema.invalido("Formatos que el asistente puede explicar: PDF, DOCX, TXT y MD.");
            return extraer(nombre,bytes).texto();
        }
        String texto=null;
        try(var zip=new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(bytes))) {
            for(var entrada=zip.getNextEntry();entrada!=null;entrada=zip.getNextEntry()) {
                if(!"word/document.xml".equals(entrada.getName()))continue;
                // Límite de lectura para evitar archivos comprimidos maliciosos.
                String xml=new String(zip.readNBytes(20_000_000),StandardCharsets.UTF_8);
                texto=xml.replace("</w:p>","\n").replace("<w:tab/>","\t").replace("<w:br/>","\n").replaceAll("<[^>]+>","")
                    .replace("&lt;","<").replace("&gt;",">").replace("&quot;","\"").replace("&apos;","'").replace("&amp;","&");
                break;
            }
        } catch(IOException ex) { texto=null; }
        if(texto==null)throw Problema.invalido("No se pudo leer el documento Word. Guárdalo como PDF e inténtalo de nuevo.");
        texto=normalizar(texto);
        if(texto.replaceAll("\\s","").length()<30)throw Problema.invalido("El documento casi no tiene texto para explicar.");
        return texto;
    }

    static String normalizar(String texto) {
        return texto.replace("\r\n","\n").replace('\r','\n').replaceAll("[\\p{Cntrl}&&[^\n\t]]"," ")
            .replaceAll("[ \t]+"," ").replaceAll(" *\n *","\n").replaceAll("\n{3,}","\n\n").strip();
    }

    /** Agrupa párrafos hasta cerca de MAXIMO_FRAGMENTO caracteres; corta los párrafos largos entre palabras. */
    public static List<String> fragmentar(String texto) {
        List<String> fragmentos=new ArrayList<>();
        StringBuilder actual=new StringBuilder();
        for(String parrafo:texto.split("\n\\s*\n")) {
            for(String pieza:dividir(parrafo.replace('\n',' ').strip())) {
                if(pieza.isEmpty())continue;
                if(actual.length()>0 && actual.length()+pieza.length()+1>MAXIMO_FRAGMENTO){fragmentos.add(actual.toString());actual.setLength(0);}
                if(actual.length()>0)actual.append('\n');
                actual.append(pieza);
            }
        }
        if(actual.length()>0)fragmentos.add(actual.toString());
        return fragmentos;
    }
    private static List<String> dividir(String parrafo) {
        if(parrafo.length()<=MAXIMO_FRAGMENTO)return List.of(parrafo);
        List<String> piezas=new ArrayList<>();StringBuilder b=new StringBuilder();
        for(String palabra:parrafo.split(" ")) {
            if(b.length()>0 && b.length()+palabra.length()+1>MAXIMO_FRAGMENTO){piezas.add(b.toString());b.setLength(0);}
            if(b.length()>0)b.append(' ');
            b.append(palabra.length()>MAXIMO_FRAGMENTO?palabra.substring(0,MAXIMO_FRAGMENTO):palabra);
        }
        if(b.length()>0)piezas.add(b.toString());
        return piezas;
    }
}
