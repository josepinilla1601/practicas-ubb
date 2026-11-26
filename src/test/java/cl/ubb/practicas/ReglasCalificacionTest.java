package cl.ubb.practicas;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ReglasCalificacionTest {
    static ReglasCalificacion.Nota n(String nota,int ponderacion) { return new ReglasCalificacion.Nota(new BigDecimal(nota),ponderacion); }

    @Test void promedioPonderadoConUnDecimal() {
        assertEquals(new BigDecimal("5.4"),ReglasCalificacion.promedio(List.of(n("6.0",60),n("4.5",40))));
        // 4,45 se redondea a 4,5 (redondeo tradicional).
        assertEquals(new BigDecimal("4.5"),ReglasCalificacion.promedio(List.of(n("4.0",50),n("4.9",50))));
        assertNull(ReglasCalificacion.promedio(List.of()));
    }
    @Test void situacionSegunPonderacionYAprobacion() {
        assertEquals("SIN_NOTAS",ReglasCalificacion.situacion(List.of()));
        assertEquals("EN_CURSO",ReglasCalificacion.situacion(List.of(n("6.0",40))));
        assertEquals("APROBADA",ReglasCalificacion.situacion(List.of(n("4.0",50),n("4.0",50))));
        assertEquals("REPROBADA",ReglasCalificacion.situacion(List.of(n("3.9",100))));
    }
    @Test void validaEscalaYDecimales() {
        assertNull(ReglasCalificacion.validarNota(new BigDecimal("5.5")));
        assertNull(ReglasCalificacion.validarNota(new BigDecimal("7.00")));
        assertNotNull(ReglasCalificacion.validarNota(new BigDecimal("0.9")));
        assertNotNull(ReglasCalificacion.validarNota(new BigDecimal("7.1")));
        assertNotNull(ReglasCalificacion.validarNota(new BigDecimal("5.55")));
    }
}
