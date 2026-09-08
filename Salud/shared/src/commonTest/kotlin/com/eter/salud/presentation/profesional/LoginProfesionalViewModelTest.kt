package com.eter.salud.presentation.profesional

import com.eter.salud.domain.model.EstadoVerificacionCedula
import com.eter.salud.domain.model.MotivoFalloAutenticacionProfesional
import com.eter.salud.domain.model.SesionProfesional
import com.eter.salud.domain.repository.FalloAutenticacionProfesional
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
 * Maquina de estados del acceso de personal medico. Es la barrera que protege
 * el escaner de emergencia: estas pruebas verifican que ninguna credencial
 * sale del dispositivo si el formulario no es valido.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LoginProfesionalViewModelTest {

    @BeforeTest
    fun configurar() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun limpiar() {
        Dispatchers.resetMain()
    }

    private fun viewModel(repositorio: AutenticacionProfesionalRepositorioFalso) =
        LoginProfesionalViewModel(repositorio = repositorio)

    private fun falloPor(motivo: MotivoFalloAutenticacionProfesional) =
        AutenticacionProfesionalRepositorioFalso(Result.failure(FalloAutenticacionProfesional(motivo)))

    @Test
    fun la_pantalla_arranca_vacia_sin_poder_enviar() {
        val vm = viewModel(AutenticacionProfesionalRepositorioFalso())

        assertFalse(vm.estado.value.puedeEnviar)
    }

    @Test
    fun un_formulario_invalido_nunca_llega_al_backend() {
        val repositorio = AutenticacionProfesionalRepositorioFalso()
        val vm = viewModel(repositorio)
        vm.actualizarCorreo("no-es-correo")
        vm.actualizarContrasena("123")

        vm.iniciarSesion()

        assertEquals(0, repositorio.invocaciones)
        assertContains(vm.estado.value.erroresCampo, ErrorCampoLoginProfesional.CORREO_FORMATO)
        assertContains(vm.estado.value.erroresCampo, ErrorCampoLoginProfesional.CONTRASENA_CORTA)
        assertNull(vm.estado.value.sesion)
    }

    @Test
    fun un_acceso_correcto_publica_la_sesion_con_la_cedula_ya_verificada() {
        val repositorio = AutenticacionProfesionalRepositorioFalso(
            Result.success(
                SesionProfesional(
                    idMedico = "doc_889900A",
                    token = "jwt",
                    nombre = "Elena",
                    apellidos = "Ruiz Santos",
                    tratamiento = "Dra.",
                    estadoVerificacion = EstadoVerificacionCedula.APROBADO,
                ),
            ),
        )
        val vm = viewModel(repositorio)
        vm.actualizarCorreo("dr.elena.ruiz@hospital.com")
        vm.actualizarContrasena("salud12345")

        vm.iniciarSesion()

        val estado = vm.estado.value
        assertEquals(1, repositorio.invocaciones)
        assertFalse(estado.autenticando)
        assertEquals("doc_889900A", estado.sesion?.idMedico)
        assertEquals(EstadoVerificacionCedula.APROBADO, estado.sesion?.estadoVerificacion)
        assertNull(estado.errorAutenticacion)
    }

    @Test
    fun el_correo_viaja_normalizado_y_la_contrasena_intacta() {
        val repositorio = AutenticacionProfesionalRepositorioFalso()
        val vm = viewModel(repositorio)
        vm.actualizarCorreo("  Dr.Elena.Ruiz@Hospital.COM ")
        vm.actualizarContrasena(" Secreta 123 ")

        vm.iniciarSesion()

        assertEquals("dr.elena.ruiz@hospital.com", repositorio.ultimoCorreo)
        assertEquals(" Secreta 123 ", repositorio.ultimaContrasena)
    }

    @Test
    fun unas_credenciales_incorrectas_vacian_la_contrasena() {
        val vm = viewModel(falloPor(MotivoFalloAutenticacionProfesional.CREDENCIALES_INVALIDAS))
        vm.actualizarCorreo("dr.elena.ruiz@hospital.com")
        vm.actualizarContrasena("salud12345")

        vm.iniciarSesion()

        val estado = vm.estado.value
        assertEquals(ErrorAutenticacionProfesional.CREDENCIALES_INVALIDAS, estado.errorAutenticacion)
        assertEquals("", estado.contrasena)
        assertNull(estado.sesion)
    }

    @Test
    fun una_cuenta_bloqueada_se_distingue_de_unas_credenciales_incorrectas() {
        val vm = viewModel(falloPor(MotivoFalloAutenticacionProfesional.CUENTA_BLOQUEADA))
        vm.actualizarCorreo("dr.elena.ruiz@hospital.com")
        vm.actualizarContrasena("salud12345")

        vm.iniciarSesion()

        assertEquals(ErrorAutenticacionProfesional.CUENTA_BLOQUEADA, vm.estado.value.errorAutenticacion)
    }

    @Test
    fun un_fallo_inesperado_se_reporta_como_falta_de_conexion() {
        val repositorio = AutenticacionProfesionalRepositorioFalso(
            Result.failure(IllegalStateException("socket cerrado")),
        )
        val vm = viewModel(repositorio)
        vm.actualizarCorreo("dr.elena.ruiz@hospital.com")
        vm.actualizarContrasena("salud12345")

        vm.iniciarSesion()

        assertEquals(ErrorAutenticacionProfesional.SIN_CONEXION, vm.estado.value.errorAutenticacion)
    }

    @Test
    fun el_doble_toque_en_el_boton_no_dispara_dos_autenticaciones() {
        val repositorio = AutenticacionProfesionalRepositorioFalso()
        val vm = viewModel(repositorio)
        vm.actualizarCorreo("dr.elena.ruiz@hospital.com")
        vm.actualizarContrasena("salud12345")

        vm.iniciarSesion()
        vm.iniciarSesion()

        assertEquals(1, repositorio.invocaciones)
    }

    @Test
    fun alternar_la_visibilidad_de_la_contrasena_no_altera_su_contenido() {
        val vm = viewModel(AutenticacionProfesionalRepositorioFalso())
        vm.actualizarContrasena("salud12345")

        vm.alternarVisibilidadContrasena()
        assertTrue(vm.estado.value.contrasenaVisible)

        vm.alternarVisibilidadContrasena()
        assertFalse(vm.estado.value.contrasenaVisible)
        assertEquals("salud12345", vm.estado.value.contrasena)
    }

    @Test
    fun descartar_el_error_lo_retira_del_estado_para_reintentar() {
        val vm = viewModel(falloPor(MotivoFalloAutenticacionProfesional.SIN_CONEXION))
        vm.actualizarCorreo("dr.elena.ruiz@hospital.com")
        vm.actualizarContrasena("salud12345")
        vm.iniciarSesion()

        vm.descartarError()

        assertNull(vm.estado.value.errorAutenticacion)
    }
}
