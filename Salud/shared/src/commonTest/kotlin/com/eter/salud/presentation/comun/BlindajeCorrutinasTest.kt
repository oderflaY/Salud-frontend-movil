package com.eter.salud.presentation.comun

import com.eter.salud.domain.model.Especialidad
import com.eter.salud.domain.model.MedicoVinculado
import com.eter.salud.domain.model.PacienteDto
import com.eter.salud.domain.model.PerfilDoctorDirectorio
import com.eter.salud.domain.repository.DirectorioMedicoRepositorio
import com.eter.salud.domain.repository.PacienteRepositorio
import com.eter.salud.presentation.directorio.DescubrimientoMedicoViewModel
import com.eter.salud.presentation.directorio.DirectorioUiState
import com.eter.salud.presentation.onboarding.OnboardingPacienteViewModel
import com.eter.salud.presentation.onboarding.ErrorEnvio
import com.eter.salud.presentation.onboarding.RelojFijo
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
import kotlin.test.assertIs

/**
 * Blindaje contra cierres inesperados.
 *
 * Los repositorios de hoy devuelven `Result`, pero el cliente HTTP que los va a
 * sustituir LANZA de verdad ante un timeout o un fallo de serializacion. Una
 * excepcion suelta dentro de `viewModelScope.launch` no la atrapa nadie y cierra
 * la app. Estas pruebas usan repositorios que lanzan a proposito y exigen que la
 * pantalla acabe en su estado de Error, nunca con la excepcion propagandose.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BlindajeCorrutinasTest {

    @BeforeTest
    fun configurar() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun limpiar() {
        Dispatchers.resetMain()
    }

    /** Repositorio que revienta en vez de devolver `Result.failure`. */
    private class PacienteRepositorioQueLanza : PacienteRepositorio {
        override suspend fun registrarPaciente(paciente: PacienteDto): Result<String> =
            throw IllegalStateException("el cliente HTTP reventó")
    }

    private class DirectorioQueLanza : DirectorioMedicoRepositorio {
        override suspend fun obtenerMedicoVinculado(idPaciente: String): Result<MedicoVinculado?> =
            throw IllegalStateException("timeout")

        override suspend fun buscarDirectorio(
            especialidad: Especialidad?,
        ): Result<List<PerfilDoctorDirectorio>> = throw IllegalStateException("timeout")

        override suspend fun obtenerMedicosVinculados(
            idPaciente: String,
        ): Result<List<MedicoVinculado>> = throw IllegalStateException("timeout")

        override suspend fun obtenerPerfilDeMedico(
            idMedico: String,
        ): Result<PerfilDoctorDirectorio?> = throw IllegalStateException("timeout")

        override suspend fun solicitarVinculacion(
            idPaciente: String,
            idMedico: String,
        ): Result<MedicoVinculado> = throw IllegalStateException("timeout")
    }

    @Test
    fun si_el_guardado_del_cuestionario_lanza_la_app_no_se_cierra_y_muestra_el_error() {
        val vm = OnboardingPacienteViewModel(
            repositorio = PacienteRepositorioQueLanza(),
            reloj = RelojFijo(),
        )

        // Sin datos criticos capturados, el envio se confirma explicitamente.
        vm.confirmarEnvioConCamposCriticosOmitidos()

        val estado = vm.estado.value
        assertFalse(estado.enviando)
        assertFalse(estado.envioExitoso)
        assertEquals(ErrorEnvio.SIN_CONEXION, estado.errorEnvio)
    }

    @Test
    fun si_el_directorio_lanza_la_pantalla_queda_en_error_y_no_revienta() {
        val vm = DescubrimientoMedicoViewModel(
            repositorio = DirectorioQueLanza(),
            idPaciente = "pac_01H8X9A",
        )

        assertIs<DirectorioUiState.Error>(vm.estado.value.directorio)
    }

    @Test
    fun si_la_vinculacion_lanza_el_paciente_sigue_en_el_directorio() {
        val vm = DescubrimientoMedicoViewModel(
            repositorio = DirectorioQueLanza(),
            idPaciente = "pac_01H8X9A",
        )

        vm.solicitarVinculacion("doc_889900A")

        val estado = vm.estado.value
        assertEquals(true, estado.errorVinculacion)
        assertEquals(null, estado.medicoVinculado)
        assertEquals(null, estado.idSolicitandoVinculacion)
    }
}
