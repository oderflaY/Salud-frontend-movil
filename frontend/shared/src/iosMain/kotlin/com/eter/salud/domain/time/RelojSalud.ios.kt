package com.eter.salud.domain.time

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.NSTimeZone
import platform.Foundation.timeZoneWithAbbreviation

/**
 * Implementacion iOS con [NSDateFormatter]. Se usa el locale `en_US_POSIX`
 * porque es el unico estable para formatos fijos segun la guia de Apple.
 */
private class RelojIos : RelojSalud {

    private val formatoFecha = NSDateFormatter().apply {
        dateFormat = "yyyy-MM-dd"
        locale = NSLocale("en_US_POSIX")
    }

    private val formatoInstante = NSDateFormatter().apply {
        dateFormat = "yyyy-MM-dd'T'HH:mm:ss'Z'"
        locale = NSLocale("en_US_POSIX")
        timeZone = NSTimeZone.timeZoneWithAbbreviation("UTC")!!
    }

    /** Hora corta en la zona horaria del dispositivo: no se fuerza `timeZone`. */
    private val formatoHoraLocal = NSDateFormatter().apply {
        dateFormat = "HH:mm"
        locale = NSLocale("en_US_POSIX")
    }

    override fun fechaHoy(): String = formatoFecha.stringFromDate(NSDate())

    override fun instanteActual(): String = formatoInstante.stringFromDate(NSDate())

    override fun horaLocal(instanteIso: String): String {
        val fecha = formatoInstante.dateFromString(instanteIso) ?: return ""
        return formatoHoraLocal.stringFromDate(fecha)
    }
}

actual fun relojDelSistema(): RelojSalud = RelojIos()
