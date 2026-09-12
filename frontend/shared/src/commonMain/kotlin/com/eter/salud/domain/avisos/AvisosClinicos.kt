package com.eter.salud.domain.avisos

import androidx.compose.runtime.Composable
import com.eter.salud.domain.model.RecordatorioMedicacion

/**
 * Textos de un aviso, ya resueltos y traducidos.
 *
 * Viajan compuestos desde la Vista en vez de dejar que el codigo de plataforma
 * los arme. La razon es que una alarma de medicacion se dispara con la app
 * CERRADA: en ese momento no hay tema, ni recursos de Compose cargados, ni
 * ViewModel al que preguntar. Si el texto se resolviera alli, habria que
 * duplicar todas las cadenas en los recursos nativos de Android y de iOS, y el
 * dia que alguien tradujera la app se olvidaria de uno de los dos.
 */
data class TextosAviso(
    val titulo: String,
    val cuerpo: String,
    /** Etiqueta del boton que registra la toma sin abrir la app. */
    val accionRegistrar: String? = null,
    /** Etiqueta del boton que reprograma el aviso unos minutos mas tarde. */
    val accionPosponer: String? = null,
)

/**
 * Avisos del sistema operativo: recordatorios de medicacion y mensajes del
 * medico.
 *
 * Es la unica frontera de la app hacia las notificaciones. La Vista y los
 * ViewModel hablan con este contrato y nunca con `NotificationManager` ni con
 * `UNUserNotificationCenter`, de modo que la logica de cuando avisar es comun y
 * lo unico que cambia por plataforma es como se pinta el aviso.
 *
 * ## Por que los recordatorios son locales
 *
 * Un recordatorio de medicacion NO puede depender de una notificacion enviada
 * desde el servidor. El paciente que mas lo necesita es justo el que puede estar
 * sin cobertura o sin datos, y una pastilla que se olvida por falta de red es un
 * fallo del producto. Por eso la alarma se programa en el propio telefono y
 * suena aunque la app lleve una semana cerrada y el aparato en modo avion.
 */
interface AvisosClinicos {

    /** Si el sistema tiene permitido mostrar avisos de esta app. */
    val permitidos: Boolean

    /**
     * Programa el recordatorio de una toma, o REEMPLAZA el que ya existiera para
     * la misma toma. Reprogramar no debe poder duplicar el aviso de una pastilla
     * (ver [RecordatorioMedicacion.claveSistema]).
     */
    fun programarRecordatorio(recordatorio: RecordatorioMedicacion, textos: TextosAviso)

    /** Retira el recordatorio de una toma ya registrada u omitida. */
    fun cancelarRecordatorio(idToma: String)

    /**
     * Aviso inmediato de un mensaje del medico, con prioridad alta para que
     * aparezca sobre la pantalla en vez de quedarse en la bandeja.
     *
     * [textoMensaje] llega ya recortado por quien llama: el aviso es una
     * invitacion a abrir la conversacion, no el lugar donde leer una indicacion
     * clinica completa.
     */
    fun avisarMensajeDelMedico(idConversacion: String, textos: TextosAviso)
}

/**
 * Implementacion de la plataforma, atada al ciclo de vida de la composicion.
 *
 * Se obtiene con un `remember` y no con un objeto global por el mismo motivo que
 * el lector NFC: en Android necesita el `Context`, y guardarlo en una variable
 * estatica es la via directa a una fuga de la Activity.
 */
@Composable
expect fun recordarAvisosClinicos(): AvisosClinicos
