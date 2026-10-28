package cl.ubb.practicas;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TextoMaterialTest {
    @Test void fragmentaPorParrafosSinSuperarElMaximo() {
        String largo="palabra ".repeat(400);
        var fragmentos=TextoMaterial.fragmentar("Primer párrafo breve.\n\nSegundo párrafo breve.\n\n"+largo);
        assertTrue(fragmentos.get(0).startsWith("Primer párrafo breve."));
        assertTrue(fragmentos.stream().allMatch(f -> f.length()<=TextoMaterial.MAXIMO_FRAGMENTO));
        assertTrue(fragmentos.size()>=3);
    }
    @Test void normalizaEspaciosYRechazaFormatosDesconocidos() {
        var r=TextoMaterial.extraer("apuntes.txt","Texto   con\r\n\r\n\r\n\r\nespacios de sobra y suficiente contenido.".getBytes(StandardCharsets.UTF_8));
        assertEquals("Texto con\n\nespacios de sobra y suficiente contenido.",r.texto());
        assertThrows(Problema.class,() -> TextoMaterial.extraer("apuntes.doc",new byte[]{1,2,3}));
        assertThrows(Problema.class,() -> TextoMaterial.extraer("latin1.txt",new byte[]{(byte)0xE1,'a','b','c'}));
    }
}
