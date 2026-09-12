package com.eter.salud.presentation.bandeja

import com.eter.salud.domain.model.AutorMensaje
import com.eter.salud.domain.model.Especialidad
import com.eter.salud.domain.model.MedicoVinculado
import com.eter.salud.domain.model.MensajeChat
import com.eter.salud.presentation.chat.ChatRepositorioFalso
import com.eter.salud.presentation.directorio.DirectorioMedicoRepositorioFalso
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Bandeja de conversaciones.
 *
 * Lo que se fija aqui es el CRUCE: el directorio sabe con quien esta vinculado
 * el paciente y el chat sabe que se dijo, y una fila necesita las dos cosas.
 * Tambien que la bandeja distinga "no tienes medicos" de "fallo la carga": son
 * dos pantallas distintas y confundirlas hace que un error de red parezca una
 * cuenta vacia.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BandejaViewModelTest {

    private val idPaciente = "pac_01H8X9A"

    private val elena = MedicoVinculado(
        idMedico = "doc_889900A",
        nombreCompleto = "Dra. Elena Ruiz Santos",
        especialidad = Especialidad.CARDIOLOGIA,
        idConversacion = "conv_pac_01H8X9A_doc_889900A",
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
        directorio: DirectorioMedicoRepositorioFalso = DirectorioMedicoRepositorioFalso(
            medicoVinculado = Result.success(elena),
        ),
        chat: ChatRepositorioFalso = ChatRepositorioFalso(),
    ) = BandejaViewModel(directorio, chat, idPaciente)

    @Test
    fun al_construirse_la_bandeja_se_llena_sola_sin_que_la_vista_lo_pida() {
        val estado = viewModel().estado.value

        val conConversaciones = assertIs<BandejaUiState.ConConversaciones>(estado)
        assertEquals(1, conConversaciones.conversaciones.size)
        assertEquals("Dra. Elena Ruiz Santos", conConversaciones.conversaciones.single().nombreMedico)
    }

    @Test
    fun refrescar_trae_al_medico_escogido_en_el_directorio_sin_pasar_por_cargando() = runTest {
        val directorio = DirectorioMedicoRepositorioFalso(medicoVinculado = Result.success(elena))
        val vm = viewModel(directorio = directorio)
        val estados = mutableListOf<BandejaUiState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.estado.toList(estados) }

        directorio.solicitarVinculacion(idPaciente, "doc_local_2")
        vm.refrescar()

        val estado = assertIs<BandejaUiState.ConConversaciones>(vm.estado.value)
        assertEquals("Dr. Carlos Mendoza", estado.conversaciones.single().nombreMedico)
        // Con filas ya pintadas, volver a la pestana no las cambia por un esqueleto.
        assertTrue(estados.none { it is BandejaUiState.Cargando })
    }

    @Test
    fun la_fila_muestra_el_ULTIMO_mensaje_de_la_conversacion() {
        val chat = ChatRepositorioFalso(
            historial = Result.success(
                listOf(
                    MensajeChat("m1", AutorMensaje.MEDICO, "Primero", "2026-09-07T10:00:00Z"),
                    MensajeChat("m2", AutorMensaje.PACIENTE, "Ultimo", "2026-09-07T11:00:00Z"),
                ),
            ),
        )

        val estado = assertIs<BandejaUiState.ConConversaciones>(viewModel(chat = chat).estado.value)

        val fila = estado.conversaciones.single()
        assertEquals("Ultimo", fila.ultimoMensaje)
        // El autor viaja para que la Vista pueda prefijar "Tu:" cuando toca.
        assertEquals(AutorMensaje.PACIENTE, fila.autorUltimoMensaje)
    }

    @Test
    fun sin_medicos_vinculados_se_publica_el_estado_vacio_y_no_una_lista_en_blanco() {
        // Es una invitacion a buscar el primer especialista, no una pantalla que
        // parece un fallo de carga.
        val directorio = DirectorioMedicoRepositorioFalso(medicoVinculado = Result.success(null))

        assertEquals(BandejaUiState.SinConversaciones, viewModel(directorio).estado.value)
    }

    @Test
    fun si_falla_la_consulta_de_vinculados_se_distingue_del_estado_vacio() {
        val directorio = DirectorioMedicoRepositorioFalso(
            medicoVinculado = Result.failure(IllegalStateException("sin red")),
        )

        assertEquals(BandejaUiState.Error, viewModel(directorio).estado.value)
    }

    @Test
    fun una_conversacion_sin_mensajes_no_deja_una_fila_muda_en_la_lista() {
        // Una fila sin ultimo mensaje seria un hueco vacio: mejor no pintarla.
        val chat = ChatRepositorioFalso(historial = Result.success(emptyList()))

        assertEquals(BandejaUiState.SinConversaciones, viewModel(chat = chat).estado.value)
    }

    @Test
    fun el_medico_se_reconstruye_desde_la_fila_para_poder_abrir_su_chat() {
        val vm = viewModel()
        val fila = assertIs<BandejaUiState.ConConversaciones>(vm.estado.value).conversaciones.single()

        assertEquals(elena, vm.medicoDe(fila))
    }
}
