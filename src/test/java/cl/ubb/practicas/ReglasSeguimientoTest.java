package cl.ubb.practicas;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReglasSeguimientoTest {
    @Test void vencimientoRespetaDiaLimiteYAusenciaDePlazo() {
        LocalDate hoy=LocalDate.of(2026,10,28);
        assertTrue(ReglasSeguimiento.vencido(hoy.minusDays(1),true,hoy));
        assertFalse(ReglasSeguimiento.vencido(hoy,true,hoy));
        assertFalse(ReglasSeguimiento.vencido(null,true,hoy));
        assertFalse(ReglasSeguimiento.vencido(hoy.minusDays(1),false,hoy));
    }
    @Test void estadoDependeDeLaUltimaVersionYSuRevision() {
        assertEquals("PENDIENTE",ReglasSeguimiento.estado(0,null));
        assertEquals("EN_REVISION",ReglasSeguimiento.estado(2,null));
        assertEquals("OBSERVADO",ReglasSeguimiento.estado(1,"OBSERVADO"));
        assertEquals("APROBADO",ReglasSeguimiento.estado(3,"APROBADO"));
        assertTrue(ReglasSeguimiento.pendiente("OBSERVADO"));
        assertFalse(ReglasSeguimiento.pendiente("EN_REVISION"));
    }
}
