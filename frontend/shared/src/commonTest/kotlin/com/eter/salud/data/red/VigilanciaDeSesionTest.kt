package com.eter.salud.data.red

import com.eter.salud.data.sesion.AvisoDeSesion
import com.eter.salud.data.sesion.TipoAvisoDeSesion
import com.eter.salud.domain.model.SesionPaciente
import com.eter.salud.presentation.sesion.SesionUiState
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * El cliente avisa UNA vez, en un solo sitio, cuando el backend rechaza la
 * sesion y no solo una peticion (reset de cuenta desde el panel del medico,
 * cuenta bloqueada o dada de baja).
 */
class VigilanciaDeSesionTest {

    private val sesion = SesionUiState(
        paciente = SesionPaciente(idPaciente = "pac_1", token = "jwt_vigente", requiereOnboarding = false),
        restaurando = false,
    )

    private fun respuestaDeError(codigo: HttpStatusCode, motivo: String) =
        Triple(codigo, """{"code":"PT${codigo.value}","message":"$motivo"}""", headersOf("Content-Type", "application/json"))

    private suspend fun avisosTras(codigo: HttpStatusCode, motivo: String, conSesion: Boolean = true): List<AvisoDeSesion> {
        val avisos = mutableListOf<AvisoDeSesion>()
        val (status, cuerpo, cabeceras) = respuestaDeError(codigo, motivo)
        val cliente = clienteDePrueba(
            fuenteDeSesion = if (conSesion) FuenteDeSesionFalsa(sesion) else FuenteDeSesionFalsa(),
            alRechazarSesion = { avisos += it },
        ) { respond(cuerpo, status, cabeceras) }
        cliente.get("$URL_BASE_DE_PRUEBA/paciente_expediente")
        return avisos
    }

    @Test
    fun una_sesion_revocada_avisa_con_el_token_que_se_rechazo() = runTest {
        assertEquals(
            listOf(AvisoDeSesion(TipoAvisoDeSesion.CERRADA, "jwt_vigente")),
            avisosTras(HttpStatusCode.Unauthorized, "SESION_REVOCADA"),
        )
    }

    @Test
    fun una_cuenta_inactiva_tambien_cierra_la_sesion() = runTest {
        assertEquals(TipoAvisoDeSesion.CERRADA, avisosTras(HttpStatusCode.Unauthorized, "CUENTA_INACTIVA").single().tipo)
    }

    @Test
    fun la_contrasena_temporal_pendiente_pide_cambiarla() = runTest {
        assertEquals(
            TipoAvisoDeSesion.CAMBIO_DE_CONTRASENA_REQUERIDO,
            avisosTras(HttpStatusCode.Forbidden, "CAMBIO_CONTRASENA_REQUERIDO").single().tipo,
        )
    }

    @Test
    fun un_no_autorizado_de_negocio_no_saca_a_nadie() = runTest {
        // Abrir una conversacion ajena, por ejemplo: la sesion sigue siendo buena.
        assertTrue(avisosTras(HttpStatusCode.Unauthorized, "NO_AUTORIZADO").isEmpty())
    }

    @Test
    fun sin_sesion_no_hay_nada_que_cerrar() = runTest {
        assertTrue(avisosTras(HttpStatusCode.Unauthorized, "SESION_REVOCADA", conSesion = false).isEmpty())
    }

    @Test
    fun una_respuesta_correcta_no_avisa() = runTest {
        val avisos = mutableListOf<AvisoDeSesion>()
        val cliente = clienteDePrueba(FuenteDeSesionFalsa(sesion), alRechazarSesion = { avisos += it }) {
            respond("[]", HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
        }
        cliente.get("$URL_BASE_DE_PRUEBA/paciente_expediente")
        assertTrue(avisos.isEmpty())
    }

    @Test
    fun el_repositorio_sigue_leyendo_el_motivo_despues_del_aviso() = runTest {
        // El aviso lee el cuerpo; el repositorio tiene que poder leerlo otra vez.
        val avisos = mutableListOf<AvisoDeSesion>()
        val (status, cuerpo, cabeceras) = respuestaDeError(HttpStatusCode.Unauthorized, "SESION_REVOCADA")
        val cliente = clienteDePrueba(FuenteDeSesionFalsa(sesion), alRechazarSesion = { avisos += it }) {
            respond(cuerpo, status, cabeceras)
        }

        val respuesta = cliente.post("$URL_BASE_DE_PRUEBA/rpc/obtener_historial")

        assertEquals(1, avisos.size)
        assertEquals("SESION_REVOCADA", respuesta.motivoDeError<MotivoDePrueba>()?.name)
    }

    private enum class MotivoDePrueba { SESION_REVOCADA }
}
