package com.eter.salud.presentation.configuracion

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Regla del modo oscuro efectivo.
 *
 * Es lo que lee `SaludTheme` en la raiz de la app: si falla, el interruptor de
 * Configuracion se mueve y la pantalla no cambia, o peor, la app ignora el
 * modo del sistema aunque el paciente nunca haya tocado nada.
 */
class ConfiguracionViewModelTest {

    @Test
    fun por_defecto_la_app_sigue_al_sistema_en_ambos_sentidos() {
        val estado = ConfiguracionViewModel().estado.value

        assertTrue(estado.modoOscuroEfectivo(sistemaEnOscuro = true))
        assertFalse(estado.modoOscuroEfectivo(sistemaEnOscuro = false))
    }

    @Test
    fun forzar_el_oscuro_gana_aunque_el_sistema_este_en_claro() {
        val configuracion = ConfiguracionViewModel()

        configuracion.cambiarModoOscuro(true)

        assertTrue(configuracion.estado.value.modoOscuroEfectivo(sistemaEnOscuro = false))
    }

    @Test
    fun fijar_el_claro_a_mano_gana_aunque_el_sistema_este_en_oscuro() {
        val configuracion = ConfiguracionViewModel()

        configuracion.cambiarSeguirAlSistema(false)

        assertFalse(configuracion.estado.value.modoOscuroEfectivo(sistemaEnOscuro = true))
    }

    @Test
    fun volver_a_seguir_al_sistema_descarta_el_oscuro_forzado() {
        val configuracion = ConfiguracionViewModel()
        configuracion.cambiarModoOscuro(true)

        configuracion.cambiarSeguirAlSistema(true)

        assertFalse(configuracion.estado.value.modoOscuroEfectivo(sistemaEnOscuro = false))
    }
}
