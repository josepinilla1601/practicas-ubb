package cl.ubb.practicas;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;

class ClaveTest {
    @Test void rechazaClavesCortasYLimiteDeBytesBcrypt() {
        assertThrows(Problema.class,()->CuentaController.validarClave("corta"));
        assertThrows(Problema.class,()->CuentaController.validarClave("ñ".repeat(40)));
        assertDoesNotThrow(()->CuentaController.validarClave("UnaClaveLarga2026"));
    }
    @Test void hashNoAlmacenaTextoYCompruebaClave() {
        var encoder=new BCryptPasswordEncoder();String hash=encoder.encode("UnaClaveLarga2026");
        assertNotEquals("UnaClaveLarga2026",hash);
        assertTrue(encoder.matches("UnaClaveLarga2026",hash));
        assertFalse(encoder.matches("OtraClaveLarga2026",hash));
    }
}
