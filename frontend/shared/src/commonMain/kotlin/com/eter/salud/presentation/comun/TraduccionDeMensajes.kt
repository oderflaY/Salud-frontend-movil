package com.eter.salud.presentation.comun

import com.eter.salud.domain.repository.TraduccionRepositorio

/**
 * Traduccion de UN mensaje del chat, tal como la ve la pantalla.
 *
 * No existe un estado "sin traducir": eso es la ausencia de entrada en el mapa.
 * Lo que si necesita nombre propio es la espera (diez segundos son visibles) y
 * el fallo, porque el mensaje se sigue leyendo en su idioma original y la
 * pantalla debe poder ofrecer reintentar.
 */
sealed interface EstadoTraduccion {

    /** Pedida al backend, todavia sin respuesta. */
    data object Cargando : EstadoTraduccion

    /**
     * Traduccion lista. [mostrandoOriginal] es lo que se esta viendo ahora
     * mismo: el original siempre queda a un toque, porque una traduccion
     * automatica de un sintoma puede cambiarle el matiz.
     */
    data class Lista(val texto: String, val mostrandoOriginal: Boolean = false) : EstadoTraduccion

    /** No se pudo traducir (sin conexion, modelo caido). Se puede reintentar. */
    data object Fallida : EstadoTraduccion

    /**
     * El mensaje ya estaba en el idioma de quien lee. No es un fallo ni algo
     * que reintentar: se dice y se deja el mensaje como esta.
     */
    data object SinCambios : EstadoTraduccion
}

/** Pide la traduccion y la convierte en el estado que pinta la Vista. */
internal suspend fun traducirParaLaVista(
    repositorio: TraduccionRepositorio,
    texto: String,
    idiomaDestino: String,
): EstadoTraduccion = ejecutarSeguro { repositorio.traducir(texto, idiomaDestino) }.fold(
    onSuccess = { traduccion ->
        // Un modelo puede devolver el mismo texto si ya estaba en ese idioma:
        // mostrarlo como "traducido" seria mentir sobre lo que hizo.
        if (traduccion.trim() == texto.trim()) EstadoTraduccion.SinCambios else EstadoTraduccion.Lista(traduccion)
    },
    onFailure = { EstadoTraduccion.Fallida },
)

/**
 * Deja ver todas las traducciones ya hechas, o todos los originales. Es lo que
 * pasa al encender o apagar la traduccion automatica: lo ya traducido no se
 * vuelve a pedir, solo cambia que version se muestra.
 */
internal fun Map<String, EstadoTraduccion>.mostrandoOriginales(
    original: Boolean,
): Map<String, EstadoTraduccion> = mapValues { (_, estado) ->
    if (estado is EstadoTraduccion.Lista) estado.copy(mostrandoOriginal = original) else estado
}

/** Cruza entre la traduccion y el original; no hace nada si aun no esta lista. */
internal fun Map<String, EstadoTraduccion>.alternando(idMensaje: String): Map<String, EstadoTraduccion> {
    val actual = this[idMensaje] as? EstadoTraduccion.Lista ?: return this
    return this + (idMensaje to actual.copy(mostrandoOriginal = !actual.mostrandoOriginal))
}
