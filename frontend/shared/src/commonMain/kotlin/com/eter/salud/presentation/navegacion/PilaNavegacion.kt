package com.eter.salud.presentation.navegacion

import androidx.compose.runtime.Immutable

/**
 * Pila de retorno de la app. Estructura inmutable y pura: cada operacion
 * devuelve una pila nueva, nunca modifica la actual.
 *
 * Es lo que la version anterior no tenia. Con un solo `enum` en una variable, la
 * app no recordaba de donde venias: cada pantalla tenia que codificar a mano a
 * donde "volver", y el boton Atras del sistema cerraba la aplicacion desde
 * cualquier sitio. Aqui volver es una operacion del sistema, no una decision que
 * cada pantalla repite.
 *
 * Al ser pura se puede probar entera sin levantar Compose, que es justo lo que
 * hace falta en la pieza de la que cuelga toda la navegacion.
 */
@Immutable
data class PilaNavegacion(
    val entradas: List<Destino>,
    /**
     * Si el ultimo movimiento fue hacia adelante. Lo consume la animacion para
     * decidir el sentido del deslizamiento: sin esto, volver se veria igual que
     * avanzar y el gesto perderia su significado espacial.
     */
    val avanzando: Boolean = true,
) {

    init {
        require(entradas.isNotEmpty()) { "La pila de navegacion nunca puede quedar vacia" }
    }

    val actual: Destino get() = entradas.last()

    /** Cierto si hay a donde volver. Gobierna la flecha de la cabecera. */
    val puedeVolver: Boolean get() = entradas.size > 1

    /** La seccion cuya pestana debe verse encendida, o nulo fuera de las pestanas. */
    val seccionActiva: Destino.Seccion?
        get() = entradas.lastOrNull { it is Destino.Seccion } as? Destino.Seccion

    /**
     * Apila un destino encima del actual.
     *
     * Repetir el destino que ya esta arriba NO apila nada: es lo que evita que un
     * doble toque nervioso -- muy comun en una lista de pacientes -- deje dos
     * copias de la misma pantalla y obligue a pulsar Atras dos veces.
     */
    fun apilar(destino: Destino): PilaNavegacion =
        if (destino == actual) copy(avanzando = true)
        else PilaNavegacion(entradas + destino, avanzando = true)

    /**
     * Retrocede una pantalla. Desde la raiz no hace nada: quien decide si eso
     * cierra la app es la plataforma, no esta estructura, y vaciar la pila
     * dejaria a la interfaz sin nada que dibujar.
     */
    fun volver(): PilaNavegacion =
        if (puedeVolver) PilaNavegacion(entradas.dropLast(1), avanzando = false) else this

    /**
     * Cambia de pestana.
     *
     * La pila se reconstruye en vez de crecer: sin esto, saltar entre pestanas
     * diez veces dejaria diez entradas y volver seria recorrer el historial de
     * toques del usuario, que no es lo que nadie espera de una barra inferior.
     *
     * Toda seccion que no sea [inicio] se apila SOBRE ella, de modo que Atras
     * desde cualquier pestana lleva al panel principal y solo desde ahi sale de
     * la app. Es el comportamiento que Android da por sentado.
     */
    fun irASeccion(seccion: Destino.Seccion, inicio: Destino.Seccion): PilaNavegacion {
        val nuevas = if (seccion == inicio) listOf(seccion) else listOf(inicio, seccion)
        return PilaNavegacion(nuevas, avanzando = seccion != seccionActiva)
    }

    /**
     * Reinicia la navegacion entera en [destino].
     *
     * Es el equivalente al `popUpTo(0) { inclusive = true }` de las librerias de
     * navegacion, y existe para el cierre de sesion: tras salir, el boton Atras
     * NO puede devolver a una pantalla con datos clinicos de la sesion anterior.
     */
    fun reiniciarEn(destino: Destino): PilaNavegacion =
        PilaNavegacion(listOf(destino), avanzando = false)

    /**
     * Vuelve atras hasta [destino], dejandolo arriba.
     *
     * Si el destino no esta en la pila no cambia nada, en vez de vaciarla: un
     * objetivo escrito mal no debe poder dejar al usuario en ningun sitio.
     */
    fun volverHasta(destino: Destino): PilaNavegacion {
        val indice = entradas.indexOf(destino)
        if (indice < 0) return this
        return PilaNavegacion(entradas.take(indice + 1), avanzando = false)
    }

    companion object {
        fun raiz(destino: Destino) = PilaNavegacion(listOf(destino))
    }
}
