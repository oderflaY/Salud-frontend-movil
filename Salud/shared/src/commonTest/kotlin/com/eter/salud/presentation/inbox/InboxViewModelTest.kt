package com.eter.salud.presentation.inbox

import com.eter.salud.domain.diario.SeveridadDiario
import com.eter.salud.domain.model.Adjunto
import com.eter.salud.domain.model.AutorMensaje
import com.eter.salud.domain.model.MensajeChat
import com.eter.salud.domain.model.PacienteVinculado
import com.eter.salud.domain.model.ResumenClinicoIa
import com.eter.salud.domain.model.RiesgoPaciente
import com.eter.salud.domain.repository.ChatRepositorio
import com.eter.salud.domain.repository.ResumenIaNoDisponible
import com.eter.salud.presentation.profesional.DiarioParaPanelFalso
import com.eter.salud.presentation.profesional.PacientesVinculadosRepositorioFalso
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * Bandeja clinica del medico.
 *
 * Lo que se fija aqui es el ORDEN, porque es la mitad del triage: el punto de
 * color solo confirma lo que la posicion ya dijo. Un fallo que dejara a un
 * paciente critico en mitad de la lista no se veria en ninguna captura de
 * pantalla, y es exactamente el fallo que no puede ocurrir.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InboxViewModelTest {

    private val idMedico = "doc_889900A"

    private fun paciente(id: String, riesgo: RiesgoPaciente, canal: String = "conv_$id") = PacienteVinculado(
        idPaciente = id,
        nombreCompleto = "Paciente $id",
        riesgo = riesgo,
        idConversacion = canal,
    )

    @BeforeTest
    fun configurar() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun limpiar() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        pacientes: Result<List<PacienteVinculado>>,
        historiales: Map<String, Result<List<MensajeChat>>> = emptyMap(),
        severidades: Map<String, SeveridadDiario> = emptyMap(),
    ) = InboxViewModel(
        pacientesVinculados = PacientesVinculadosRepositorioFalso(pacientes),
        chat = ChatPorConversacionFalso(historiales),
        diario = DiarioParaPanelFalso(severidades),
        idMedico = idMedico,
    )

    private fun InboxViewModel.idsEnOrden(): List<String> =
        assertIs<InboxUiState.ConConversaciones>(estado.value).conversaciones.map { it.paciente.idPaciente }

    @Test
    fun el_paciente_critico_va_arriba_aunque_haya_escrito_hace_horas() {
        val vm = viewModel(
            pacientes = Result.success(
                listOf(
                    paciente("estable", RiesgoPaciente.BAJO),
                    paciente("critico", RiesgoPaciente.ALTO),
                    paciente("vigilancia", RiesgoPaciente.MEDIO),
                ),
            ),
            historiales = mapOf(
                "conv_estable" to Result.success(listOf(mensaje("2026-09-10T18:00:00Z"))),
                "conv_critico" to Result.success(listOf(mensaje("2026-09-10T08:00:00Z"))),
                "conv_vigilancia" to Result.success(listOf(mensaje("2026-09-10T12:00:00Z"))),
            ),
        )

        assertEquals(listOf("critico", "vigilancia", "estable"), vm.idsEnOrden())
    }

    @Test
    fun dentro_de_un_mismo_nivel_desempata_lo_mas_reciente() {
        val vm = viewModel(
            pacientes = Result.success(
                listOf(
                    paciente("antiguo", RiesgoPaciente.BAJO),
                    paciente("sin_mensajes", RiesgoPaciente.BAJO),
                    paciente("reciente", RiesgoPaciente.BAJO),
                ),
            ),
            historiales = mapOf(
                "conv_antiguo" to Result.success(listOf(mensaje("2026-09-09T09:00:00Z"))),
                "conv_reciente" to Result.success(listOf(mensaje("2026-09-10T09:00:00Z"))),
            ),
        )

        assertEquals(listOf("reciente", "antiguo", "sin_mensajes"), vm.idsEnOrden())
    }

    @Test
    fun un_diario_en_rojo_sube_a_critico_a_un_paciente_de_riesgo_bajo() {
        val vm = viewModel(
            pacientes = Result.success(
                listOf(
                    paciente("expediente_alto", RiesgoPaciente.ALTO),
                    paciente("diario_rojo", RiesgoPaciente.BAJO),
                ),
            ),
            severidades = mapOf("diario_rojo" to SeveridadDiario.ROJO),
        )

        val filas = assertIs<InboxUiState.ConConversaciones>(vm.estado.value).conversaciones
        assertEquals(NivelTriage.CRITICO, filas.single { it.paciente.idPaciente == "diario_rojo" }.nivel)
    }

    @Test
    fun un_diario_en_verde_no_baja_el_riesgo_del_expediente() {
        // El maximo, no el promedio: escribir "me siento bien" no borra un
        // expediente de riesgo alto.
        assertEquals(
            NivelTriage.CRITICO,
            NivelTriage.combinar(RiesgoPaciente.ALTO, SeveridadDiario.VERDE),
        )
        assertEquals(
            NivelTriage.VIGILANCIA,
            NivelTriage.combinar(RiesgoPaciente.MEDIO, null),
        )
    }

    @Test
    fun si_el_historial_falla_la_fila_sigue_en_la_bandeja_sin_vista_previa() {
        val vm = viewModel(
            pacientes = Result.success(listOf(paciente("sin_historial", RiesgoPaciente.ALTO))),
            historiales = mapOf("conv_sin_historial" to Result.failure(IllegalStateException("red caida"))),
        )

        val fila = assertIs<InboxUiState.ConConversaciones>(vm.estado.value).conversaciones.single()
        assertEquals("sin_historial", fila.paciente.idPaciente)
        assertNull(fila.ultimoMensaje)
    }

    @Test
    fun la_fila_guarda_el_ULTIMO_mensaje_y_quien_lo_escribio() {
        val vm = viewModel(
            pacientes = Result.success(listOf(paciente("p1", RiesgoPaciente.BAJO))),
            historiales = mapOf(
                "conv_p1" to Result.success(
                    listOf(
                        mensaje("2026-09-10T08:00:00Z", "Me duele la cabeza", AutorMensaje.PACIENTE),
                        mensaje("2026-09-10T08:05:00Z", "Tome paracetamol y avise", AutorMensaje.MEDICO),
                    ),
                ),
            ),
        )

        val fila = assertIs<InboxUiState.ConConversaciones>(vm.estado.value).conversaciones.single()
        assertEquals("Tome paracetamol y avise", fila.ultimoMensaje)
        assertEquals(AutorMensaje.MEDICO, fila.autorUltimoMensaje)
    }

    @Test
    fun los_pacientes_sin_canal_de_chat_no_entran_en_la_bandeja() {
        val vm = viewModel(
            pacientes = Result.success(listOf(paciente("sin_canal", RiesgoPaciente.ALTO, canal = ""))),
        )

        assertEquals(InboxUiState.SinConversaciones, vm.estado.value)
    }

    @Test
    fun si_falla_la_cartera_se_distingue_del_estado_vacio() {
        val vm = viewModel(pacientes = Result.failure(IllegalStateException("sin red")))

        assertEquals(InboxUiState.Error, vm.estado.value)
    }

    private fun mensaje(
        instante: String,
        texto: String = "Mensaje de prueba",
        autor: AutorMensaje = AutorMensaje.PACIENTE,
    ) = MensajeChat(idMensaje = "msg_$instante", autor = autor, texto = texto, instante = instante)
}

/**
 * Chat falso con un historial POR conversacion.
 *
 * El doble compartido devuelve el mismo historial para todas, y el orden por
 * recencia no se puede probar si todas las filas tienen el mismo ultimo mensaje.
 */
private class ChatPorConversacionFalso(
    private val historiales: Map<String, Result<List<MensajeChat>>>,
) : ChatRepositorio {

    override suspend fun obtenerHistorial(idConversacion: String): Result<List<MensajeChat>> =
        historiales[idConversacion] ?: Result.success(emptyList())

    override suspend fun enviarMensaje(
        idConversacion: String,
        texto: String,
        instante: String,
        autor: AutorMensaje,
        adjunto: Adjunto?,
    ): Result<MensajeChat> = Result.failure(UnsupportedOperationException("La bandeja no envia"))

    override fun mensajesSinLeer(idConversacion: String): Flow<Int> = flowOf(0)

    override suspend fun marcarConversacionLeida(idConversacion: String) = Unit

    override suspend fun obtenerRespuestaAutomatica(
        idConversacion: String,
        instante: String,
    ): Result<MensajeChat> = Result.failure(UnsupportedOperationException("La bandeja no responde"))

    override suspend fun obtenerResumenClinico(idMensaje: String): Result<ResumenClinicoIa> =
        Result.failure(ResumenIaNoDisponible())
}
