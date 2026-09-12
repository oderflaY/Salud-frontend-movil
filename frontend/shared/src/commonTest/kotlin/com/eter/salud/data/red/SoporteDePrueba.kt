package com.eter.salud.data.red

import com.eter.salud.data.sesion.FuenteDeSesion
import com.eter.salud.domain.model.SesionPaciente
import com.eter.salud.domain.model.SesionProfesional
import com.eter.salud.presentation.sesion.SesionUiState
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Sesion en memoria para las pruebas de red: mismo contrato, sin tocar disco. */
class FuenteDeSesionFalsa(inicial: SesionUiState = SesionUiState(restaurando = false)) : FuenteDeSesion {
    private val flujo = MutableStateFlow(inicial)
    override val sesion: Flow<SesionUiState> = flujo.asStateFlow()

    override suspend fun guardarPaciente(sesion: SesionPaciente) {
        flujo.value = SesionUiState(paciente = sesion, restaurando = false)
    }

    override suspend fun guardarProfesional(sesion: SesionProfesional) {
        flujo.value = SesionUiState(profesional = sesion, restaurando = false)
    }

    override suspend fun borrar() {
        flujo.value = SesionUiState(restaurando = false)
    }
}

/**
 * Cliente HTTP identico al de produccion (mismos complementos, mismo JSON),
 * pero sobre un [MockEngine]: cada repositorio `*Remoto` se prueba contra
 * peticiones reales de Ktor sin tocar la red.
 */
fun clienteDePrueba(
    fuenteDeSesion: FuenteDeSesion = FuenteDeSesionFalsa(),
    manejador: MockRequestHandleScope.(request: HttpRequestData) -> HttpResponseData,
): HttpClient = HttpClient(MockEngine) {
    configurarPluginsRed(fuenteDeSesion)
    engine {
        addHandler(manejador)
    }
}

const val URL_BASE_DE_PRUEBA = "https://api.prueba.local"
