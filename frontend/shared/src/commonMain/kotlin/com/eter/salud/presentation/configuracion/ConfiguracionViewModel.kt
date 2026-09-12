package com.eter.salud.presentation.configuracion

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Preferencias del paciente en este dispositivo.
 *
 * Son ajustes LOCALES, no del expediente: viven en el telefono y no viajan al
 * backend. Un recordatorio silenciado en la tableta de casa no debe callar el
 * del movil que el paciente lleva encima.
 */
data class ConfiguracionUiState(
    val recordatoriosDeMedicacion: Boolean = true,
    val avisosDeMensajes: Boolean = true,
    /** Seguir la preferencia del sistema, en vez de fijar el modo a mano. */
    val seguirModoDelSistema: Boolean = true,
    val forzarModoOscuro: Boolean = false,
    val biometriaAlAbrir: Boolean = false,
    /** El sistema tiene bloqueadas las notificaciones de la app. */
    val avisosBloqueadosPorElSistema: Boolean = false,
    /** Este aparato no tiene huella ni rostro configurados. */
    val biometriaDisponible: Boolean = true,
) {
    /**
     * El modo que la app tiene que pintar, dada la preferencia del sistema.
     *
     * Vive aqui y no en el tema para que la regla -- el ajuste manual solo
     * manda cuando el paciente dejo de seguir al sistema -- se pruebe sin UI.
     */
    fun modoOscuroEfectivo(sistemaEnOscuro: Boolean): Boolean =
        if (seguirModoDelSistema) sistemaEnOscuro else forzarModoOscuro
}

/**
 * ViewModel de la pantalla de configuracion.
 *
 * Guarda las preferencias en memoria. Es deliberado en esta fase: el
 * almacenamiento persistente entra con la base de datos local, y hacerlo ahora
 * significaria escribir dos veces la misma capa. Lo que si esta fijado ya es el
 * CONTRATO -- que se puede ajustar y con que reglas -- de modo que persistirlo
 * despues no toque ni la Vista ni estas reglas.
 */
class ConfiguracionViewModel(
    avisosPermitidosPorElSistema: Boolean = true,
    biometriaDisponible: Boolean = true,
) : ViewModel() {

    private val _estado = MutableStateFlow(
        ConfiguracionUiState(
            avisosBloqueadosPorElSistema = !avisosPermitidosPorElSistema,
            biometriaDisponible = biometriaDisponible,
        ),
    )
    val estado: StateFlow<ConfiguracionUiState> = _estado.asStateFlow()

    fun cambiarRecordatorios(activo: Boolean) {
        _estado.update { it.copy(recordatoriosDeMedicacion = activo) }
    }

    fun cambiarAvisosDeMensajes(activo: Boolean) {
        _estado.update { it.copy(avisosDeMensajes = activo) }
    }

    /**
     * Al volver a seguir al sistema se descarta la eleccion manual: dejarla
     * guardada haria que reactivar el interruptor mas tarde saltara a un modo
     * que el paciente ya no recuerda haber elegido.
     */
    fun cambiarSeguirAlSistema(activo: Boolean) {
        _estado.update {
            if (activo) it.copy(seguirModoDelSistema = true, forzarModoOscuro = false)
            else it.copy(seguirModoDelSistema = false)
        }
    }

    fun cambiarModoOscuro(activo: Boolean) {
        // Fijar el modo a mano implica dejar de seguir al sistema: mantener las
        // dos cosas activas dejaria un interruptor que no hace nada visible.
        _estado.update { it.copy(forzarModoOscuro = activo, seguirModoDelSistema = false) }
    }

    fun cambiarBiometria(activo: Boolean) {
        if (!_estado.value.biometriaDisponible) return
        _estado.update { it.copy(biometriaAlAbrir = activo) }
    }
}
