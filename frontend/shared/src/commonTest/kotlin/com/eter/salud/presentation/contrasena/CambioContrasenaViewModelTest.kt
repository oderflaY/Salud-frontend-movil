package com.eter.salud.presentation.contrasena

import com.eter.salud.domain.model.SesionPaciente
import com.eter.salud.domain.repository.CambioDeContrasenaRepositorio
import com.eter.salud.domain.repository.FalloCambioContrasena
import com.eter.salud.domain.repository.MotivoFalloCambioContrasena
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

@OptIn(ExperimentalCoroutinesApi::class)
class CambioContrasenaViewModelTest {

    private class RepositorioFalso(
        private val resultado: Result<SesionPaciente> = Result.success(
            SesionPaciente("pac_1", "jwt_nuevo", requiereOnboarding = false),
        ),
    ) : CambioDeContrasenaRepositorio {
        val llamadas = mutableListOf<Pair<String?, String>>()
        override suspend fun cambiar(contrasenaActual: String?, contrasenaNueva: String): Result<SesionPaciente> {
            llamadas += contrasenaActual to contrasenaNueva
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

    private fun CambioContrasenaViewModel.llenar(actual: String = "", nueva: String, confirmacion: String = nueva) {
        if (actual.isNotEmpty()) actualizarActual(actual)
        actualizarNueva(nueva)
        actualizarConfirmacion(confirmacion)
    }

    // ------------------------------------------------------------ Obligatorio

    @Test
    fun el_obligatorio_arranca_abierto_y_sin_pedir_la_actual() {
        val vm = CambioContrasenaViewModel(RepositorioFalso(), obligatorio = true)

        assertTrue(vm.estado.value.desplegado)
        vm.llenar(nueva = "MiClaveNueva2026")
        assertTrue(vm.estado.value.puedeEnviar)
    }

    @Test
    fun el_obligatorio_manda_la_nueva_sin_la_actual_y_entrega_la_sesion() {
        val repositorio = RepositorioFalso()
        val vm = CambioContrasenaViewModel(repositorio, obligatorio = true)
        vm.llenar(nueva = "MiClaveNueva2026")

        vm.guardar()

        assertEquals(listOf<Pair<String?, String>>(null to "MiClaveNueva2026"), repositorio.llamadas)
        assertEquals("jwt_nuevo", vm.estado.value.sesionNueva?.token)
        // Ninguna contrasena se queda en memoria.
        assertEquals("", vm.estado.value.nueva)
        assertEquals("", vm.estado.value.confirmacion)
    }

    @Test
    fun el_obligatorio_no_se_puede_plegar() {
        val vm = CambioContrasenaViewModel(RepositorioFalso(), obligatorio = true)
        vm.llenar(nueva = "MiClaveNueva2026")

        vm.plegar()

        assertTrue(vm.estado.value.desplegado)
        assertEquals("MiClaveNueva2026", vm.estado.value.nueva)
    }

    // ------------------------------------------------------------ Validacion

    @Test
    fun una_contrasena_corta_no_sale_del_telefono() {
        val repositorio = RepositorioFalso()
        val vm = CambioContrasenaViewModel(repositorio, obligatorio = true)
        vm.llenar(nueva = "corta")

        vm.guardar()

        assertEquals(listOf(ErrorCampoContrasena.NUEVA_CORTA), vm.estado.value.erroresCampo)
        assertTrue(repositorio.llamadas.isEmpty())
    }

    @Test
    fun las_dos_tienen_que_coincidir() {
        val vm = CambioContrasenaViewModel(RepositorioFalso(), obligatorio = true)
        vm.llenar(nueva = "MiClaveNueva2026", confirmacion = "MiClaveNueva2025")

        vm.guardar()

        assertEquals(listOf(ErrorCampoContrasena.NO_COINCIDEN), vm.estado.value.erroresCampo)
    }

    @Test
    fun en_ajustes_la_nueva_no_puede_ser_la_misma() {
        val vm = CambioContrasenaViewModel(RepositorioFalso(), obligatorio = false)
        vm.desplegar()
        vm.llenar(actual = "Demo1234", nueva = "Demo1234")

        vm.guardar()

        assertEquals(listOf(ErrorCampoContrasena.IGUAL_A_LA_ACTUAL), vm.estado.value.erroresCampo)
    }

    @Test
    fun escribir_limpia_los_errores() {
        val vm = CambioContrasenaViewModel(RepositorioFalso(), obligatorio = true)
        vm.llenar(nueva = "corta")
        vm.guardar()

        vm.actualizarNueva("cortaperoya")

        assertTrue(vm.estado.value.erroresCampo.isEmpty())
    }

    // ------------------------------------------------------------ Voluntario

    @Test
    fun en_ajustes_hace_falta_la_actual_para_enviar() {
        val vm = CambioContrasenaViewModel(RepositorioFalso(), obligatorio = false)
        vm.desplegar()
        vm.llenar(nueva = "OtraClave2027")

        assertFalse(vm.estado.value.puedeEnviar)
    }

    @Test
    fun en_ajustes_manda_la_actual_y_al_entregar_se_pliega_con_aviso() {
        val repositorio = RepositorioFalso()
        val vm = CambioContrasenaViewModel(repositorio, obligatorio = false)
        vm.desplegar()
        vm.llenar(actual = "Demo1234", nueva = "OtraClave2027")

        vm.guardar()
        vm.sesionEntregada()

        assertEquals(listOf<Pair<String?, String>>("Demo1234" to "OtraClave2027"), repositorio.llamadas)
        assertFalse(vm.estado.value.desplegado)
        assertTrue(vm.estado.value.completado)
        assertNull(vm.estado.value.sesionNueva)
    }

    @Test
    fun cancelar_en_ajustes_borra_lo_tecleado() {
        val vm = CambioContrasenaViewModel(RepositorioFalso(), obligatorio = false)
        vm.desplegar()
        vm.llenar(actual = "Demo1234", nueva = "OtraClave2027")

        vm.plegar()

        assertFalse(vm.estado.value.desplegado)
        assertEquals("", vm.estado.value.actual)
        assertEquals("", vm.estado.value.nueva)
    }

    // ------------------------------------------------------------ Fallos

    @Test
    fun una_actual_incorrecta_se_explica_y_se_borra() {
        val vm = CambioContrasenaViewModel(
            RepositorioFalso(Result.failure(FalloCambioContrasena(MotivoFalloCambioContrasena.CREDENCIALES_INVALIDAS))),
            obligatorio = false,
        )
        vm.desplegar()
        vm.llenar(actual = "NoEsLaClave1", nueva = "OtraClave2027")

        vm.guardar()

        assertEquals(MotivoFalloCambioContrasena.CREDENCIALES_INVALIDAS, vm.estado.value.error)
        assertEquals("", vm.estado.value.actual)
        assertFalse(vm.estado.value.enviando)
    }

    @Test
    fun una_temporal_vencida_se_explica() {
        val vm = CambioContrasenaViewModel(
            RepositorioFalso(Result.failure(FalloCambioContrasena(MotivoFalloCambioContrasena.CONTRASENA_TEMPORAL_VENCIDA))),
            obligatorio = true,
        )
        vm.llenar(nueva = "MiClaveNueva2026")

        vm.guardar()

        assertEquals(MotivoFalloCambioContrasena.CONTRASENA_TEMPORAL_VENCIDA, vm.estado.value.error)
        assertNull(vm.estado.value.sesionNueva)
    }

    @Test
    fun cualquier_otra_excepcion_cuenta_como_sin_conexion() {
        val vm = CambioContrasenaViewModel(
            RepositorioFalso(Result.failure(IllegalStateException("socket cerrado"))),
            obligatorio = true,
        )
        vm.llenar(nueva = "MiClaveNueva2026")

        vm.guardar()

        assertEquals(MotivoFalloCambioContrasena.SIN_CONEXION, vm.estado.value.error)
    }
}
