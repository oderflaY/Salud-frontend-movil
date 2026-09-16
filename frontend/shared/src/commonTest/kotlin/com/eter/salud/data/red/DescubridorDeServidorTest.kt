package com.eter.salud.data.red

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DescubridorDeServidorTest {

    private class RedFalsa(private val vivos: Set<String>) {
        val probados = mutableListOf<String>()
        suspend fun sondear(url: String): Boolean {
            probados += url
            return url in vivos
        }
    }

    @Test
    fun si_la_direccion_conocida_responde_no_barre_nada() = runTest {
        val red = RedFalsa(setOf("http://192.168.18.27:8000"))
        val hallado = DescubridorDeServidor(red::sondear).encontrar(listOf("http://192.168.18.27:8000/"))
        assertEquals("http://192.168.18.27:8000", hallado)
        assertEquals(1, red.probados.size)
    }

    @Test
    fun prueba_primero_la_ultima_que_funciono_y_luego_la_del_apk() = runTest {
        val red = RedFalsa(setOf("http://192.168.18.110:8000"))
        val hallado = DescubridorDeServidor(red::sondear)
            .encontrar(listOf("http://192.168.18.27:8000", "http://192.168.18.110:8000"))
        assertEquals("http://192.168.18.110:8000", hallado)
        assertEquals(listOf("http://192.168.18.27:8000", "http://192.168.18.110:8000"), red.probados)
    }

    @Test
    fun si_cambio_la_ip_la_encuentra_en_la_misma_subred() = runTest {
        val red = RedFalsa(setOf("http://192.168.18.203:8000"))
        val hallado = DescubridorDeServidor(red::sondear).encontrar(listOf("http://192.168.18.27:8000"))
        assertEquals("http://192.168.18.203:8000", hallado)
    }

    @Test
    fun nunca_barre_direcciones_publicas_ni_nombres() = runTest {
        val red = RedFalsa(emptySet())
        assertNull(DescubridorDeServidor(red::sondear).encontrar(listOf("https://api.salud.mx", "http://8.8.8.8:8000")))
        assertEquals(2, red.probados.size)
    }

    @Test
    fun reconoce_solo_subredes_privadas() {
        assertEquals(DescubridorDeServidor.Subred("http", "192.168.18", 8000), DescubridorDeServidor.subredDe("http://192.168.18.27:8000"))
        assertEquals(DescubridorDeServidor.Subred("http", "10.0.2", 80), DescubridorDeServidor.subredDe("http://10.0.2.2"))
        assertEquals("172.20.1", DescubridorDeServidor.subredDe("http://172.20.1.5:8000")?.prefijo)
        assertNull(DescubridorDeServidor.subredDe("http://172.32.1.5:8000"))
        assertNull(DescubridorDeServidor.subredDe("http://localhost:8000"))
        assertNull(DescubridorDeServidor.subredDe("https://api.salud.mx"))
    }
}
