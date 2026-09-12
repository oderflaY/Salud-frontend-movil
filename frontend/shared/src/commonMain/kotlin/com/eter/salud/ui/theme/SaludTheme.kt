// Hallmark - redesign 2026-09-11 (paleta especificada por el usuario) - genero: modern-minimal
// tema: custom "Biotech Premium" - claro: #F0F1F4 / #FFFFFF / zafiro #0A4C86 - oscuro OLED: #000000 / #1C1C1E / neon #4DA3FF
// tipografia: Manrope 400/500/600/700/800 (una familia) - ejes: light+oled / geometric-sans / cool-neutral
// contraste: texto >= 14.9:1, secundario >= 5.1:1, estados >= 5.0:1, senales >= 3.4:1, acento con texto >= 8.0:1
package com.eter.salud.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
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
    /**
     * Fondo de pantalla: gris perla en claro, negro OLED en oscuro.
     * Es el mismo valor que `MaterialTheme.colorScheme.background`.
     */
    val fondo: Color,
    /**
     * Texto principal y titulares ExtraBold: Slate en claro, IceWhite en oscuro.
     * Es el mismo valor que `onBackground` y `onSurface`, asi que un `Text` sin
     * color explicito dentro de una [fondoTarjeta] ya lo hereda.
     */
    val textoPrincipal: Color,
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
    /** Relleno de la accion principal de una pantalla: zafiro en claro, neon en oscuro. */
    val acentoAccion: Color,
    /** Texto sobre [acentoAccion]. */
    val sobreAcentoAccion: Color,
    /** Fondo de los campos de captura: contenedor suave, sin borde agresivo. */
    val fondoCampo: Color,
    /**
     * Fondo de las tarjetas y bloques agrupados del panel principal: blanco en
     * claro, Graphite en oscuro. Nunca lleva sombra; se despega por tono.
     */
    val fondoTarjeta: Color,
    /** Fondo de pantalla del Directorio Medico y del chat. */
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
    // pantalla: cada tarjeta lleva ademas el nombre del estado escrito.

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

    /** Superficie de las zonas heroe y de los discos de avatar: zafiro suave. */
    val veloAcento: Color,

    // --------------------------------------------------- Semaforo de triage
    //
    // El tono SENAL de cada nivel: puntos, halos y el trazo del anillo. Pasan
    // 3:1 contra el fondo (minimo de un elemento no textual) y son algo mas
    // vivos que su version de texto, porque de ellos depende que la alerta se
    // vea sin buscarla. En oscuro son los tonos Glow: el LED sobre el negro
    // OLED. El texto de un nivel sigue usando [exito], [textoAdvertencia] y
    // [acentoCritico].

    /** Paciente sin senales de alarma. */
    val senalEstable: Color,
    /** Conviene revisarlo antes que al resto. */
    val senalVigilancia: Color,
    /** Requiere atencion pronta. */
    val senalCritico: Color,

    /**
     * Fondo de lo destacado y lo seleccionado: el dia elegido del calendario,
     * la tarjeta de la proxima medicina, los circulos de icono. Es el zafiro
     * rebajado (en oscuro, hundido) hasta poder llevar texto principal encima.
     */
    val acentoSuave: Color,

    /** Fondo de una confirmacion ("Ya la tomaste"). */
    val fondoExito: Color,

    /** Si la paleta activa es la oscura. */
    val esModoOscuro: Boolean,
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
    /**
     * Separacion entre bloques mayores de una portada.
     *
     * Existe porque [respiro] es el aire ENTRE piezas de una misma idea, y el
     * salto de "quien eres" a "como vas hoy" es un cambio de tema: con el mismo
     * hueco que separa dos tarjetas, la cifra heroe se leia como un bloque mas
     * de la lista en lugar de como la pantalla.
     */
    val seccion: Dp = 64.dp,
)

/** Separacion vertical por defecto entre bloques de una pregunta. */
val PasoOnboardingEspaciado: Dp = 24.dp

/** Area tactil minima accesible recomendada por las guias de iOS y Android. */
val AreaTactilMinima: Dp = 48.dp

/**
 * Alto de las acciones de pantalla completa (entrar, guardar, confirmar).
 *
 * Por encima del minimo accesible a proposito: el minimo garantiza que se pueda
 * pulsar, no que se vea como la decision principal de la vista.
 */
val AlturaAccion: Dp = 60.dp

/**
 * Alto de la accion que una persona mayor repite a diario ("Ya me la tome").
 *
 * 72dp: la guia de accesibilidad para mayores recomienda objetivos de al menos
 * 60dp porque la precision del dedo cae con la edad y con el temblor; esta
 * accion va un escalon por encima porque es LA accion de la app.
 */
val AlturaAccionMayor: Dp = 72.dp

/**
 * Medidas de las piezas que se repiten en varias pantallas.
 *
 * Existen porque estaban duplicadas: el mismo punto de estado media 7, 8, 10 y
 * 14dp en cuatro archivos. Era la misma pieza visual con cuatro medidas, y eso
 * es lo que hace que una app parezca ensamblada por partes en vez de disenada.
 */
object MedidaSalud {
    /** Punto de estado (semaforo, cumplimiento, severidad). */
    val punto: Dp = 8.dp

    /** Punto con aro, cuando se posa sobre otra pieza y necesita despegarse. */
    val puntoConAro: Dp = 14.dp

    /** Grosor del aro de [puntoConAro]. */
    val aroDelPunto: Dp = 2.dp

    /** Punto de semaforo clinico con halo (triage de un paciente en la cartera). */
    val puntoTriage: Dp = 12.dp

    /** Disco con la inicial: avatar de paciente y de medico. */
    val disco: Dp = 44.dp

    /**
     * Disco de avatar de la bandeja clinica.
     *
     * Mas grande que [disco] porque en la bandeja el avatar no identifica
     * solamente: es el soporte del punto de triage, y el punto necesita un
     * borde de disco lo bastante grande para posarse sin tapar la inicial.
     */
    val discoBandeja: Dp = 52.dp

    /** Glifo dentro de una pildora de estado. */
    val glifoEnPildora: Dp = 16.dp

    /** Tope de ancho de una burbuja de chat. */
    val anchoBurbuja: Dp = 280.dp
}

/**
 * Esquema de Material en Modo Claro.
 *
 * Se declaran TODOS los roles de superficie, contenedores incluidos: los que no
 * se declaran heredan el esquema base de Material -- un lila -- y se cuelan en
 * las hojas, los menus y los dialogos que la app no dibuja a mano.
 *
 * `outline` y `outlineVariant` no son lo mismo: `outline` es el borde de lo que
 * se puede TOCAR (campos de texto, el pulgar del interruptor) y necesita 3:1;
 * `outlineVariant` es el filete decorativo de los divisores de Material.
 */
private val ColoresClaros = lightColorScheme(
    primary = Sapphire,
    onPrimary = White,
    primaryContainer = SapphireMist,
    onPrimaryContainer = Slate,
    inversePrimary = NeonSky,
    secondary = Ash,
    onSecondary = White,
    secondaryContainer = Fog,
    onSecondaryContainer = Slate,
    tertiary = Emerald,
    onTertiary = White,
    tertiaryContainer = EmeraldMist,
    onTertiaryContainer = Emerald,
    background = PearlGray,
    onBackground = Slate,
    surface = White,
    onSurface = Slate,
    surfaceVariant = Fog,
    onSurfaceVariant = Ash,
    // La tinta tonal de Material mezcla `primary` sobre las superficies
    // elevadas y las volveria azuladas y sucias; se neutraliza.
    surfaceTint = White,
    inverseSurface = Slate,
    inverseOnSurface = IceWhite,
    error = Crimson,
    onError = White,
    errorContainer = CrimsonMist,
    onErrorContainer = Crimson,
    outline = Pewter,
    outlineVariant = Hairline,
    scrim = OledBlack,
    surfaceBright = White,
    surfaceDim = Fog,
    surfaceContainerLowest = White,
    surfaceContainerLow = White,
    surfaceContainer = White,
    surfaceContainerHigh = PearlGray,
    surfaceContainerHighest = Fog,
)

/**
 * Esquema de Material en Modo Oscuro OLED.
 *
 * La escalera de superficies sube en luz, nunca en sombra: negro (fondo) ->
 * Graphite (tarjetas, hojas, dialogos) -> Onyx (campos, menus) -> Carbon (lo
 * mas alto). Ningun rol de superficie queda en un tono claro, tampoco
 * `inverseSurface`: en Material oscuro es un gris casi blanco que pintaria
 * cualquier snackbar o tooltip futuro como un parche encendido en la pantalla,
 * asi que aqui es el escalon mas alto de la escalera.
 */
private val ColoresOscuros = darkColorScheme(
    primary = NeonSky,
    onPrimary = OledBlack,
    primaryContainer = SapphireDeep,
    onPrimaryContainer = IceWhite,
    inversePrimary = NeonSky,
    secondary = PaleAsh,
    onSecondary = OledBlack,
    secondaryContainer = Onyx,
    onSecondaryContainer = IceWhite,
    tertiary = EmeraldGlow,
    onTertiary = OledBlack,
    tertiaryContainer = EmeraldDeep,
    onTertiaryContainer = EmeraldGlow,
    background = OledBlack,
    onBackground = IceWhite,
    surface = Graphite,
    onSurface = IceWhite,
    surfaceVariant = Onyx,
    onSurfaceVariant = PaleAsh,
    surfaceTint = Graphite,
    inverseSurface = Carbon,
    inverseOnSurface = IceWhite,
    error = CrimsonGlow,
    onError = OledBlack,
    errorContainer = CrimsonDeep,
    onErrorContainer = CrimsonGlow,
    outline = Smoke,
    outlineVariant = Carbon,
    scrim = OledBlack,
    surfaceBright = Onyx,
    surfaceDim = OledBlack,
    surfaceContainerLowest = OledBlack,
    surfaceContainerLow = Graphite,
    surfaceContainer = Graphite,
    surfaceContainerHigh = Onyx,
    surfaceContainerHighest = Carbon,
)

/** Roles semanticos en Modo Claro: la capa que las pantallas consumen. */
private val ColoresSaludClaros = ColoresSalud(
    fondo = PearlGray,
    textoPrincipal = Slate,
    textoSecundario = Ash,
    separador = Hairline,
    acentoCritico = Crimson,
    fondoCritico = CrimsonMist,
    exito = Emerald,
    acentoAccion = Sapphire,
    sobreAcentoAccion = White,
    fondoCampo = Fog,
    fondoTarjeta = White,
    fondoConversacion = PearlGray,
    fondoBurbujaMedico = White,
    sobreBurbujaMedico = Slate,
    fondoAdvertencia = AmberMist,
    textoAdvertencia = Amber,
    citaPendiente = Amber,
    fondoCitaPendiente = AmberMist,
    citaConfirmada = Emerald,
    fondoCitaConfirmada = EmeraldMist,
    citaEnCurso = Sapphire,
    fondoCitaEnCurso = SapphireMist,
    citaCancelada = Crimson,
    fondoCitaCancelada = CrimsonMist,
    citaBloqueada = Ash,
    fondoCitaBloqueada = Fog,
    veloAcento = SapphireMist,
    senalEstable = EmeraldSignal,
    senalVigilancia = AmberSignal,
    senalCritico = CrimsonSignal,
    acentoSuave = SapphireMist,
    fondoExito = EmeraldMist,
    esModoOscuro = false,
)

/**
 * Roles semanticos en Modo Oscuro OLED: mismo tono por rol, otra luminosidad.
 * Cada fondo es negro, Graphite, Onyx o un tono hundido casi hasta el negro;
 * ninguno es claro.
 */
private val ColoresSaludOscuros = ColoresSalud(
    fondo = OledBlack,
    textoPrincipal = IceWhite,
    textoSecundario = PaleAsh,
    separador = Carbon,
    acentoCritico = CrimsonGlow,
    fondoCritico = CrimsonDeep,
    exito = EmeraldGlow,
    acentoAccion = NeonSky,
    sobreAcentoAccion = OledBlack,
    fondoCampo = Onyx,
    fondoTarjeta = Graphite,
    fondoConversacion = OledBlack,
    fondoBurbujaMedico = Graphite,
    sobreBurbujaMedico = IceWhite,
    fondoAdvertencia = AmberDeep,
    textoAdvertencia = AmberGlow,
    citaPendiente = AmberGlow,
    fondoCitaPendiente = AmberDeep,
    citaConfirmada = EmeraldGlow,
    fondoCitaConfirmada = EmeraldDeep,
    citaEnCurso = NeonSky,
    fondoCitaEnCurso = SapphireDeep,
    citaCancelada = CrimsonGlow,
    fondoCitaCancelada = CrimsonDeep,
    citaBloqueada = PaleAsh,
    fondoCitaBloqueada = Onyx,
    veloAcento = SapphireDeep,
    senalEstable = EmeraldGlow,
    senalVigilancia = AmberGlow,
    senalCritico = CrimsonGlow,
    acentoSuave = SapphireDeep,
    fondoExito = EmeraldDeep,
    esModoOscuro = true,
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
            typography = tipografiaSalud(),
            // Los componentes de Material (hojas modales, tarjetas, menus) leen
            // su forma de aqui. Sin este puente adoptarian el radio por defecto
            // de la libreria y desentonarian justo al lado de las piezas propias.
            shapes = Shapes(
                extraSmall = FormaSalud.sutil,
                small = FormaSalud.sutil,
                medium = FormaSalud.media,
                large = FormaSalud.grande,
                extraLarge = FormaSalud.destacada,
            ),
            content = contenido,
        )
    }
}
