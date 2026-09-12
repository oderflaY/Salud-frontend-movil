package com.eter.salud.domain.avisos

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.eter.salud.domain.model.RecordatorioMedicacion
import com.eter.salud.domain.time.CalendarioSalud
import platform.Foundation.NSDateComponents
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationInterruptionLevel
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter

/**
 * Avisos clinicos sobre UserNotifications de iOS.
 *
 * Equivale al `AlarmManager` de Android: [UNCalendarNotificationTrigger]
 * programa el aviso en el propio dispositivo, de modo que suena sin red y con la
 * app cerrada, que es la razon de ser del modulo.
 *
 * ## Nivel de interrupcion
 *
 * El recordatorio se marca como `timeSensitive`. Es la unica categoria que iOS
 * deja atravesar el modo Concentracion sin ser una alerta critica del sistema, y
 * describe exactamente lo que es una toma de medicacion: algo que importa AHORA
 * y pierde su valor una hora despues. Marcarlo como aviso normal haria que el
 * paciente con Concentracion activada -- durmiendo, trabajando -- no se enterara.
 */
private class AvisosClinicosIos : AvisosClinicos {

    private val centro = UNUserNotificationCenter.currentNotificationCenter()

    /**
     * Se cachea la respuesta del permiso porque en iOS consultarlo es asincrono
     * y este contrato es sincrono. Empieza en `false` y solo pasa a `true`
     * cuando el usuario acepta: dar por supuesto el permiso haria creer a la
     * pantalla que el paciente esta avisado cuando no lo esta.
     */
    private var autorizado: Boolean = false

    init {
        centro.requestAuthorizationWithOptions(
            options = UNAuthorizationOptionAlert or
                UNAuthorizationOptionSound or
                UNAuthorizationOptionBadge,
        ) { concedido, _ ->
            autorizado = concedido
        }
    }

    override val permitidos: Boolean get() = autorizado

    override fun programarRecordatorio(
        recordatorio: RecordatorioMedicacion,
        textos: TextosAviso,
    ) {
        val partes = CalendarioSalud.descomponer(recordatorio.fecha) ?: return
        val horaMinuto = recordatorio.horaProgramada.split(":")
        if (horaMinuto.size != 2) return
        val hora = horaMinuto[0].toIntOrNull() ?: return
        val minuto = horaMinuto[1].toIntOrNull() ?: return

        val componentes = NSDateComponents().apply {
            setYear(partes.anio.toLong())
            setMonth(partes.mes.toLong())
            setDay(partes.dia.toLong())
            setHour(hora.toLong())
            setMinute(minuto.toLong())
        }

        val contenido = UNMutableNotificationContent().apply {
            setTitle(textos.titulo)
            setBody(textos.cuerpo)
            setSound(UNNotificationSound.defaultSound)
            setInterruptionLevel(UNNotificationInterruptionLevel.UNNotificationInterruptionLevelTimeSensitive)
        }

        // El identificador es la clave estable de la toma: volver a programarla
        // SUSTITUYE el aviso anterior en vez de anadir un segundo, igual que
        // hace `FLAG_UPDATE_CURRENT` en Android.
        val peticion = UNNotificationRequest.requestWithIdentifier(
            identifier = recordatorio.claveSistema,
            content = contenido,
            trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(
                dateComponents = componentes,
                repeats = false,
            ),
        )
        centro.addNotificationRequest(peticion, null)
    }

    override fun cancelarRecordatorio(idToma: String) {
        val clave = listOf("toma_$idToma")
        centro.removePendingNotificationRequestsWithIdentifiers(clave)
        centro.removeDeliveredNotificationsWithIdentifiers(clave)
    }

    override fun avisarMensajeDelMedico(idConversacion: String, textos: TextosAviso) {
        val contenido = UNMutableNotificationContent().apply {
            setTitle(textos.titulo)
            setBody(textos.cuerpo)
            setSound(UNNotificationSound.defaultSound)
        }
        // Disparo inmediato: iOS no admite intervalo cero, y un segundo es lo
        // minimo que acepta sin rechazar la peticion.
        val peticion = UNNotificationRequest.requestWithIdentifier(
            identifier = idConversacion,
            content = contenido,
            trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(
                timeInterval = RETARDO_MINIMO_SEGUNDOS,
                repeats = false,
            ),
        )
        centro.addNotificationRequest(peticion, null)
    }
}

@Composable
actual fun recordarAvisosClinicos(): AvisosClinicos = remember { AvisosClinicosIos() }

private const val RETARDO_MINIMO_SEGUNDOS = 1.0
