package com.eter.salud.presentation.avisos

import com.eter.salud.domain.model.AutorMensaje
import com.eter.salud.domain.model.MensajeChat
import com.eter.salud.domain.model.ResumenClinicoIa
import com.eter.salud.domain.repository.ChatRepositorio
import com.eter.salud.domain.repository.MensajeEntrante
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Solo lo que importa aqui: los avisos de mensaje nuevo por conversacion. */
private class ChatConAvisos : ChatRepositorio {
    private val entrantes = MutableSharedFlow<Pair<String, MensajeEntrante>>(extraBufferCapacity = 16)

    fun llega(idConversacion: String, idMensaje: String, autor: AutorMensaje, texto: String = "Hola") {
        entrantes.tryEmit(idConversacion to MensajeEntrante(idMensaje, autor, texto))
    }

    override fun mensajesEntrantes(idConversacion: String): Flow<MensajeEntrante> =
        entrantes.filter { it.first == idConversacion }.map { it.second }

    override suspend fun obtenerHistorial(idConversacion: String) = Result.success(emptyList<MensajeChat>())
    override suspend fun enviarMensaje(
        idConversacion: String,
        texto: String,
        instante: String,
        autor: AutorMensaje,
        adjunto: com.eter.salud.domain.model.Adjunto?,
    ): Result<MensajeChat> = Result.failure(UnsupportedOperationException())
    override fun mensajesSinLeer(idConversacion: String): Flow<Int> = emptyFlow()
    override suspend fun marcarConversacionLeida(idConversacion: String) = Unit
    override suspend fun obtenerRespuestaAutomatica(idConversacion: String, instante: String): Result<MensajeChat> =
        Result.failure(UnsupportedOperationException())
    override suspend fun obtenerResumenClinico(idMensaje: String): Result<ResumenClinicoIa> =
        Result.failure(UnsupportedOperationException())
}

class VigilanteDeMensajesTest {

    private val conDoctor = ConversacionVigilada("conv_1", "Dr. Carlos Silva")
    private val conOtro = ConversacionVigilada("conv_2", "Dra. Maria Lopez")

    @Test
    fun avisa_lo_que_escribe_la_otra_parte_con_su_nombre() = runTest(UnconfinedTestDispatcher()) {
        val chat = ChatConAvisos()
        val avisados = mutableListOf<MensajeParaAvisar>()
        val escucha = launch {
            VigilanteDeMensajes(chat, AutorMensaje.PACIENTE)
                .mensajesParaAvisar(listOf(conDoctor, conOtro)) { null }
                .collect { avisados += it }
        }

        chat.llega("conv_2", "m1", AutorMensaje.MEDICO, "Lo mejor seria que vinieras a consulta")

        assertEquals(listOf("Dra. Maria Lopez"), avisados.map { it.conversacion.nombre })
        assertEquals("Lo mejor seria que vinieras a consulta", avisados.single().mensaje.texto)
        escucha.cancel()
    }

    @Test
    fun no_avisa_lo_que_uno_mismo_escribio() = runTest(UnconfinedTestDispatcher()) {
        val chat = ChatConAvisos()
        val avisados = mutableListOf<MensajeParaAvisar>()
        val escucha = launch {
            VigilanteDeMensajes(chat, AutorMensaje.PACIENTE).mensajesParaAvisar(listOf(conDoctor)) { null }.collect { avisados += it }
        }

        chat.llega("conv_1", "m1", AutorMensaje.PACIENTE)

        assertEquals(0, avisados.size)
        escucha.cancel()
    }

    @Test
    fun no_suena_por_la_conversacion_que_se_esta_leyendo() = runTest(UnconfinedTestDispatcher()) {
        val chat = ChatConAvisos()
        val avisados = mutableListOf<MensajeParaAvisar>()
        var aLaVista: String? = "conv_1"
        val escucha = launch {
            VigilanteDeMensajes(chat, AutorMensaje.PACIENTE).mensajesParaAvisar(listOf(conDoctor)) { aLaVista }.collect { avisados += it }
        }

        chat.llega("conv_1", "m1", AutorMensaje.MEDICO)
        aLaVista = null
        chat.llega("conv_1", "m2", AutorMensaje.MEDICO)

        assertEquals(listOf("m2"), avisados.map { it.mensaje.idMensaje })
        escucha.cancel()
    }

    @Test
    fun el_mismo_mensaje_repetido_se_avisa_una_sola_vez() = runTest(UnconfinedTestDispatcher()) {
        val chat = ChatConAvisos()
        val avisados = mutableListOf<MensajeParaAvisar>()
        val escucha = launch {
            VigilanteDeMensajes(chat, AutorMensaje.MEDICO).mensajesParaAvisar(listOf(conDoctor)) { null }.collect { avisados += it }
        }

        chat.llega("conv_1", "m1", AutorMensaje.PACIENTE)
        chat.llega("conv_1", "m1", AutorMensaje.PACIENTE)

        assertEquals(1, avisados.size)
        escucha.cancel()
    }
}
