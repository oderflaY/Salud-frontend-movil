package com.eter.salud.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Tokens de color SEMANTICOS: la UI nunca nombra un color, nombra su rol.
 * Cambiar la paleta o el modo oscuro se hace aqui y en ningun otro archivo
 * (DM_Arquitectura_App.md, seccion 3: Modo Oscuro Obligatorio).
 */
@Immutable
data class ColoresSalud(
    /** Texto de apoyo, descripciones y ayudas. */
    val textoSecundario: Color,
    /** Lineas divisorias de baja jerarquia. */
    val separador: Color,
    /** Acento de un dato critico de supervivencia. */
    val acentoCritico: Color,
    /** Fondo de los avisos de dato critico. */
    val fondoCritico: Color,
    /** Confirmacion de una accion completada. */
    val exito: Color,
    /** Relleno de la accion principal de una pantalla (Turquesa de Salud). */
    val acentoAccion: Color,
    /** Texto sobre [acentoAccion]. */
    val sobreAcentoAccion: Color,
    /** Fondo de los campos de captura: contenedor suave, sin borde agresivo. */
    val fondoCampo: Color,
    /** Fondo de las tarjetas y bloques agrupados del panel principal. */
    val fondoTarjeta: Color,
    /** Fondo de pantalla del Directorio Medico y del chat: un blanco levemente gris, distinto del fondo general. */
    val fondoConversacion: Color,
    /** Relleno de la burbuja de mensajes del medico (la del paciente usa `MaterialTheme.colorScheme.primary`). */
    val fondoBurbujaMedico: Color,
    /** Texto sobre [fondoBurbujaMedico]. */
    val sobreBurbujaMedico: Color,
    /** Fondo del banner de advertencia clinica ("esto es orientacion, no un diagnostico"). */
    val fondoAdvertencia: Color,
    /** Texto sobre [fondoAdvertencia]. */
    val textoAdvertencia: Color,

    // --------------------------------------------------- Estados de la agenda
    //
    // Un par (texto, fondo) por estado de cita. El color NUNCA viaja solo en la
    // pantalla: cada tarjeta lleva ademas el nombre del estado escrito, porque
    // un calendario que solo distingue por tono es ilegible para quien no
    // percibe el rojo y el verde, y es exactamente ahi donde se juega si el
    // medico ve que un paciente no asistio.
    //
    // "Cancelada" y "No asistio" comparten par a proposito: son dos hechos
    // distintos con la misma consecuencia visual ("esta cita no va a ocurrir"),
    // y su diferencia la dice el texto de la tarjeta.

    /** Cita pendiente de que el consultorio la confirme. */
    val citaPendiente: Color,
    val fondoCitaPendiente: Color,
    /** Cita ya confirmada por el consultorio. */
    val citaConfirmada: Color,
    val fondoCitaConfirmada: Color,
    /** Consulta en curso o paciente ya en sala. */
    val citaEnCurso: Color,
    val fondoCitaEnCurso: Color,
    /** Cita cancelada o inasistencia. */
    val citaCancelada: Color,
    val fondoCitaCancelada: Color,
    /** Horario que el medico reservo para si (comida, cirugia, vacaciones). */
    val citaBloqueada: Color,
    val fondoCitaBloqueada: Color,
)

/**
 * Escala de espaciado. El minimalismo del DM exige aire: los pasos usan
 * [PasoOnboardingEspaciado] como separacion base entre bloques.
 */
@Immutable
data class EspaciadoSalud(
    val minimo: Dp = 4.dp,
    val compacto: Dp = 8.dp,
    val medio: Dp = 16.dp,
    val amplio: Dp = 24.dp,
    val generoso: Dp = 32.dp,
    val respiro: Dp = 48.dp,
)

/** Separacion vertical por defecto entre bloques de una pregunta. */
val PasoOnboardingEspaciado: Dp = 24.dp

/** Area tactil minima accesible recomendada por las guias de iOS y Android. */
val AreaTactilMinima: Dp = 48.dp

/**
 * Paleta clinica en Modo Claro. Azul institucional sobre blanco clinico, con el
 * gris contenedor reservado a los campos de captura.
 */
private val ColoresClaros = lightColorScheme(
    primary = Color(0xFF0055FF),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE3ECFF),
    onPrimaryContainer = Color(0xFF001A4D),
    secondary = Color(0xFF4B5563),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF111827),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFF4F5F7),
    onSurfaceVariant = Color(0xFF4B5563),
    outline = Color(0xFFC7CBD1),
    error = Color(0xFFE63946),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFDE7E9),
    onErrorContainer = Color(0xFF4A0A10),
)

/**
 * Paleta clinica en Modo Oscuro sobre el negro pizarra `#0F1115`.
 *
 * El azul institucional `#0055FF` se aclara a `#7FA6FF` SOLO aqui: sobre fondo
 * oscuro el original queda en 2.5:1 de contraste y el DM exige no perder
 * legibilidad (seccion 3). El resto de la paleta conserva los tonos exactos.
 */
private val ColoresOscuros = darkColorScheme(
    primary = Color(0xFF7FA6FF),
    onPrimary = Color(0xFF001A4D),
    primaryContainer = Color(0xFF003AAD),
    onPrimaryContainer = Color(0xFFE3ECFF),
    secondary = Color(0xFFA8B0BC),
    onSecondary = Color(0xFF1B1F27),
    background = Color(0xFF0F1115),
    onBackground = Color(0xFFE7E9EE),
    surface = Color(0xFF14171D),
    onSurface = Color(0xFFE7E9EE),
    surfaceVariant = Color(0xFF1B1F27),
    onSurfaceVariant = Color(0xFFA8B0BC),
    outline = Color(0xFF6B7280),
    error = Color(0xFFFF8A8F),
    onError = Color(0xFF4A0A10),
    errorContainer = Color(0xFF7A1C24),
    onErrorContainer = Color(0xFFFFE1E3),
)

/**
 * El Turquesa de Salud `#00A896` se conserva exacto como relleno de la accion
 * principal, pero su texto va en negro pizarra: blanco sobre turquesa da 3:1 y
 * no alcanza el minimo legible; con `#0F1115` sube a 7:1.
 */
private val ColoresSaludClaros = ColoresSalud(
    textoSecundario = Color(0xFF4B5563),
    separador = Color(0xFFE5E7EB),
    acentoCritico = Color(0xFFE63946),
    fondoCritico = Color(0xFFFDE7E9),
    exito = Color(0xFF00806F),
    acentoAccion = Color(0xFF00A896),
    sobreAcentoAccion = Color(0xFF0F1115),
    fondoCampo = Color(0xFFF4F5F7),
    fondoTarjeta = Color(0xFFF4F5F7),
    fondoConversacion = Color(0xFFF8F9FA),
    fondoBurbujaMedico = Color(0xFFE5E7EB),
    sobreBurbujaMedico = Color(0xFF111827),
    fondoAdvertencia = Color(0xFFFFF3CD),
    textoAdvertencia = Color(0xFF664D03),
    // El ambar se oscurece hasta `#B45309` para el texto: el amarillo puro del
    // codigo de colores del requerimiento sobre su propio fondo claro no llega
    // al minimo legible, y una etiqueta de estado que no se lee no es un estado.
    citaPendiente = Color(0xFFB45309),
    fondoCitaPendiente = Color(0xFFFEF3C7),
    citaConfirmada = Color(0xFF00806F),
    fondoCitaConfirmada = Color(0xFFD1FAE5),
    citaEnCurso = Color(0xFF0055FF),
    fondoCitaEnCurso = Color(0xFFE3ECFF),
    citaCancelada = Color(0xFFE63946),
    fondoCitaCancelada = Color(0xFFFDE7E9),
    citaBloqueada = Color(0xFF4B5563),
    fondoCitaBloqueada = Color(0xFFF4F5F7),
)

/**
 * El banner de advertencia SI cambia de valores entre modos, a diferencia del
 * resto de la paleta: el amarillo pastel `#FFF3CD` que pide el diseno pierde
 * todo su proposito (una nota de aviso, no una luz de neon) sobre el negro
 * pizarra del Modo Oscuro. Se sustituye por un ambar oscuro de baja saturacion
 * con texto ambar claro, que conserva el mismo significado -- "esto es una
 * nota, no un error" -- con el mismo criterio que ya se aplico al turquesa y
 * al azul institucional en este archivo.
 */
private val ColoresSaludOscuros = ColoresSalud(
    textoSecundario = Color(0xFFA8B0BC),
    separador = Color(0xFF262A33),
    acentoCritico = Color(0xFFFF8A8F),
    fondoCritico = Color(0xFF3A1216),
    exito = Color(0xFF3FD5C2),
    acentoAccion = Color(0xFF00A896),
    sobreAcentoAccion = Color(0xFF0F1115),
    fondoCampo = Color(0xFF1B1F27),
    fondoTarjeta = Color(0xFF1B1F27),
    fondoConversacion = Color(0xFF121212),
    fondoBurbujaMedico = Color(0xFF2A2D35),
    sobreBurbujaMedico = Color(0xFFE7E9EE),
    fondoAdvertencia = Color(0xFF3A2F12),
    textoAdvertencia = Color(0xFFFFD98A),
    // Mismo criterio que el resto del Modo Oscuro: se conserva el SIGNIFICADO
    // del codigo de colores (ambar, verde, azul, rojo, gris) invirtiendo la
    // pareja, con el tono saturado en el texto y un fondo apagado detras. Los
    // rellenos claros del Modo Claro sobre el negro pizarra serian faros.
    citaPendiente = Color(0xFFFFD98A),
    fondoCitaPendiente = Color(0xFF3A2F12),
    citaConfirmada = Color(0xFF3FD5C2),
    fondoCitaConfirmada = Color(0xFF0F2E2A),
    citaEnCurso = Color(0xFF7FA6FF),
    fondoCitaEnCurso = Color(0xFF17203A),
    citaCancelada = Color(0xFFFF8A8F),
    fondoCitaCancelada = Color(0xFF3A1216),
    citaBloqueada = Color(0xFFA8B0BC),
    fondoCitaBloqueada = Color(0xFF1B1F27),
)

val LocalColoresSalud = staticCompositionLocalOf { ColoresSaludClaros }

val LocalEspaciadoSalud = staticCompositionLocalOf { EspaciadoSalud() }

/**
 * Tema raiz de la app. Detecta la preferencia de Modo Claro / Oscuro del
 * sistema; ninguna pantalla decide su propia paleta.
 */
@Composable
fun SaludTheme(
    modoOscuro: Boolean = isSystemInDarkTheme(),
    contenido: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalColoresSalud provides if (modoOscuro) ColoresSaludOscuros else ColoresSaludClaros,
        LocalEspaciadoSalud provides EspaciadoSalud(),
    ) {
        MaterialTheme(
            colorScheme = if (modoOscuro) ColoresOscuros else ColoresClaros,
            content = contenido,
        )
    }
}
