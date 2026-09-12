package com.eter.salud.domain.avisos

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.eter.salud.domain.model.RecordatorioMedicacion
import com.eter.salud.domain.time.CalendarioSalud
import java.util.Calendar

/**
 * Avisos clinicos sobre las APIs del sistema de Android.
 *
 * ## Por que AlarmManager y no WorkManager
 *
 * `WorkManager` agrupa trabajos y puede retrasarlos varios minutos para ahorrar
 * bateria. Para sincronizar datos es lo correcto; para una pastilla de las 08:00
 * no lo es. `setExactAndAllowWhileIdle` es la unica via que dispara a la hora
 * exacta incluso con el telefono en reposo profundo (Doze), que es precisamente
 * el estado en que esta un movil a las ocho de la manana en la mesilla.
 *
 * ## Degradacion, nunca caida
 *
 * Desde Android 12 el sistema puede negar las alarmas exactas, y desde Android
 * 13 puede negar las notificaciones. Ninguna de las dos cosas lanza aqui: se
 * cae a una alarma inexacta, o el aviso simplemente no se programa. Una app de
 * salud que se cierra al no poder avisar es peor que una que avisa tarde.
 */
private class AvisosClinicosAndroid(private val contexto: Context) : AvisosClinicos {

    private val gestorDeAvisos =
        contexto.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val gestorDeAlarmas =
        contexto.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    init {
        crearCanales()
    }

    override val permitidos: Boolean
        get() = gestorDeAvisos.areNotificationsEnabled()

    override fun programarRecordatorio(
        recordatorio: RecordatorioMedicacion,
        textos: TextosAviso,
    ) {
        val instante = instanteDe(recordatorio.fecha, recordatorio.horaProgramada) ?: return
        // Una hora ya pasada no se programa: el sistema la disparara de
        // inmediato y el paciente recibiria el aviso de una toma vencida.
        if (instante <= System.currentTimeMillis()) return

        val disparo = intentPendienteDe(recordatorio, textos)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !gestorDeAlarmas.canScheduleExactAlarms()) {
                // Sin permiso de alarma exacta se avisa igual, con holgura. Un
                // recordatorio con diez minutos de retraso sigue sirviendo;
                // ninguno, no.
                gestorDeAlarmas.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, instante, disparo)
            } else {
                gestorDeAlarmas.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, instante, disparo)
            }
        } catch (sinPermiso: SecurityException) {
            // El sistema puede revocar el permiso entre la comprobacion y la
            // llamada. Se degrada en vez de tumbar la app.
            gestorDeAlarmas.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, instante, disparo)
        }
    }

    override fun cancelarRecordatorio(idToma: String) {
        val intencion = Intent(contexto, ReceptorDeRecordatorios::class.java)
        val disparo = PendingIntent.getBroadcast(
            contexto,
            claveNumerica("toma_$idToma"),
            intencion,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        if (disparo != null) {
            gestorDeAlarmas.cancel(disparo)
            disparo.cancel()
        }
        gestorDeAvisos.cancel(claveNumerica("toma_$idToma"))
    }

    override fun avisarMensajeDelMedico(idConversacion: String, textos: TextosAviso) {
        if (!permitidos) return
        val aviso = constructor(CANAL_MENSAJES)
            .setContentTitle(textos.titulo)
            .setContentText(textos.cuerpo)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setAutoCancel(true)
            // `MAX` es lo que hace que baje sobre la pantalla en vez de quedarse
            // en la bandeja. En API 26+ manda la importancia del canal, pero
            // esta linea sigue siendo la que gobierna en 24 y 25.
            .setPriority(Notification.PRIORITY_MAX)
            .setDefaults(Notification.DEFAULT_ALL)
            .build()
        gestorDeAvisos.notify(claveNumerica(idConversacion), aviso)
    }

    private fun intentPendienteDe(
        recordatorio: RecordatorioMedicacion,
        textos: TextosAviso,
    ): PendingIntent {
        val intencion = Intent(contexto, ReceptorDeRecordatorios::class.java).apply {
            // Los textos viajan DENTRO de la alarma. Cuando suene, la app puede
            // llevar dias cerrada y no habra recursos de Compose que consultar.
            putExtra(EXTRA_TITULO, textos.titulo)
            putExtra(EXTRA_CUERPO, textos.cuerpo)
            putExtra(EXTRA_CLAVE, recordatorio.claveSistema)
        }
        return PendingIntent.getBroadcast(
            contexto,
            claveNumerica(recordatorio.claveSistema),
            intencion,
            // `UPDATE_CURRENT` es lo que hace que reprogramar REEMPLACE la alarma
            // en vez de anadir una segunda del mismo medicamento.
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun crearCanales() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        // Dos canales y no uno: el paciente tiene que poder silenciar los
        // mensajes del chat sin silenciar sus recordatorios de medicacion.
        listOf(
            NotificationChannel(
                CANAL_MEDICACION,
                NOMBRE_CANAL_MEDICACION,
                NotificationManager.IMPORTANCE_HIGH,
            ),
            NotificationChannel(
                CANAL_MENSAJES,
                NOMBRE_CANAL_MENSAJES,
                NotificationManager.IMPORTANCE_HIGH,
            ),
        ).forEach { canal ->
            canal.enableVibration(true)
            gestorDeAvisos.createNotificationChannel(canal)
        }
    }

    private fun constructor(canal: String): Notification.Builder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(contexto, canal)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(contexto)
        }
}

/**
 * Recibe la alarma y publica el aviso.
 *
 * Es un `BroadcastReceiver` declarado en el manifiesto, no una clase interna: el
 * sistema tiene que poder instanciarlo con la app cerrada.
 */
class ReceptorDeRecordatorios : BroadcastReceiver() {

    override fun onReceive(contexto: Context, intencion: Intent) {
        val gestor =
            contexto.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!gestor.areNotificationsEnabled()) return

        val titulo = intencion.getStringExtra(EXTRA_TITULO).orEmpty()
        val cuerpo = intencion.getStringExtra(EXTRA_CUERPO).orEmpty()
        val clave = intencion.getStringExtra(EXTRA_CLAVE).orEmpty()

        val constructor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(contexto, CANAL_MEDICACION)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(contexto)
        }

        val aviso = constructor
            .setContentTitle(titulo)
            .setContentText(cuerpo)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setAutoCancel(true)
            .setPriority(Notification.PRIORITY_MAX)
            .setDefaults(Notification.DEFAULT_ALL)
            .build()

        gestor.notify(claveNumerica(clave), aviso)
    }
}

/**
 * Convierte la clave de texto del recordatorio en el entero que exige el
 * sistema. Se usa `hashCode` estable de la cadena para que la MISMA toma
 * produzca siempre el mismo identificador y su alarma se pueda reemplazar y
 * cancelar mas tarde.
 */
private fun claveNumerica(clave: String): Int = clave.hashCode()

/** Convierte `YYYY-MM-DD` + `HH:MM` locales al instante absoluto del sistema. */
private fun instanteDe(fecha: String, hora: String): Long? {
    val partes = CalendarioSalud.descomponer(fecha) ?: return null
    val trozosHora = hora.split(":")
    if (trozosHora.size != 2) return null
    val h = trozosHora[0].toIntOrNull() ?: return null
    val m = trozosHora[1].toIntOrNull() ?: return null

    return Calendar.getInstance().apply {
        set(Calendar.YEAR, partes.anio.toInt())
        set(Calendar.MONTH, partes.mes.toInt() - 1)
        set(Calendar.DAY_OF_MONTH, partes.dia.toInt())
        set(Calendar.HOUR_OF_DAY, h)
        set(Calendar.MINUTE, m)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

@Composable
actual fun recordarAvisosClinicos(): AvisosClinicos {
    val contexto = LocalContext.current
    return remember(contexto) { AvisosClinicosAndroid(contexto.applicationContext) }
}

private const val CANAL_MEDICACION = "salud_medicacion"
private const val CANAL_MENSAJES = "salud_mensajes"
private const val NOMBRE_CANAL_MEDICACION = "Recordatorios de medicacion"
private const val NOMBRE_CANAL_MENSAJES = "Mensajes de tu medico"
private const val EXTRA_TITULO = "titulo"
private const val EXTRA_CUERPO = "cuerpo"
private const val EXTRA_CLAVE = "clave"
