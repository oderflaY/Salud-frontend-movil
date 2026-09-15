package com.eter.salud.presentation.configuracion

import com.eter.salud.domain.repository.BajaDeCuentaRepositorio
import com.eter.salud.domain.repository.FalloBaja
import com.eter.salud.domain.repository.MotivoFalloBaja
import com.eter.salud.domain.repository.TipoDeCuenta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * La baja es irreversible: estas pruebas cuidan que no se pueda disparar sin
 * confirmar, y que la contrasena no se quede en memoria mas de lo necesario.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BajaDeCuentaViewModelTest {

    private class RepositorioFalso(var resultado: Result<Unit> = Result.success(Unit)) : BajaDeCuentaRepositorio {
        val llamadas = mutableListOf<Triple<TipoDeCuenta, String, String>>()
        override suspend fun darDeBaja(tipo: TipoDeCuenta, correo: String, contrasena: String): Result<Unit> {
            llamadas += Triple(tipo, correo, contrasena)
            return resultado
        }
    }

    @BeforeTest
    fun configurar() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun limpiar() {
        Dispatchers.resetMain()
    }

    private fun BajaDeCuentaViewModel.llenar(correo: String = "  Ana@Correo.MX ", contrasena: String = "Demo1234") {
        desplegar()
        actualizarCorreo(correo)
        actualizarContrasena(contrasena)
    }

    @Test
    fun sin_marcar_que_se_entiende_no_se_puede_enviar() {
        val repositorio = RepositorioFalso()
        val vm = BajaDeCuentaViewModel(repositorio, TipoDeCuenta.PACIENTE)
        vm.llenar()

        assertFalse(vm.estado.value.puedeEnviar)
        vm.darDeBaja()
        assertTrue(repositorio.llamadas.isEmpty(), "no debe llegar nada al backend sin confirmar")
    }

    @Test
    fun confirmada_envia_el_correo_normalizado_y_termina() {
        val repositorio = RepositorioFalso()
        val vm = BajaDeCuentaViewModel(repositorio, TipoDeCuenta.PROFESIONAL)
        vm.llenar()
        vm.cambiarConfirmacion(true)

        vm.darDeBaja()

        assertEquals(Triple(TipoDeCuenta.PROFESIONAL, "ana@correo.mx", "Demo1234"), repositorio.llamadas.single())
        val estado = vm.estado.value
        assertTrue(estado.completada)
        assertEquals("", estado.contrasena, "la contrasena no se queda en memoria")
        assertFalse(estado.puedeEnviar, "una cuenta ya borrada no se vuelve a enviar")
    }

    @Test
    fun credenciales_malas_muestran_el_error_y_limpian_la_contrasena() {
        val repositorio = RepositorioFalso(Result.failure(FalloBaja(MotivoFalloBaja.CREDENCIALES_INVALIDAS)))
        val vm = BajaDeCuentaViewModel(repositorio, TipoDeCuenta.PACIENTE)
        vm.llenar()
        vm.cambiarConfirmacion(true)

        vm.darDeBaja()

        val estado = vm.estado.value
        assertEquals(MotivoFalloBaja.CREDENCIALES_INVALIDAS, estado.error)
        assertEquals("", estado.contrasena)
        assertFalse(estado.completada)
    }

    @Test
    fun sin_red_conserva_la_contrasena_para_reintentar() {
        val repositorio = RepositorioFalso(Result.failure(IllegalStateException("socket cerrado")))
        val vm = BajaDeCuentaViewModel(repositorio, TipoDeCuenta.PACIENTE)
        vm.llenar()
        vm.cambiarConfirmacion(true)

        vm.darDeBaja()

        val estado = vm.estado.value
        assertEquals(MotivoFalloBaja.SIN_CONEXION, estado.error)
        assertEquals("Demo1234", estado.contrasena)
    }

    @Test
    fun cancelar_borra_lo_tecleado() {
        val vm = BajaDeCuentaViewModel(RepositorioFalso(), TipoDeCuenta.PACIENTE)
        vm.llenar()
        vm.cambiarConfirmacion(true)

        vm.plegar()

        val estado = vm.estado.value
        assertFalse(estado.desplegada)
        assertEquals("", estado.contrasena)
        assertFalse(estado.confirmada)
        assertNull(estado.error)
    }
}
