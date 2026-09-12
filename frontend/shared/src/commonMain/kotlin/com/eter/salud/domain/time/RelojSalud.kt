package com.eter.salud.domain.time

/**
 * Fuente de tiempo inyectable. Se abstrae para que la logica de presentacion sea
 * determinista en las pruebas (DM_Arquitectura_App.md, seccion 2: TDD).
 */
interface RelojSalud {

    /** Fecha de calendario local en formato `YYYY-MM-DD`. */
    fun fechaHoy(): String

    /** Instante actual en ISO 8601 UTC (`YYYY-MM-DDTHH:MM:SSZ`). */
    fun instanteActual(): String

    /**
     * Convierte un instante ISO 8601 UTC a la hora local del dispositivo en
     * formato corto (`HH:MM`), para las marcas de tiempo del chat.
     *
     * La conversion vive en la plataforma y no en codigo comun a proposito:
     * requiere la zona horaria del sistema, que Kotlin comun no conoce. Si el
     * texto no es un instante valido devuelve cadena vacia, porque una hora
     * inventada bajo un mensaje clinico es peor que ninguna hora.
     */
    fun horaLocal(instanteIso: String): String
}

/** Reloj real de la plataforma (Android / iOS). */
expect fun relojDelSistema(): RelojSalud
