package com.eter.salud.domain.time

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Implementacion Android basada en [SimpleDateFormat] para mantener
 * compatibilidad con minSdk 24 (java.time exige API 26 o desugaring).
 */
private class RelojAndroid : RelojSalud {

    private val formatoFecha = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    private val formatoInstante = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    /** Hora corta en la zona horaria del dispositivo, sin forzar `timeZone`. */
    private val formatoHoraLocal = SimpleDateFormat("HH:mm", Locale.US)

    override fun fechaHoy(): String = formatoFecha.format(Date())

    override fun instanteActual(): String = formatoInstante.format(Date())

    override fun horaLocal(instanteIso: String): String = runCatching {
        formatoInstante.parse(instanteIso)?.let(formatoHoraLocal::format).orEmpty()
    }.getOrDefault("")
}

actual fun relojDelSistema(): RelojSalud = RelojAndroid()
