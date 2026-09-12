package com.eter.salud.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font
import salud.shared.generated.resources.Res
import salud.shared.generated.resources.manrope_bold
import salud.shared.generated.resources.manrope_extrabold
import salud.shared.generated.resources.manrope_medium
import salud.shared.generated.resources.manrope_regular
import salud.shared.generated.resources.manrope_semibold

/**
 * Escala tipografica de la app.
 *
 * ## Por que Manrope
 *
 * Es una grotesca geometrica moderna, de ojo medio alto y formas abiertas: se
 * lee limpia a tamano grande sin volverse infantil, y sus cifras son claras y
 * tabulares (`tnum`), asi que las horas de las tomas se alinean en columna.
 * Acompana al indigo con la misma voz: actual, tranquila, sin adornos.
 *
 * Una sola familia para todo. La jerarquia la dan el tamano y el peso (400
 * contra 800), no una segunda fuente.
 *
 * Licencia: SIL Open Font License 1.1, incluida como recurso.
 *
 * ## La escala es grande a proposito
 *
 * El texto de lectura va a 17-18sp (Material usa 14-16) y ninguna etiqueta baja
 * de 13sp. Es la recomendacion de las guias de accesibilidad para mayores: a
 * partir de los 60 anos la mayoria necesita un 20-30% mas de tamano para leer al
 * mismo ritmo. Y como todo va en `sp`, el ajuste de tamano de letra del telefono
 * sigue funcionando encima.
 */

/**
 * Recorta el espacio muerto que la fuente reserva encima y debajo del texto.
 *
 * Sin esto, un titular con interlineado holgado queda visualmente descentrado
 * dentro de su caja y ningun `padding` simetrico lo arregla.
 */
private val AJUSTE_LINEA = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

/** Cifras tabulares: toda cifra que se compara con otra ocupa el mismo ancho. */
private const val CIFRAS_TABULARES = "tnum"

/** La familia del sistema, en los cuatro pesos que la escala usa. */
@Composable
fun familiaSalud(): FontFamily = FontFamily(
    Font(Res.font.manrope_regular, FontWeight.Normal),
    Font(Res.font.manrope_medium, FontWeight.Medium),
    Font(Res.font.manrope_semibold, FontWeight.SemiBold),
    Font(Res.font.manrope_bold, FontWeight.Bold),
    Font(Res.font.manrope_extrabold, FontWeight.ExtraBold),
)

private fun estilo(
    familia: FontFamily,
    tamano: Int,
    interlineado: Int,
    peso: FontWeight,
    interletrado: Double,
    tabular: Boolean = false,
) = TextStyle(
    fontFamily = familia,
    fontSize = tamano.sp,
    lineHeight = interlineado.sp,
    fontWeight = peso,
    letterSpacing = interletrado.sp,
    lineHeightStyle = AJUSTE_LINEA,
    fontFeatureSettings = if (tabular) CIFRAS_TABULARES else null,
)

/**
 * La escala de Material, en Manrope y a tamano de lectura comoda.
 *
 * Composable porque las fuentes de recursos de Compose Multiplatform solo se
 * resuelven dentro de una composicion; se memoriza por familia.
 */
@Composable
fun tipografiaSalud(): Typography {
    val familia = familiaSalud()
    return remember(familia) {
        Typography(
            // ---------------------------------------------- Display: cifras heroe
            displayLarge = estilo(familia, 40, 46, FontWeight.ExtraBold, -0.6, tabular = true),
            displayMedium = estilo(familia, 34, 40, FontWeight.ExtraBold, -0.4, tabular = true),
            displaySmall = estilo(familia, 30, 36, FontWeight.ExtraBold, -0.3, tabular = true),

            // ------------------------------------------ Headline: titulo de vista
            headlineLarge = estilo(familia, 32, 38, FontWeight.ExtraBold, -0.4),
            headlineMedium = estilo(familia, 28, 34, FontWeight.Bold, -0.3),
            headlineSmall = estilo(familia, 24, 30, FontWeight.Bold, -0.2),

            // ------------------------------------------ Title: cabecera de bloque
            titleLarge = estilo(familia, 22, 28, FontWeight.Bold, -0.1),
            titleMedium = estilo(familia, 19, 26, FontWeight.Bold, 0.0),
            titleSmall = estilo(familia, 17, 24, FontWeight.Bold, 0.0),

            // --------------------------------------------------------- Body: lectura
            bodyLarge = estilo(familia, 18, 27, FontWeight.Normal, 0.1),
            bodyMedium = estilo(familia, 17, 25, FontWeight.Normal, 0.1),
            bodySmall = estilo(familia, 15, 22, FontWeight.Normal, 0.15),

            // ----------------------------------------------------- Label: escaneo
            labelLarge = estilo(familia, 17, 22, FontWeight.Bold, 0.1, tabular = true),
            labelMedium = estilo(familia, 15, 20, FontWeight.Medium, 0.15, tabular = true),
            labelSmall = estilo(familia, 13, 18, FontWeight.Medium, 0.2, tabular = true),
        )
    }
}

/**
 * Estilos fuera de la escala de Material, para las piezas que la escala no sabe
 * nombrar. Heredan la familia de la escala activa: un `TextStyle` sin familia no
 * hereda nada, porque `Text(style = ...)` REEMPLAZA el estilo local.
 */
object CifraSalud {

    private val familia: FontFamily?
        @Composable get() = MaterialTheme.typography.displayLarge.fontFamily

    /** "Buenos dias," antes del nombre: grande para leerse, ligero para cederle el sitio. */
    val saludo: TextStyle
        @Composable get() = TextStyle(
            fontFamily = familia,
            fontSize = 24.sp,
            lineHeight = 30.sp,
            fontWeight = FontWeight.Normal,
            lineHeightStyle = AJUSTE_LINEA,
        )

    /** El nombre del paciente en la portada. */
    val nombre: TextStyle
        @Composable get() = TextStyle(
            fontFamily = familia,
            fontSize = 38.sp,
            lineHeight = 44.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.5).sp,
            lineHeightStyle = AJUSTE_LINEA,
        )

    /** Titulo de una seccion raiz (panel, agenda, bandeja). */
    val portada: TextStyle
        @Composable get() = TextStyle(
            fontFamily = familia,
            fontSize = 36.sp,
            lineHeight = 42.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.5).sp,
            lineHeightStyle = AJUSTE_LINEA,
        )

    /** La frase que resume el dia: "Te faltan 2 de 4". */
    val dato: TextStyle
        @Composable get() = TextStyle(
            fontFamily = familia,
            fontSize = 30.sp,
            lineHeight = 36.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.3).sp,
            lineHeightStyle = AJUSTE_LINEA,
            fontFeatureSettings = CIFRAS_TABULARES,
        )

    /** La hora de una toma o de una cita: el dato por el que se busca la fila. */
    val hora: TextStyle
        @Composable get() = TextStyle(
            fontFamily = familia,
            fontSize = 26.sp,
            lineHeight = 30.sp,
            fontWeight = FontWeight.ExtraBold,
            lineHeightStyle = AJUSTE_LINEA,
            fontFeatureSettings = CIFRAS_TABULARES,
        )

    /**
     * La cifra de un anillo, dimensionada contra el propio anillo y no en `sp`:
     * tiene que caber dentro del trazo a cualquier tamano de letra del sistema.
     */
    @Composable
    fun anillo(diametro: Dp): TextStyle {
        val tamano = with(LocalDensity.current) { (diametro * PROPORCION_CIFRA_ANILLO).toSp() }
        return TextStyle(
            fontFamily = familia,
            fontSize = tamano,
            lineHeight = tamano,
            fontWeight = FontWeight.ExtraBold,
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.Both,
            ),
            fontFeatureSettings = CIFRAS_TABULARES,
        )
    }

    /** El simbolo `%` de un anillo. */
    @Composable
    fun unidadAnillo(diametro: Dp): TextStyle {
        val tamano = with(LocalDensity.current) { (diametro * PROPORCION_UNIDAD_ANILLO).toSp() }
        return TextStyle(
            fontFamily = familia,
            fontSize = tamano,
            lineHeight = tamano,
            fontWeight = FontWeight.Bold,
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.Both,
            ),
        )
    }
}

private const val PROPORCION_CIFRA_ANILLO = 0.30f
private const val PROPORCION_UNIDAD_ANILLO = 0.10f
