package com.eter.salud.data.repository

import com.eter.salud.data.red.URL_BASE_DE_PRUEBA
import com.eter.salud.data.red.clienteDePrueba
import com.eter.salud.domain.model.Especialidad
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DirectorioMedicoRepositorioRemotoTest {

    @Test
    fun medico_vinculado_ausente_es_null_y_no_un_fallo() = runTest {
        // El RPC devuelve siempre una fila, con todos los campos en null cuando
        // el paciente no tiene vinculo. Nunca un 404.
        val cliente = clienteDePrueba {
            respond(
                content = """{"idMedico":null,"nombreCompleto":null,"especialidad":null,"idConversacion":null}""",
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", "application/json"),
            )
        }
        val repositorio = DirectorioMedicoRepositorioRemoto(cliente, URL_BASE_DE_PRUEBA)

        val resultado = repositorio.obtenerMedicoVinculado("pac_1")

        assertTrue(resultado.isSuccess)
        assertNull(resultado.getOrThrow())
    }

    @Test
    fun busca_en_el_directorio_filtrando_por_especialidad() = runTest {
        val cliente = clienteDePrueba { peticion ->
            // `eq.` es la sintaxis de filtro de PostgREST, no parte del valor.
            assertEquals("eq.CARDIOLOGIA", peticion.url.parameters["especialidad"])
            respond(
                content = """[{"idMedico":"doc_1","nombreCompleto":"Dra. Ruiz","especialidad":"CARDIOLOGIA","cedulaVerificada":true}]""",
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", "application/json"),
            )
        }
        val repositorio = DirectorioMedicoRepositorioRemoto(cliente, URL_BASE_DE_PRUEBA)

        val doctores = repositorio.buscarDirectorio(Especialidad.CARDIOLOGIA).getOrThrow()

        assertEquals("doc_1", doctores.single().idMedico)
    }

    @Test
    fun perfil_de_medico_dado_de_baja_es_null() = runTest {
        // La vista de PostgREST responde 200 con un array vacio, no 404.
        val cliente = clienteDePrueba {
            respond(
                content = "[]",
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", "application/json"),
            )
        }
        val repositorio = DirectorioMedicoRepositorioRemoto(cliente, URL_BASE_DE_PRUEBA)

        val resultado = repositorio.obtenerPerfilDeMedico("doc_baja")

        assertTrue(resultado.isSuccess)
        assertNull(resultado.getOrThrow())
    }

    // Respuesta real del backend para un medico que aun no lleno su perfil de
    // directorio (todo medico recien registrado). Antes tumbaba la lista entera:
    // un solo perfil incompleto dejaba al paciente sin medicos ni mensajes.
    @Test
    fun un_medico_sin_perfil_completo_no_tumba_el_directorio() = runTest {
        val cliente = clienteDePrueba {
            respond(
                content = """[{"idMedico":"doc_1","nombreCompleto":"Dr. Carlos Silva","especialidad":null,"cedulaVerificada":true,"universidad":null,"disponibilidad":null}]""",
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", "application/json"),
            )
        }
        val repositorio = DirectorioMedicoRepositorioRemoto(cliente, URL_BASE_DE_PRUEBA)

        val doctor = repositorio.buscarDirectorio(null).getOrThrow().single()

        assertNull(doctor.especialidad)
        assertEquals("", doctor.universidad)
        assertEquals("", doctor.disponibilidad)
    }

    @Test
    fun medicos_vinculados_sin_especialidad_no_tumban_la_bandeja() = runTest {
        val cliente = clienteDePrueba {
            respond(
                content = """[{"idMedico":"doc_1","nombreCompleto":"Dr. Carlos Silva","especialidad":null,"idConversacion":"conv_1"}]""",
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", "application/json"),
            )
        }
        val repositorio = DirectorioMedicoRepositorioRemoto(cliente, URL_BASE_DE_PRUEBA)

        val medico = repositorio.obtenerMedicosVinculados("pac_1").getOrThrow().single()

        assertEquals("conv_1", medico.idConversacion)
        assertNull(medico.especialidad)
    }

    @Test
    fun el_medico_vinculado_sin_especialidad_no_se_la_inventa() = runTest {
        val cliente = clienteDePrueba {
            respond(
                content = """{"idMedico":"doc_1","nombreCompleto":"Dr. Carlos Silva","especialidad":null,"idConversacion":"conv_1"}""",
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", "application/json"),
            )
        }
        val repositorio = DirectorioMedicoRepositorioRemoto(cliente, URL_BASE_DE_PRUEBA)

        // Antes caia a MEDICINA_GENERAL: una especialidad falsa en una app de salud.
        assertNull(repositorio.obtenerMedicoVinculado("pac_1").getOrThrow()?.especialidad)
    }

    @Test
    fun solicita_vinculacion_con_el_medico_elegido() = runTest {
        val cliente = clienteDePrueba {
            respond(
                content = """{"idMedico":"doc_1","nombreCompleto":"Dra. Ruiz","especialidad":"CARDIOLOGIA","idConversacion":"conv_1"}""",
                status = HttpStatusCode.Created,
                headers = headersOf("Content-Type", "application/json"),
            )
        }
        val repositorio = DirectorioMedicoRepositorioRemoto(cliente, URL_BASE_DE_PRUEBA)

        val vinculo = repositorio.solicitarVinculacion("pac_1", "doc_1").getOrThrow()

        assertEquals("conv_1", vinculo.idConversacion)
    }
}
