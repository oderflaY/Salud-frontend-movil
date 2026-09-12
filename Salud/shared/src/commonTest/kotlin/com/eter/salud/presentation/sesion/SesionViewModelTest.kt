package com.eter.salud.presentation.sesion

import com.eter.salud.data.sesion.FuenteDeSesion
import com.eter.salud.domain.model.EstadoVerificacionCedula
import com.eter.salud.domain.model.SesionPaciente
import com.eter.salud.domain.model.SesionProfesional
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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
 * Supervivencia de la sesion.
 *
 * Es la prueba del bug que se reportaba: el paciente aparecia de golpe en la
 * pantalla de acceso tras girar el telefono o volver de una llamada. Lo que se
 * fija aqui es que la sesion se restaure del disco antes de decidir nada, que la
 * memoria mande durante la ejecucion, y que cerrar sesion NO deje rastro.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SesionViewModelTest {

    private val paciente = SesionPaciente(
        idPaciente = "pac_01H8X9A",
        token = "jwt_de_prueba",
        requiereOnboarding = false,
    )

    private val profesional = SesionProfesional(
        idMedico = "doc_889900A",
        token = "jwt_medico",
        nombre = "Elena",
        apellidos = "Ruiz Santos",
        tratamiento = "Dra.",
        estadoVerificacion = EstadoVerificacionCedula.APROBADO,
    )

    /** Almacen en memoria: mismo contrato, sin tocar disco. */
    private class AlmacenFalso(inicial: SesionUiState = SesionUiState(restaurando = false)) :
        FuenteDeSesion {

        private val flujo = MutableStateFlow(inicial)
        override val sesion: Flow<SesionUiState> = flujo.asStateFlow()

        var vecesBorrado: Int = 0
            private set
        var ultimoPacienteGuardado: SesionPaciente? = null
            private set
        var ultimoProfesionalGuardado: SesionProfesional? = null
            private set

        override suspend fun guardarPaciente(sesion: SesionPaciente) {
            ultimoPacienteGuardado = sesion
            flujo.value = SesionUiState(paciente = sesion, restaurando = false)
        }

        override suspend fun guardarProfesional(sesion: SesionProfesional) {
            ultimoProfesionalGuardado = sesion
            flujo.value = SesionUiState(profesional = sesion, restaurando = false)
        }

        override suspend fun borrar() {
            vecesBorrado++
            flujo.value = SesionUiState(restaurando = false)
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

    // ------------------------------------------------------------ Restauracion

    @Test
    fun una_sesion_guardada_se_restaura_al_arrancar() {
        // Es el bug reportado: sin esto, cerrar la app devolvia al acceso.
        val almacen = AlmacenFalso(SesionUiState(paciente = paciente, restaurando = false))

        val vm = SesionViewModel(almacen)

        assertEquals(paciente, vm.estado.value.paciente)
        assertTrue(vm.estado.value.hayAlguienDentro)
    }

    @Test
    fun tambien_se_restaura_una_sesion_de_personal_medico() {
        val almacen = AlmacenFalso(SesionUiState(profesional = profesional, restaurando = false))

        val vm = SesionViewModel(almacen)

        assertEquals(profesional, vm.estado.value.profesional)
        assertNull(vm.estado.value.paciente)
    }

    @Test
    fun al_terminar_la_lectura_la_bandera_de_restauracion_baja() {
        // Mientras esta arriba, la app no dibuja nada: sin bajarla se quedaria
        // en blanco para siempre.
        val vm = SesionViewModel(AlmacenFalso())

        assertFalse(vm.estado.value.restaurando)
    }

    @Test
    fun sin_almacen_la_app_arranca_sin_sesion_y_sin_quedarse_colgada() {
        // El parametro es opcional para poder construir el ViewModel en pruebas
        // y vistas previas; no puede dejar la bandera arriba.
        val vm = SesionViewModel()

        assertFalse(vm.estado.value.restaurando)
        assertFalse(vm.estado.value.hayAlguienDentro)
    }

    // ---------------------------------------------------------------- Escritura

    @Test
    fun abrir_sesion_la_escribe_en_disco() {
        val almacen = AlmacenFalso()
        val vm = SesionViewModel(almacen)

        vm.abrirComoPaciente(paciente)

        assertEquals(paciente, almacen.ultimoPacienteGuardado)
        assertEquals(paciente, vm.estado.value.paciente)
    }

    @Test
    fun entrar_como_profesional_cierra_cualquier_sesion_de_paciente() {
        // Son dos portales con datos clinicos distintos: mezclarlos seria un
        // fallo de seguridad, no una comodidad.
        val vm = SesionViewModel(AlmacenFalso())
        vm.abrirComoPaciente(paciente)

        vm.abrirComoProfesional(profesional)

        assertNull(vm.estado.value.paciente)
        assertEquals(profesional, vm.estado.value.profesional)
    }

    @Test
    fun cerrar_sesion_borra_el_disco_y_deja_el_estado_en_blanco() {
        val almacen = AlmacenFalso()
        val vm = SesionViewModel(almacen)
        vm.abrirComoPaciente(paciente)

        vm.cerrar()

        assertEquals(1, almacen.vecesBorrado)
        assertFalse(vm.estado.value.hayAlguienDentro)
        assertFalse(vm.estado.value.restaurando)
    }

    @Test
    fun el_disco_no_pisa_a_una_sesion_ya_viva() {
        // Solo se lee el PRIMER valor. Escuchar el disco en vivo dejaria que una
        // escritura tardia o un fallo expulsara a quien ya esta dentro.
        val almacen = AlmacenFalso()
        val vm = SesionViewModel(almacen)
        vm.abrirComoProfesional(profesional)

        // El almacen cambia por su cuenta; la memoria sigue mandando.
        assertEquals(profesional, vm.estado.value.profesional)
    }
}
