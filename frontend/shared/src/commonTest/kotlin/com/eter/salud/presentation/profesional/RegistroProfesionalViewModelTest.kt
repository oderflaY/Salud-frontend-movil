package com.eter.salud.presentation.profesional

import com.eter.salud.domain.model.EstadoVerificacionCedula
import com.eter.salud.domain.model.MotivoFalloRegistroProfesional
import com.eter.salud.domain.model.SesionProfesional
import com.eter.salud.domain.repository.FalloRegistroProfesional
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

@OptIn(ExperimentalCoroutinesApi::class)
class RegistroProfesionalViewModelTest {

    @BeforeTest
    fun configurar() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun limpiar() {
        Dispatchers.resetMain()
    }

    private fun viewModel(repositorio: AutenticacionProfesionalRepositorioFalso) =
        RegistroProfesionalViewModel(repositorio = repositorio)

    private fun formularioValido(vm: RegistroProfesionalViewModel) {
        vm.actualizarCorreo("dr.carlos@hospital.com")
        vm.actualizarContrasena("salud12345")
        vm.actualizarConfirmacion("salud12345")
        vm.actualizarNombre("Carlos")
        vm.actualizarApellidos("Mendez")
        vm.actualizarTratamiento("Dr.")
        vm.actualizarCedulaProfesional("87654321")
        vm.aceptarAvisoPrivacidad(true)
    }

    @Test
    fun el_boton_solo_se_habilita_con_todos_los_campos_y_el_aviso_aceptado() {
        val vm = viewModel(AutenticacionProfesionalRepositorioFalso())
        vm.actualizarCorreo("dr.carlos@hospital.com")
        vm.actualizarContrasena("salud12345")
        vm.actualizarConfirmacion("salud12345")
        vm.actualizarNombre("Carlos")
        vm.actualizarApellidos("Mendez")
        vm.actualizarTratamiento("Dr.")
        vm.actualizarCedulaProfesional("87654321")
        assertFalse(vm.estado.value.puedeEnviar)

        vm.aceptarAvisoPrivacidad(true)

        assertTrue(vm.estado.value.puedeEnviar)
    }

    @Test
    fun un_formulario_invalido_nunca_llega_al_backend() {
        val repositorio = AutenticacionProfesionalRepositorioFalso()
        val vm = viewModel(repositorio)
        vm.actualizarCorreo("no-es-correo")
        vm.actualizarContrasena("123")
        vm.actualizarCedulaProfesional("12")
        vm.aceptarAvisoPrivacidad(true)

        vm.crearCuenta()

        assertEquals(0, repositorio.cuentasCreadas)
        assertContains(vm.estado.value.erroresCampo, ErrorCampoRegistroProfesional.CORREO_FORMATO)
        assertContains(vm.estado.value.erroresCampo, ErrorCampoRegistroProfesional.CEDULA_FORMATO)
        assertNull(vm.estado.value.sesion)
    }

    @Test
    fun una_cuenta_creada_abre_sesion_con_la_cedula_pendiente_de_verificacion() {
        val repositorio = AutenticacionProfesionalRepositorioFalso(
            resultadoRegistro = Result.success(
                SesionProfesional(
                    idMedico = "doc_local_1",
                    token = "jwt",
                    nombre = "Carlos",
                    apellidos = "Mendez",
                    tratamiento = "Dr.",
                    estadoVerificacion = EstadoVerificacionCedula.PENDIENTE,
                ),
            ),
        )
        val vm = viewModel(repositorio)
        formularioValido(vm)

        vm.crearCuenta()

        val estado = vm.estado.value
        assertEquals(1, repositorio.cuentasCreadas)
        assertEquals("87654321", repositorio.ultimaCedula)
        assertFalse(estado.creandoCuenta)
        assertEquals("doc_local_1", estado.sesion?.idMedico)
        assertEquals(EstadoVerificacionCedula.PENDIENTE, estado.sesion?.estadoVerificacion)
    }

    @Test
    fun las_contrasenas_se_vacian_al_abrir_sesion() {
        val vm = viewModel(AutenticacionProfesionalRepositorioFalso())
        formularioValido(vm)

        vm.crearCuenta()

        assertEquals("", vm.estado.value.contrasena)
        assertEquals("", vm.estado.value.confirmacion)
    }

    @Test
    fun una_cedula_ya_registrada_se_distingue_de_un_correo_ya_registrado() {
        val repositorio = AutenticacionProfesionalRepositorioFalso(
            resultadoRegistro = Result.failure(
                FalloRegistroProfesional(MotivoFalloRegistroProfesional.CEDULA_YA_REGISTRADA),
            ),
        )
        val vm = viewModel(repositorio)
        formularioValido(vm)

        vm.crearCuenta()

        assertEquals(ErrorRegistroProfesional.CEDULA_YA_REGISTRADA, vm.estado.value.errorRegistro)
        assertNull(vm.estado.value.sesion)
    }

    @Test
    fun un_correo_ya_registrado_se_reporta_por_separado() {
        val repositorio = AutenticacionProfesionalRepositorioFalso(
            resultadoRegistro = Result.failure(
                FalloRegistroProfesional(MotivoFalloRegistroProfesional.CORREO_YA_REGISTRADO),
            ),
        )
        val vm = viewModel(repositorio)
        formularioValido(vm)

        vm.crearCuenta()

        assertEquals(ErrorRegistroProfesional.CORREO_YA_REGISTRADO, vm.estado.value.errorRegistro)
    }

    @Test
    fun el_doble_toque_no_crea_dos_cuentas() {
        val repositorio = AutenticacionProfesionalRepositorioFalso()
        val vm = viewModel(repositorio)
        formularioValido(vm)

        vm.crearCuenta()
        vm.crearCuenta()

        assertEquals(1, repositorio.cuentasCreadas)
    }

    @Test
    fun escribir_de_nuevo_limpia_los_errores_del_intento_anterior() {
        val vm = viewModel(AutenticacionProfesionalRepositorioFalso())
        vm.crearCuenta()
        assertTrue(vm.estado.value.erroresCampo.isNotEmpty())

        vm.actualizarNombre("C")

        assertTrue(vm.estado.value.erroresCampo.isEmpty())
    }
}
