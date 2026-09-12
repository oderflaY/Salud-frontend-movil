package com.eter.salud.presentation.navegacion

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Guarda la pila de navegacion viva.
 *
 * Es un `ViewModel` y no un `remember` de Compose para que la pila sobreviva a
 * lo que un `remember` no sobrevive: girar el telefono, plegarlo, o que el
 * sistema recree la actividad al volver de una llamada. Antes, cualquiera de las
 * tres cosas devolvia al usuario a la pantalla de acceso; en mitad de un
 * cuestionario clinico eso significa volver a capturarlo todo.
 *
 * No conoce Compose ni recursos: solo publica [PilaNavegacion] y recibe ordenes.
 */
class NavegacionViewModel(destinoInicial: Destino = Destino.Acceso) : ViewModel() {

    private val _pila = MutableStateFlow(PilaNavegacion.raiz(destinoInicial))
    val pila: StateFlow<PilaNavegacion> = _pila.asStateFlow()

    fun ir(destino: Destino) = _pila.update { it.apilar(destino) }

    fun volver() = _pila.update { it.volver() }

    fun irASeccion(seccion: Destino.Seccion, inicio: Destino.Seccion) =
        _pila.update { it.irASeccion(seccion, inicio) }

    fun volverHasta(destino: Destino) = _pila.update { it.volverHasta(destino) }

    /** Cambio de portal o cierre de sesion: la pila anterior deja de existir. */
    fun reiniciarEn(destino: Destino) = _pila.update { it.reiniciarEn(destino) }
}
