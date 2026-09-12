package com.eter.salud.presentation.comun

import kotlin.coroutines.cancellation.CancellationException

/**
 * Blindaje de las llamadas a repositorio dentro de `viewModelScope.launch`.
 *
 * Los repositorios devuelven `Result`, pero eso solo cubre los fallos que ELLOS
 * deciden representar como valor. Una excepcion lanzada de verdad -- un timeout
 * del cliente HTTP que sustituira a los mocks, un fallo de serializacion, un
 * error de base de datos -- escapa del `launch`, no la atrapa nadie y cierra la
 * app de golpe. Este envoltorio la convierte en `Result.failure`, que cada
 * ViewModel ya sabe traducir a su estado de Error.
 *
 * [CancellationException] se relanza a proposito: es como las corrutinas se
 * cancelan entre si. Tragarsela romperia la concurrencia estructurada y dejaria
 * trabajo huerfano corriendo tras cerrar la pantalla.
 */
internal suspend fun <T> ejecutarSeguro(bloque: suspend () -> Result<T>): Result<T> =
    try {
        bloque()
    } catch (cancelacion: CancellationException) {
        throw cancelacion
    } catch (error: Throwable) {
        Result.failure(error)
    }
