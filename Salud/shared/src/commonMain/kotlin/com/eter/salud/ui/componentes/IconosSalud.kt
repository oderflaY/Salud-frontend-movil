package com.eter.salud.ui.componentes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Iconografia del panel principal.
 *
 * Se dibuja con primitivas en lugar de depender de un paquete de iconos: el
 * artefacto de Material Icons no esta publicado para esta version de Compose
 * Multiplatform, y dibujarlos aqui garantiza ademas que hereden el color del
 * tema y escalen sin perdida en ambos modos.
 *
 * Son glifos estandar y reconocibles (tarjeta, documento, persona, reloj), sin
 * emojis ni adornos, como exige el DM.
 */
enum class GlifoSalud {
    TARJETA,
    DIARIO,
    MEDICOS,
    HISTORIAL,
    ONDAS_NFC,
    VERIFICADO,
    BUSCAR,
    ENVIAR,
}

/**
 * Icono decorativo: se marca sin semantica porque su significado ya lo aporta
 * la etiqueta del elemento que lo contiene. Duplicarlo haria que TalkBack y
 * VoiceOver leyeran lo mismo dos veces.
 */
@Composable
fun IconoSalud(
    glifo: GlifoSalud,
    modifier: Modifier = Modifier,
    lado: Dp = 28.dp,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Canvas(
        modifier = modifier
            .size(lado)
            .clearAndSetSemantics { },
    ) {
        val trazo = Stroke(width = size.minDimension * PROPORCION_TRAZO)
        when (glifo) {
            GlifoSalud.TARJETA -> dibujarTarjeta(color, trazo)
            GlifoSalud.DIARIO -> dibujarDiario(color, trazo)
            GlifoSalud.MEDICOS -> dibujarPersona(color, trazo)
            GlifoSalud.HISTORIAL -> dibujarReloj(color, trazo)
            GlifoSalud.ONDAS_NFC -> dibujarOndasNfc(color, trazo)
            GlifoSalud.VERIFICADO -> dibujarVerificado(color, trazo)
            GlifoSalud.BUSCAR -> dibujarBuscar(color, trazo)
            GlifoSalud.ENVIAR -> dibujarEnviar(color, trazo)
        }
    }
}

/** Tarjeta con banda magnetica: la forma con la que se lee el RFID. */
private fun DrawScope.dibujarTarjeta(color: Color, trazo: Stroke) {
    val margen = size.minDimension * 0.1f
    val alto = size.height * 0.62f
    drawRoundRect(
        color = color,
        topLeft = Offset(margen, (size.height - alto) / 2f),
        size = Size(size.width - margen * 2, alto),
        cornerRadius = CornerRadius(size.minDimension * 0.12f),
        style = trazo,
    )
    drawLine(
        color = color,
        start = Offset(margen, size.height * 0.44f),
        end = Offset(size.width - margen, size.height * 0.44f),
        strokeWidth = trazo.width,
    )
}

/** Documento con renglones: el diario de sintomas. */
private fun DrawScope.dibujarDiario(color: Color, trazo: Stroke) {
    val margenX = size.width * 0.18f
    val margenY = size.height * 0.1f
    drawRoundRect(
        color = color,
        topLeft = Offset(margenX, margenY),
        size = Size(size.width - margenX * 2, size.height - margenY * 2),
        cornerRadius = CornerRadius(size.minDimension * 0.1f),
        style = trazo,
    )
    listOf(0.36f, 0.52f, 0.68f).forEach { proporcion ->
        drawLine(
            color = color,
            start = Offset(margenX + size.width * 0.1f, size.height * proporcion),
            end = Offset(size.width - margenX - size.width * 0.1f, size.height * proporcion),
            strokeWidth = trazo.width,
        )
    }
}

/** Busto: la representacion estandar de una persona. */
private fun DrawScope.dibujarPersona(color: Color, trazo: Stroke) {
    drawCircle(
        color = color,
        radius = size.minDimension * 0.17f,
        center = Offset(size.width / 2f, size.height * 0.32f),
        style = trazo,
    )
    drawArc(
        color = color,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(size.width * 0.2f, size.height * 0.58f),
        size = Size(size.width * 0.6f, size.height * 0.5f),
        style = trazo,
    )
}

/** Reloj: el historial es la linea de tiempo del paciente. */
private fun DrawScope.dibujarReloj(color: Color, trazo: Stroke) {
    val radio = size.minDimension * 0.38f
    val centro = Offset(size.width / 2f, size.height / 2f)
    drawCircle(color = color, radius = radio, center = centro, style = trazo)
    drawLine(
        color = color,
        start = centro,
        end = Offset(centro.x, centro.y - radio * 0.55f),
        strokeWidth = trazo.width,
    )
    drawLine(
        color = color,
        start = centro,
        end = Offset(centro.x + radio * 0.45f, centro.y),
        strokeWidth = trazo.width,
    )
}

/**
 * Ondas concentricas: version estatica del icono NFC para usarse dentro de
 * botones (por ejemplo, la accion de escaneo de la Home del profesional). La
 * animacion pulsante de la pantalla de escaneo vive aparte, en
 * `EscanerEmergenciaScreen`, porque solo tiene sentido ahi.
 */
private fun DrawScope.dibujarOndasNfc(color: Color, trazo: Stroke) {
    val centro = Offset(size.width / 2f, size.height / 2f)
    listOf(0.32f, 0.62f, 0.95f).forEach { proporcion ->
        drawCircle(
            color = color,
            radius = size.minDimension / 2f * proporcion,
            center = centro,
            style = trazo,
        )
    }
}

/** Sello con marca de verificacion: la Cedula Profesional ya fue validada. */
private fun DrawScope.dibujarVerificado(color: Color, trazo: Stroke) {
    val radio = size.minDimension * 0.42f
    val centro = Offset(size.width / 2f, size.height / 2f)
    drawCircle(color = color, radius = radio, center = centro, style = trazo)
    val marca = Path().apply {
        moveTo(centro.x - radio * 0.42f, centro.y)
        lineTo(centro.x - radio * 0.06f, centro.y + radio * 0.34f)
        lineTo(centro.x + radio * 0.46f, centro.y - radio * 0.38f)
    }
    drawPath(path = marca, color = color, style = trazo)
}

/** Lupa: la barra de busqueda del Directorio Medico. */
private fun DrawScope.dibujarBuscar(color: Color, trazo: Stroke) {
    val radio = size.minDimension * 0.32f
    val centro = Offset(size.width * 0.42f, size.height * 0.42f)
    drawCircle(color = color, radius = radio, center = centro, style = trazo)
    drawLine(
        color = color,
        start = Offset(centro.x + radio * 0.72f, centro.y + radio * 0.72f),
        end = Offset(size.width * 0.86f, size.height * 0.86f),
        strokeWidth = trazo.width,
        cap = StrokeCap.Round,
    )
}

/** Dardo de envio: el boton de mandar un mensaje en el chat. */
private fun DrawScope.dibujarEnviar(color: Color, trazo: Stroke) {
    val dardo = Path().apply {
        moveTo(size.width * 0.12f, size.height * 0.50f)
        lineTo(size.width * 0.88f, size.height * 0.15f)
        lineTo(size.width * 0.62f, size.height * 0.88f)
        lineTo(size.width * 0.50f, size.height * 0.56f)
        close()
    }
    drawPath(path = dardo, color = color, style = trazo)
    drawLine(
        color = color,
        start = Offset(size.width * 0.50f, size.height * 0.56f),
        end = Offset(size.width * 0.88f, size.height * 0.15f),
        strokeWidth = trazo.width,
    )
}

private const val PROPORCION_TRAZO = 0.08f
