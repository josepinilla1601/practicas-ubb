package cl.ubb.practicas;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BuscadorMaterialTest {
    @Test void normalizaTildesPluralesYPalabrasVacias() {
        assertEquals(List.of("asistencia","minima","practica"),BuscadorMaterial.palabras("¿Cuál es la asistencia MÍNIMA de las prácticas?"));
    }
    @Test void ordenaPorRelevanciaYExigeCoberturaSuficiente() {
        var a=new BuscadorMaterial.Fragmento(1,"Reglamento",1,"La asistencia mínima es del 90 por ciento. Se controla semanalmente.");
        var b=new BuscadorMaterial.Fragmento(2,"Calendario",1,"La asistencia a talleres es voluntaria.");
        var r=BuscadorMaterial.buscar("asistencia mínima",List.of(b,a),3);
        assertEquals(1,r.size());
        assertEquals(1,r.get(0).fragmento().materialId());
        assertTrue(r.get(0).extracto().startsWith("La asistencia mínima"));
        assertTrue(BuscadorMaterial.buscar("campeonato de fútbol",List.of(a,b),3).isEmpty());
    }
    @Test void detectaPreguntasSobrePlazosPersonales() {
        assertTrue(AsistenteController.preguntaPorPlazos("¿Qué tengo pendiente?"));
        assertTrue(AsistenteController.preguntaPorPlazos("¿Qué documentos me faltan?"));
        assertFalse(AsistenteController.preguntaPorPlazos("¿En qué formato se entrega el informe final?"));
    }
}
