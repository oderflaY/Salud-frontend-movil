package com.eter.salud.presentation.registro

import com.eter.salud.domain.model.MotivoFalloRegistro
import com.eter.salud.domain.model.SesionPaciente
import com.eter.salud.domain.repository.FalloRegistro
import com.eter.salud.presentation.login.AutenticacionRepositorioFalso
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Maquina de estados de la creacion de cuenta.
 *
 * Cubre la misma regla de oro que el acceso: nada sale del dispositivo si el
 * formulario no es valido, y ningun fallo del backend deja la pantalla en un
 * estado ambiguo.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RegistroViewModelTest {

    @BeforeTest
    fun configurar() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun limpiar() {
        Dispatchers.resetMain()
    }

    private fun viewModel(repositorio: AutenticacionRepositorioFalso) =
        RegistroViewModel(repositorio = repositorio)

    private fun formularioValido(vm: RegistroViewModel) {
        vm.actualizarCorreo("paciente@correo.com")
        vm.actualizarContrasena("salud12345")
        vm.actualizarConfirmacion("salud12345")
        vm.aceptarAvisoPrivacidad(true)
    }

    @Test
    fun la_pantalla_arranca_vacia_con_el_aviso_sin_aceptar_y_sin_poder_enviar() {
        val vm = viewModel(AutenticacionRepositorioFalso())

        val estado = vm.estado.value
        assertEquals("", estado.correo)
        assertFalse(estado.avisoAceptado)
        assertFalse(estado.puedeEnviar)
    }

    @Test
    fun el_boton_solo_se_habilita_con_los_tres_campos_escritos_y_el_aviso_aceptado() {
        val vm = viewModel(AutenticacionRepositorioFalso())
        vm.actualizarCorreo("paciente@correo.com")
        vm.actualizarContrasena("salud12345")
        vm.actualizarConfirmacion("salud12345")
        assertFalse(vm.estado.value.puedeEnviar)

        vm.aceptarAvisoPrivacidad(true)

        assertTrue(vm.estado.value.puedeEnviar)
    }

    @Test
    fun un_formulario_invalido_nunca_llega_al_backend() {
        val repositorio = AutenticacionRepositorioFalso()
        val vm = viewModel(repositorio)
        vm.actualizarCorreo("paciente")
        vm.actualizarContrasena("sal")
        vm.actualizarConfirmacion("otra")
        vm.aceptarAvisoPrivacidad(true)

        vm.crearCuenta()

        assertEquals(0, repositorio.cuentasCreadas)
        assertContains(vm.estado.value.erroresCampo, ErrorCampoRegistro.CORREO_FORMATO)
        assertContains(vm.estado.value.erroresCampo, ErrorCampoRegistro.CONTRASENA_CORTA)
        assertNull(vm.estado.value.sesion)
    }

    @Test
    fun una_cuenta_creada_abre_sesion_y_exige_pasar_por_el_onboarding() {
        val repositorio = AutenticacionRepositorioFalso(
            resultadoRegistro = Result.success(
                SesionPaciente(
                    idPaciente = "pac_nuevo",
                    token = "jwt",
                    requiereOnboarding = true,
                ),
            ),
        )
        val vm = viewModel(repositorio)
        formularioValido(vm)

        vm.crearCuenta()

        val estado = vm.estado.value
        assertEquals(1, repositorio.cuentasCreadas)
        assertEquals("paciente@correo.com", repositorio.ultimoCorreo)
        assertFalse(estado.creandoCuenta)
        assertEquals("pac_nuevo", estado.sesion?.idPaciente)
        assertTrue(estado.sesion?.requiereOnboarding == true)
    }

    @Test
    fun las_contrasenas_se_vacian_al_abrir_sesion_para_no_dejarlas_en_memoria() {
        val vm = viewModel(AutenticacionRepositorioFalso())
        formularioValido(vm)

        vm.crearCuenta()

        assertEquals("", vm.estado.value.contrasena)
        assertEquals("", vm.estado.value.confirmacion)
    }

    @Test
    fun un_correo_ya_registrado_se_distingue_de_un_fallo_de_red() {
        val repositorio = AutenticacionRepositorioFalso(
            resultadoRegistro = Result.failure(
                FalloRegistro(MotivoFalloRegistro.CORREO_YA_REGISTRADO),
            ),
        )
        val vm = viewModel(repositorio)
        formularioValido(vm)

        vm.crearCuenta()

        assertEquals(ErrorRegistro.CORREO_YA_REGISTRADO, vm.estado.value.errorRegistro)
        assertNull(vm.estado.value.sesion)
    }

    @Test
    fun un_fallo_inesperado_se_reporta_como_falta_de_conexion_y_conserva_lo_escrito() {
        val repositorio = AutenticacionRepositorioFalso(
            resultadoRegistro = Result.failure(IllegalStateException("socket cerrado")),
        )
        val vm = viewModel(repositorio)
        formularioValido(vm)

        vm.crearCuenta()

        assertEquals(ErrorRegistro.SIN_CONEXION, vm.estado.value.errorRegistro)
        assertEquals("salud12345", vm.estado.value.contrasena)
    }

    @Test
    fun el_doble_toque_no_crea_dos_cuentas() {
        val repositorio = AutenticacionRepositorioFalso()
        val vm = viewModel(repositorio)
        formularioValido(vm)

        vm.crearCuenta()
        vm.crearCuenta()

        assertEquals(1, repositorio.cuentasCreadas)
    }

    @Test
    fun escribir_de_nuevo_limpia_los_errores_del_intento_anterior() {
        val vm = viewModel(AutenticacionRepositorioFalso())
        vm.crearCuenta()
        assertTrue(vm.estado.value.erroresCampo.isNotEmpty())

        vm.actualizarCorreo("p")

        assertTrue(vm.estado.value.erroresCampo.isEmpty())
    }

    @Test
    fun alternar_la_visibilidad_afecta_a_los_dos_campos_de_contrasena_a_la_vez() {
        val vm = viewModel(AutenticacionRepositorioFalso())

        vm.alternarVisibilidadContrasena()

        assertTrue(vm.estado.value.contrasenaVisible)
    }
}
