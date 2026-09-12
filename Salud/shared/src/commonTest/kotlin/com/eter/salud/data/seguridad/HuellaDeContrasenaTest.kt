package com.eter.salud.data.seguridad

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Huella de contrasenas.
 *
 * SHA-256 y PBKDF2 se comparan contra los vectores publicados (FIPS 180-4 y
 * RFC 7914): una implementacion de criptografia que "parece funcionar" pero
 * difiere en un bit no se nota hasta el dia en que haga falta migrar las
 * huellas a otro sistema.
 */
class HuellaDeContrasenaTest {

    @Test
    fun sha256_coincide_con_los_vectores_de_fips_180_4() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            Sha256.resumen(ByteArray(0)).aHex(),
        )
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Sha256.resumen("abc".encodeToByteArray()).aHex(),
        )
        // 56 bytes: obliga a un segundo bloque de relleno.
        assertEquals(
            "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
            Sha256.resumen("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq".encodeToByteArray()).aHex(),
        )
    }

    @Test
    fun pbkdf2_coincide_con_el_vector_del_rfc_7914() {
        val huella = HuellaDeContrasena.pbkdf2(
            contrasena = "passwd".encodeToByteArray(),
            sal = "salt".encodeToByteArray(),
            iteraciones = 1,
            longitud = 64,
        )
        assertEquals(
            "55ac046e56e3089fec1691c22544b605f94185216dde0465e68b9d57c20dacbc" +
                "49ca9cccf179b645991664b39d77ef317c71b845b1e30bd509112041d3a19783",
            huella.aHex(),
        )
    }

    @Test
    fun la_contrasena_correcta_verifica_y_una_distinta_no() {
        val guardada = HuellaDeContrasena.crear("salud12345", iteraciones = 50)

        assertTrue(HuellaDeContrasena.verificar("salud12345", guardada))
        assertFalse(HuellaDeContrasena.verificar("salud12346", guardada))
        assertFalse(HuellaDeContrasena.verificar("", guardada))
    }

    @Test
    fun la_huella_no_contiene_la_contrasena_y_cada_una_lleva_su_sal() {
        val primera = HuellaDeContrasena.crear("salud12345", iteraciones = 50)
        val segunda = HuellaDeContrasena.crear("salud12345", iteraciones = 50)

        assertFalse("salud12345" in primera)
        // Misma contrasena, sal distinta: dos cuentas no delatan que la comparten.
        assertNotEquals(primera, segunda)
    }

    @Test
    fun una_huella_con_formato_desconocido_nunca_verifica() {
        assertFalse(HuellaDeContrasena.verificar("salud12345", "salud12345"))
        assertFalse(HuellaDeContrasena.verificar("salud12345", "md5\$1\$00\$00"))
        assertFalse(HuellaDeContrasena.verificar("salud12345", "pbkdf2-sha256\$0\$00\$00"))
        assertFalse(HuellaDeContrasena.verificar("salud12345", "pbkdf2-sha256\$10\$zz\$00"))
    }
}
