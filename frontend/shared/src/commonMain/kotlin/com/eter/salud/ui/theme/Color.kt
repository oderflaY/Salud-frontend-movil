package com.eter.salud.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Paleta cruda del sistema "Biotech Premium".
 *
 * Son los UNICOS valores literales de color del proyecto, y ninguna pantalla los
 * nombra: las vistas piden roles ([ColoresSalud.fondoTarjeta], `primary`...) y
 * es [SaludTheme] quien decide que valor ocupa cada rol en cada modo.
 *
 * ## La idea
 *
 * Instrumental de laboratorio: neutros sin tinte, un solo acento zafiro y color
 * de verdad unicamente donde hay una senal clinica. El croma se reserva para lo
 * que el paciente o el medico tienen que ver sin buscarlo.
 *
 * ## El modo oscuro es OLED, no gris
 *
 * El fondo es negro puro (#000000): en una pantalla OLED ese pixel se apaga, y
 * eso es bateria y es el negro mas profundo que el panel puede dar. Sobre el:
 *
 *  - Las tarjetas se despegan por TONO, con [Graphite], nunca con sombra: una
 *    sombra sobre negro no existe. Se eligio Graphite (#1C1C1E, 1.23:1 contra
 *    el negro) y no EerieBlack (#121212, 1.12:1) porque este ultimo se funde
 *    con el fondo en cuanto baja el brillo.
 *  - La elevacion se dice con luz: campo, filete y superficie inversa suben un
 *    escalon de gris cada uno ([Onyx], [Carbon]).
 *  - Ninguna superficie clara sobrevive: ni tarjetas, ni hojas, ni dialogos,
 *    ni la superficie inversa de Material.
 *  - Los semaforos pasan a tonos "Glow", saturados y luminosos, para que el
 *    punto de triage brille como el LED de un monitor sobre el negro.
 *
 * ## Contraste verificado (WCAG 2.x)
 *
 * Texto principal >= 14.9:1 en claro y >= 15.6:1 en oscuro. Texto secundario
 * >= 5.1:1. Texto de estado >= 5.0:1 sobre fondo y tarjeta. Senales (puntos,
 * barras) >= 3.4:1, el minimo de un elemento no textual es 3:1. Ningun estado
 * viaja solo en color: siempre lleva icono y palabra.
 */

// --------------------------------------------------------- Neutros, claro
/** Fondo de pantalla: gris perla frio, apenas separado del blanco. */
val PearlGray = Color(0xFFF0F1F4)

/** Tarjeta: blanco puro. Se despega del perla por tono y por filete. */
val White = Color(0xFFFFFFFF)

/** Campos de captura, pistas vacias, circulos de icono. */
val Fog = Color(0xFFE7E8EC)

/** Filete de tarjeta y divisores: 1.38:1 contra la tarjeta. */
val Hairline = Color(0xFFD9DBE1)

/** Borde de lo que se puede tocar (campos, interruptores): 3.25:1 contra el fondo. */
val Pewter = Color(0xFF84858D)

/** Texto principal y titulares ExtraBold. 16.8:1 sobre la tarjeta. */
val Slate = Color(0xFF1D1D1F)

/** Texto secundario en claro. 5.5:1 sobre el fondo. */
val Ash = Color(0xFF606067)

// -------------------------------------------------------- Neutros, oscuro
/** Fondo de pantalla: negro OLED, el pixel apagado. */
val OledBlack = Color(0xFF000000)

/** Tarjeta: grafito profundo, separado del negro sin sombra. */
val Graphite = Color(0xFF1C1C1E)

/** Campos de captura: un escalon de luz por encima de la tarjeta. */
val Onyx = Color(0xFF2C2C2E)

/** Filete de tarjeta y divisores: 1.45:1 contra la tarjeta. */
val Carbon = Color(0xFF38383A)

/** Borde de lo que se puede tocar: 3.8:1 contra la tarjeta. */
val Smoke = Color(0xFF76767D)

/** Texto principal en oscuro: blanco hueso, no blanco puro. 15.6:1 sobre la tarjeta. */
val IceWhite = Color(0xFFF5F5F7)

/**
 * Texto secundario en oscuro: el mismo Ash, subido de luminosidad para
 * mantener 6.6:1 sobre la tarjeta (el Ash de claro caeria a 2.7:1).
 */
val PaleAsh = Color(0xFFA1A1A8)

// ----------------------------------------------------------------- Acento
/** Zafiro: botones, seleccion, enlaces en claro. Texto blanco encima a 8.8:1. */
val Sapphire = Color(0xFF0A4C86)

/** Zafiro rebajado: fondo de lo destacado y lo seleccionado en claro. */
val SapphireMist = Color(0xFFE3ECF7)

/** Cielo neon: el acento en oscuro, con texto en negro OLED encima (8.0:1). */
val NeonSky = Color(0xFF4DA3FF)

/** Zafiro hundido: fondo de lo destacado en oscuro. NeonSky encima a 5.8:1. */
val SapphireDeep = Color(0xFF0C2742)

// ------------------------------------------------------ Estados, claro
//
// Tres tonos por estado: el de TEXTO (oscuro, >= 5.4:1), el SUAVE (fondo de
// avisos y pildoras) y el de SENAL (puntos y barras: mas vivo que el de texto,
// pero todavia >= 3.4:1 sobre el fondo perla).

val Emerald = Color(0xFF036B4D)
val EmeraldMist = Color(0xFFE1F4EB)
val EmeraldSignal = Color(0xFF0B9463)

val Amber = Color(0xFF915200)
val AmberMist = Color(0xFFFCF0DA)
val AmberSignal = Color(0xFFC46A00)

val Crimson = Color(0xFFBE1E2D)
val CrimsonMist = Color(0xFFFCE7E9)
val CrimsonSignal = Color(0xFFE0263C)

// ----------------------------------------------------- Estados, oscuro
//
// En oscuro el tono de texto y el de senal son el MISMO: saturado y luminoso,
// el LED sobre el negro. El fondo suave es el propio tono hundido casi hasta
// el negro, para que un aviso nunca encienda una superficie clara.

val EmeraldGlow = Color(0xFF30E0A1)
val EmeraldDeep = Color(0xFF07261B)

val AmberGlow = Color(0xFFFFB73D)
val AmberDeep = Color(0xFF2B1E07)

val CrimsonGlow = Color(0xFFFF5A67)
val CrimsonDeep = Color(0xFF2E0D12)

// ------------------------------------------------------- Halo del semaforo
/**
 * Opacidades del difuminado que rodea al punto de triage de la cartera.
 *
 * Son tokens y no numeros sueltos en la pieza porque definen CUANTO grita una
 * senal clinica, que es una decision de sistema.
 */
object HaloTriage {
    /** Opacidad del halo justo en el borde del punto. */
    const val CENTRO = 0.45f

    /** Opacidad a media distancia, antes de apagarse del todo. */
    const val MEDIO = 0.16f

    /** Cuantas veces el diametro del punto mide el halo completo. */
    const val FACTOR_RADIO = 2.6f
}
