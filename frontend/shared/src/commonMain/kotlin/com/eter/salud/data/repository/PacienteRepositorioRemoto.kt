package com.eter.salud.data.repository

import com.eter.salud.data.red.FalloDeRedGenerico
import com.eter.salud.domain.model.PacienteDto
import com.eter.salud.domain.repository.PacienteRepositorio
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonPrimitive

/**
 * `PacienteRepositorio` contra el backend real (`mapeo-endpoints.md`, seccion
 * 3): la funcion `registrar_paciente` de PostgREST. Sin motivo tipado: el
 * onboarding solo distingue exito de fallo, nunca una causa concreta que la
 * Vista deba mostrar distinta.
 */
class PacienteRepositorioRemoto(
    private val cliente: HttpClient,
    private val baseUrl: String,
) : PacienteRepositorio {

    override suspend fun registrarPaciente(paciente: PacienteDto): Result<String> {
        val respuesta = cliente.post("$baseUrl/rpc/registrar_paciente") {
            contentType(ContentType.Application.Json)
            // Un solo parametro jsonb: el body es el DTO completo, en camelCase.
            header("Prefer", "params=single-object")
            setBody(paciente)
        }
        return if (respuesta.status.isSuccess()) {
            // La funcion devuelve el idPaciente como escalar JSON, no envuelto
            // en un objeto: `"pac_xxx"`, no `{"idPaciente": "pac_xxx"}`.
            //
            // Se deserializa como JsonElement y NO con `body<String>()`: Ktor
            // trata String como un caso especial y entrega el cuerpo crudo sin
            // pasar por content negotiation, con lo que el id se quedaba con
            // las comillas del JSON pegadas (`"pac_xxx"`). Ese id viajaba
            // despues en cada filtro (`idPaciente=eq."pac_xxx"`) y no casaba
            // con ninguna fila.
            Result.success(respuesta.body<JsonElement>().jsonPrimitive.content)
        } else {
            Result.failure(FalloDeRedGenerico(respuesta.status.value))
        }
    }
}
