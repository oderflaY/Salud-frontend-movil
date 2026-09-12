package com.eter.salud.domain.model

import kotlinx.serialization.json.Json

/**
 * Configuracion unica de serializacion del ecosistema.
 *
 * - `explicitNulls = false`: los campos que el paciente aun no captura no viajan
 *   como `null`, el backend en Go recibe solo lo que existe.
 * - `encodeDefaults = true`: las listas vacias si viajan, para que el backend
 *   pueda distinguir "sin alergias conocidas" de "no preguntado".
 * - `ignoreUnknownKeys = true`: el backend puede agregar nodos sin romper la app.
 */
val PacienteJson: Json = Json {
    explicitNulls = false
    encodeDefaults = true
    ignoreUnknownKeys = true
    prettyPrint = false
}
