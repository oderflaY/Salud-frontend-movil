package com.eter.salud.domain.avisos

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
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

    // Estado de Compose y no una consulta en el getter: si fuera consulta, la
    // pantalla leeria `false` al abrir y nunca se enteraria de que el paciente
    // acaba de conceder el permiso, y los recordatorios no se programarian.
    override var permitidos by mutableStateOf(gestorDeAvisos.areNotificationsEnabled())
        private set

    override var alarmasExactas by mutableStateOf(puedeProgramarExactas())
        private set

    /** Lo conecta [recordarAvisosClinicos]: lanzar el dialogo exige la composicion. */
    var pedirPermisoDeAvisos: () -> Unit = {}

    /**
     * Tras un primer rechazo, Android 13+ puede dejar de mostrar el dialogo sin
     * avisar. Desde ahi el boton lleva a Ajustes en vez de no hacer nada.
     */
    var dialogoRechazado = false

    private val preferencias = contexto.getSharedPreferences(PREFERENCIAS_AVISOS, Context.MODE_PRIVATE)

    /** Relee ambos permisos: al volver de Ajustes el sistema no avisa de nada. */
    fun refrescar() {
        permitidos = gestorDeAvisos.areNotificationsEnabled()
        alarmasExactas = puedeProgramarExactas()
    }

    override fun pedirPermisoSiHaceFalta() {
        // Antes de Android 13 los avisos vienen permitidos y no hay dialogo.
        if (permitidos || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (preferencias.getBoolean(CLAVE_PERMISO_PEDIDO, false)) return
        preferencias.edit().putBoolean(CLAVE_PERMISO_PEDIDO, true).apply()
        pedirPermisoDeAvisos()
    }

    /** Observable, como los permisos: al responder, el dialogo se cierra al instante. */
    private var alarmasYaExplicadas by mutableStateOf(preferencias.getBoolean(CLAVE_ALARMAS_EXPLICADAS, false))

    override val debeExplicarAlarmasExactas: Boolean
        get() = permitidos && !alarmasExactas && !alarmasYaExplicadas

    override fun responderAlarmasExactas(aceptar: Boolean) {
        preferencias.edit().putBoolean(CLAVE_ALARMAS_EXPLICADAS, true).apply()
        alarmasYaExplicadas = true
        if (aceptar && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            abrirAjustes(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${contexto.packageName}")),
            )
        }
    }

    override fun solicitarPermisos() {
        when {
            !permitidos && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !dialogoRechazado ->
                pedirPermisoDeAvisos()
            !permitidos -> abrirAjustes(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, contexto.packageName),
            )
            !alarmasExactas && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> abrirAjustes(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${contexto.packageName}")),
            )
        }
    }

    private fun puedeProgramarExactas(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || gestorDeAlarmas.canScheduleExactAlarms()

    private fun abrirAjustes(intencion: Intent) {
        val nueva = intencion.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            contexto.startActivity(nueva)
        } catch (sinPantalla: ActivityNotFoundException) {
            // Algunos fabricantes quitan esas pantallas de Ajustes: la ficha
            // general de la app existe siempre y lleva a ambos permisos.
            contexto.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${contexto.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

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
        // Se relee: el permiso pudo cambiar desde Ajustes con la app abierta.
        if (!gestorDeAvisos.areNotificationsEnabled()) return
        val aviso = constructor(CANAL_MENSAJES)
            .conEstiloSalud(contexto)
            .setContentTitle(textos.titulo)
            .setContentText(textos.cuerpo)
            // El texto completo al expandir: una indicacion del medico no se
            // corta en la primera linea.
            .setStyle(Notification.BigTextStyle().bigText(textos.cuerpo))
            .setCategory(Notification.CATEGORY_MESSAGE)
            // Tocarlo abre ESA conversacion, no solo la app.
            .setContentIntent(intentParaAbrir(contexto, idConversacion))
            .setGroup(GRUPO_MENSAJES)
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
            putExtra(EXTRA_ETIQUETA_POSPONER, textos.accionPosponer)
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
            canal.enableLights(true)
            canal.lightColor = COLOR_MARCA
            canal.description = if (canal.id == CANAL_MEDICACION) {
                DESCRIPCION_CANAL_MEDICACION
            } else {
                DESCRIPCION_CANAL_MENSAJES
            }
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
        if (intencion.action == ACCION_POSPONER) {
            posponer(contexto, intencion)
            return
        }
        val gestor =
            contexto.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!gestor.areNotificationsEnabled()) return

        val titulo = intencion.getStringExtra(EXTRA_TITULO).orEmpty()
        val cuerpo = intencion.getStringExtra(EXTRA_CUERPO).orEmpty()
        val clave = intencion.getStringExtra(EXTRA_CLAVE).orEmpty()
        val etiquetaPosponer = intencion.getStringExtra(EXTRA_ETIQUETA_POSPONER)

        val constructor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(contexto, CANAL_MEDICACION)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(contexto)
        }

        constructor
            .conEstiloSalud(contexto)
            .setContentTitle(titulo)
            .setContentText(cuerpo)
            .setStyle(Notification.BigTextStyle().bigText(cuerpo))
            .setCategory(Notification.CATEGORY_REMINDER)
            .setContentIntent(intentParaAbrir(contexto, idConversacion = null))
            .setAutoCancel(true)
            .setPriority(Notification.PRIORITY_MAX)
            .setDefaults(Notification.DEFAULT_ALL)

        // "Posponer 10 min" desde la propia notificacion: sin abrir la app,
        // que es lo que uno quiere cuando la alarma suena en mal momento.
        if (!etiquetaPosponer.isNullOrBlank()) {
            val posponer = Intent(contexto, ReceptorDeRecordatorios::class.java)
                .setAction(ACCION_POSPONER)
                .putExtras(intencion)
            constructor.addAction(
                Notification.Action.Builder(
                    null,
                    etiquetaPosponer,
                    PendingIntent.getBroadcast(
                        contexto,
                        claveNumerica("posponer_$clave"),
                        posponer,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                ).build(),
            )
        }

        gestor.notify(claveNumerica(clave), constructor.build())
    }

    /** Retira el aviso y lo vuelve a programar dentro de [MINUTOS_POSPONER] minutos. */
    private fun posponer(contexto: Context, intencion: Intent) {
        val clave = intencion.getStringExtra(EXTRA_CLAVE).orEmpty()
        val gestor = contexto.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        gestor.cancel(claveNumerica(clave))

        val alarmas = contexto.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val repetir = Intent(contexto, ReceptorDeRecordatorios::class.java).putExtras(intencion).setAction(null)
        val disparo = PendingIntent.getBroadcast(
            contexto,
            claveNumerica(clave),
            repetir,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val instante = System.currentTimeMillis() + MINUTOS_POSPONER * 60_000L
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmas.canScheduleExactAlarms()) {
                alarmas.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, instante, disparo)
            } else {
                alarmas.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, instante, disparo)
            }
        } catch (sinPermiso: SecurityException) {
            alarmas.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, instante, disparo)
        }
    }
}

/**
 * Lo que comparten todos los avisos de +Salud: el icono de la marca (no uno
 * generico del sistema), el color, y la hora en que llego.
 */
private fun Notification.Builder.conEstiloSalud(contexto: Context): Notification.Builder =
    setSmallIcon(iconoDeAvisos(contexto))
        .setColor(COLOR_MARCA)
        .setShowWhen(true)
        .setWhen(System.currentTimeMillis())

/**
 * El icono vive en la app (`androidApp/res/drawable/ic_stat_salud.xml`) y este
 * modulo no ve su clase `R`: se busca por nombre. Si no estuviera (una prueba,
 * otra app que use el modulo), se usa uno del sistema en vez de fallar.
 */
@android.annotation.SuppressLint("DiscouragedApi")
private fun iconoDeAvisos(contexto: Context): Int =
    contexto.resources.getIdentifier(ICONO_DE_AVISOS, "drawable", contexto.packageName)
        .takeIf { it != 0 }
        ?: android.R.drawable.ic_popup_reminder

/**
 * Abre la app y, si hay [idConversacion], esa conversacion. Con la app ya
 * abierta reutiliza la misma pantalla (`SINGLE_TOP`) en vez de apilar otra.
 */
private fun intentParaAbrir(contexto: Context, idConversacion: String?): PendingIntent {
    val intencion = (contexto.packageManager.getLaunchIntentForPackage(contexto.packageName) ?: Intent())
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        .apply { if (idConversacion != null) putExtra(EXTRA_ABRIR_CONVERSACION, idConversacion) }
    return PendingIntent.getActivity(
        contexto,
        claveNumerica("abrir_${idConversacion.orEmpty()}"),
        intencion,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
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
    val avisos = remember(contexto) { AvisosClinicosAndroid(contexto.applicationContext) }

    val lanzador = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
        if (!concedido) avisos.dialogoRechazado = true
        avisos.refrescar()
    }
    SideEffect {
        avisos.pedirPermisoDeAvisos = { lanzador.launch(Manifest.permission.POST_NOTIFICATIONS) }
    }

    // El permiso de alarmas exactas solo se concede desde Ajustes, y el de
    // avisos tambien puede activarse ahi a mano: se relee al volver a la app.
    LifecycleResumeEffect(avisos) {
        avisos.refrescar()
        onPauseOrDispose {}
    }
    return avisos
}

private const val PREFERENCIAS_AVISOS = "salud_avisos"
private const val CLAVE_PERMISO_PEDIDO = "permiso_de_avisos_pedido"
private const val CLAVE_ALARMAS_EXPLICADAS = "alarmas_exactas_explicadas"
private const val CANAL_MEDICACION = "salud_medicacion"
private const val CANAL_MENSAJES = "salud_mensajes"
private const val NOMBRE_CANAL_MEDICACION = "Recordatorios de medicacion"
private const val NOMBRE_CANAL_MENSAJES = "Mensajes de tu medico"
private const val EXTRA_TITULO = "titulo"
private const val EXTRA_CUERPO = "cuerpo"
private const val EXTRA_CLAVE = "clave"
private const val EXTRA_ETIQUETA_POSPONER = "etiqueta_posponer"
private const val ACCION_POSPONER = "com.eter.salud.POSPONER_RECORDATORIO"
private const val MINUTOS_POSPONER = 10
private const val ICONO_DE_AVISOS = "ic_stat_salud"
private const val GRUPO_MENSAJES = "salud_mensajes"
private const val COLOR_MARCA = 0xFF0A4C86.toInt()
private const val DESCRIPCION_CANAL_MEDICACION = "Suenan a la hora de cada toma, aunque la app este cerrada."
private const val DESCRIPCION_CANAL_MENSAJES = "Avisan cuando te escriben en el chat."

/** Lo lee la Activity al abrirse desde un aviso de mensaje. */
const val EXTRA_ABRIR_CONVERSACION = "com.eter.salud.ABRIR_CONVERSACION"
