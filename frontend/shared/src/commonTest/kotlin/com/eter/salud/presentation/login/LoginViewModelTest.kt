package com.eter.salud.presentation.login

import com.eter.salud.domain.model.MotivoFalloAutenticacion
import com.eter.salud.domain.model.SesionPaciente
import com.eter.salud.domain.repository.FalloAutenticacion
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
 * Maquina de estados del acceso del paciente.
 *
 * La regla de oro que cubren estas pruebas: ninguna credencial sale del
 * dispositivo si el formulario no es valido, y ningun fallo del backend deja la
 * pantalla en un estado que permita reintentos ciegos.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    @BeforeTest
    fun configurar() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun limpiar() {
        Dispatchers.resetMain()
    }

    private fun viewModel(repositorio: AutenticacionRepositorioFalso) =
        LoginViewModel(repositorio = repositorio)

    private fun falloPor(motivo: MotivoFalloAutenticacion) =
        AutenticacionRepositorioFalso(Result.failure(FalloAutenticacion(motivo)))

    // ------------------------------------------------------------ Estado inicial

    @Test
    fun la_pantalla_arranca_vacia_con_la_contrasena_oculta_y_sin_poder_enviar() {
        val vm = viewModel(AutenticacionRepositorioFalso())

        val estado = vm.estado.value
        assertEquals("", estado.correo)
        assertEquals("", estado.contrasena)
        assertFalse(estado.contrasenaVisible)
        assertFalse(estado.puedeEnviar)
        assertTrue(estado.erroresCampo.isEmpty())
    }

    @Test
    fun el_boton_se_habilita_en_cuanto_hay_algo_escrito_en_ambos_campos() {
        val vm = viewModel(AutenticacionRepositorioFalso())

        vm.actualizarCorreo("paciente@correo.com")
        assertFalse(vm.estado.value.puedeEnviar)

        vm.actualizarContrasena("12345678")
        assertTrue(vm.estado.value.puedeEnviar)
    }

    // ---------------------------------------------------------------- Validacion

    @Test
    fun un_formulario_invalido_nunca_llega_al_backend() {
        val repositorio = AutenticacionRepositorioFalso()
        val vm = viewModel(repositorio)
        vm.actualizarCorreo("paciente")
        vm.actualizarContrasena("123")

        vm.iniciarSesion()

        assertEquals(0, repositorio.invocaciones)
        assertContains(vm.estado.value.erroresCampo, ErrorCampoLogin.CORREO_FORMATO)
        assertContains(vm.estado.value.erroresCampo, ErrorCampoLogin.CONTRASENA_CORTA)
        assertNull(vm.estado.value.sesion)
    }

    @Test
    fun escribir_de_nuevo_limpia_los_errores_del_intento_anterior() {
        val vm = viewModel(AutenticacionRepositorioFalso())
        vm.iniciarSesion()
        assertTrue(vm.estado.value.erroresCampo.isNotEmpty())

        vm.actualizarCorreo("p")

        assertTrue(vm.estado.value.erroresCampo.isEmpty())
    }

    // -------------------------------------------------------------- Autenticacion

    @Test
    fun un_acceso_correcto_publica_la_sesion_y_apaga_el_indicador_de_carga() {
        val repositorio = AutenticacionRepositorioFalso(
            Result.success(
                SesionPaciente(
                    idPaciente = "pac_01H8X9A",
                    token = "jwt",
                    requiereOnboarding = true,
                ),
            ),
        )
        val vm = viewModel(repositorio)
        vm.actualizarCorreo("paciente@correo.com")
        vm.actualizarContrasena("12345678")

        vm.iniciarSesion()

        val estado = vm.estado.value
        assertEquals(1, repositorio.invocaciones)
        assertFalse(estado.autenticando)
        assertEquals("pac_01H8X9A", estado.sesion?.idPaciente)
        assertTrue(estado.sesion?.requiereOnboarding == true)
        assertNull(estado.errorAutenticacion)
    }

    @Test
    fun entregada_la_sesion_el_formulario_queda_limpio_para_poder_salir_y_entrar_otra_vez() {
        // Si la sesion se quedara en el estado, al cerrar sesion la pantalla de
        // acceso volveria a entregarla y el paciente jamas podria salir.
        val vm = viewModel(AutenticacionRepositorioFalso())
        vm.actualizarCorreo("paciente@correo.com")
        vm.actualizarContrasena("12345678")
        vm.iniciarSesion()

        vm.sesionEntregada()

        val estado = vm.estado.value
        assertNull(estado.sesion)
        assertEquals("", estado.correo)
        assertFalse(estado.autenticando)
    }

    @Test
    fun el_correo_viaja_normalizado_y_la_contrasena_intacta() {
        val repositorio = AutenticacionRepositorioFalso()
        val vm = viewModel(repositorio)
        vm.actualizarCorreo("  Paciente@Correo.COM ")
        vm.actualizarContrasena(" Secreta 123 ")

        vm.iniciarSesion()

        assertEquals("paciente@correo.com", repositorio.ultimoCorreo)
        assertEquals(" Secreta 123 ", repositorio.ultimaContrasena)
    }

    @Test
    fun unas_credenciales_incorrectas_dejan_el_correo_escrito_y_vacian_la_contrasena() {
        val vm = viewModel(falloPor(MotivoFalloAutenticacion.CREDENCIALES_INVALIDAS))
        vm.actualizarCorreo("paciente@correo.com")
        vm.actualizarContrasena("12345678")

        vm.iniciarSesion()

        val estado = vm.estado.value
        assertEquals(
            ErrorAutenticacion.CREDENCIALES_INVALIDAS,
            estado.errorAutenticacion,
        )
        assertEquals("paciente@correo.com", estado.correo)
        assertEquals("", estado.contrasena)
        assertNull(estado.sesion)
    }

    @Test
    fun una_caida_de_red_no_borra_lo_escrito_porque_el_paciente_va_a_reintentar() {
        val vm = viewModel(falloPor(MotivoFalloAutenticacion.SIN_CONEXION))
        vm.actualizarCorreo("paciente@correo.com")
        vm.actualizarContrasena("12345678")

        vm.iniciarSesion()

        val estado = vm.estado.value
        assertEquals(ErrorAutenticacion.SIN_CONEXION, estado.errorAutenticacion)
        assertEquals("12345678", estado.contrasena)
    }

    @Test
    fun una_cuenta_bloqueada_se_distingue_de_unas_credenciales_incorrectas() {
        val vm = viewModel(falloPor(MotivoFalloAutenticacion.CUENTA_BLOQUEADA))
        vm.actualizarCorreo("paciente@correo.com")
        vm.actualizarContrasena("12345678")

        vm.iniciarSesion()

        assertEquals(ErrorAutenticacion.CUENTA_BLOQUEADA, vm.estado.value.errorAutenticacion)
    }

    @Test
    fun un_fallo_inesperado_se_reporta_como_falta_de_conexion_y_no_revienta() {
        val repositorio = AutenticacionRepositorioFalso(
            Result.failure(IllegalStateException("socket cerrado")),
        )
        val vm = viewModel(repositorio)
        vm.actualizarCorreo("paciente@correo.com")
        vm.actualizarContrasena("12345678")

        vm.iniciarSesion()

        assertEquals(ErrorAutenticacion.SIN_CONEXION, vm.estado.value.errorAutenticacion)
    }

    @Test
    fun el_doble_toque_en_el_boton_no_dispara_dos_autenticaciones() {
        val repositorio = AutenticacionRepositorioFalso()
        val vm = viewModel(repositorio)
        vm.actualizarCorreo("paciente@correo.com")
        vm.actualizarContrasena("12345678")

        vm.iniciarSesion()
        vm.iniciarSesion()

        assertEquals(1, repositorio.invocaciones)
    }

    @Test
    fun con_la_sesion_abierta_no_se_vuelve_a_autenticar() {
        val repositorio = AutenticacionRepositorioFalso()
        val vm = viewModel(repositorio)
        vm.actualizarCorreo("paciente@correo.com")
        vm.actualizarContrasena("12345678")
        vm.iniciarSesion()

        vm.iniciarSesion()

        assertEquals(1, repositorio.invocaciones)
    }

    // ------------------------------------------------------------------ Detalles

    @Test
    fun alternar_la_visibilidad_de_la_contrasena_no_altera_su_contenido() {
        val vm = viewModel(AutenticacionRepositorioFalso())
        vm.actualizarContrasena("12345678")

        vm.alternarVisibilidadContrasena()
        assertTrue(vm.estado.value.contrasenaVisible)

        vm.alternarVisibilidadContrasena()
        assertFalse(vm.estado.value.contrasenaVisible)
        assertEquals("12345678", vm.estado.value.contrasena)
    }

    @Test
    fun descartar_el_error_lo_retira_del_estado_para_reintentar() {
        val vm = viewModel(falloPor(MotivoFalloAutenticacion.SIN_CONEXION))
        vm.actualizarCorreo("paciente@correo.com")
        vm.actualizarContrasena("12345678")
        vm.iniciarSesion()

        vm.descartarError()

        assertNull(vm.estado.value.errorAutenticacion)
    }
}
