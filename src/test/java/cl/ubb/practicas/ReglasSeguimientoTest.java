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
}
