package com.eter.salud.ui.componentes

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.eter.salud.ui.theme.CifraSalud
import com.eter.salud.ui.theme.FormaSalud
import com.eter.salud.ui.theme.HaloTriage
import com.eter.salud.ui.theme.LocalColoresSalud
import com.eter.salud.ui.theme.LocalEspaciadoSalud
import com.eter.salud.ui.theme.MedidaSalud
import com.eter.salud.ui.theme.MovimientoSalud
import kotlin.math.roundToInt

/**
 * Piezas del lenguaje visual de la app.
 *
 * A diferencia de `ComponentesSalud.kt`, que resuelve controles (botones,
 * campos), esto es COMPOSICION: las formas grandes que le dan cara a una
 * pantalla. Estan aqui y no dentro de cada vista para que dos pantallas
 * distintas se reconozcan como la misma app.
 */

/**
 * Cabecera de titulo grande, al estilo de las apps nativas de sistema.
 *
 * Sustituye a la `TopAppBar` con el titulo diminuto y centrado que llevaban las
 * secciones: una barra superior gasta 56dp de alto para decir una palabra en
 * 17sp; esta cabecera usa ese mismo espacio para decir DONDE estas, QUE hay aqui
 * y POR QUE importa.
 */
@Composable
fun CabeceraGrande(
    titulo: String,
    modifier: Modifier = Modifier,
    sobretitulo: String? = null,
    apoyo: String? = null,
    accion: (@Composable () -> Unit)? = null,
) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current

    Column(modifier = modifier.fillMaxWidth()) {
        if (accion != null) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) { accion() }
        }
        if (sobretitulo != null) {
            Text(
                text = sobretitulo,
                style = MaterialTheme.typography.labelLarge,
                color = colores.textoSecundario,
            )
            Spacer(Modifier.height(espaciado.minimo))
        }
        Text(
            text = titulo,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() },
        )
        if (apoyo != null) {
            Spacer(Modifier.height(espaciado.compacto))
            Text(
                text = apoyo,
                style = MaterialTheme.typography.bodyMedium,
                color = colores.textoSecundario,
            )
        }
    }
}

/**
 * Anillo de adherencia: la cifra heroe del panel del paciente.
 *
 * ## Masivo y grueso
 *
 * El diametro lo decide quien lo coloca (el panel lo lleva a ~86% del ancho) y
 * el trazo es fijo y grueso. A trazo fino, un anillo grande se lee como un
 * grafico decorativo; a 22dp se lee como un instrumento: la parte llena tiene
 * masa suficiente para compararse de un vistazo con la parte vacia.
 *
 * ## La cifra, en el centro EXACTO
 *
 * El numero se centra por si solo y el `%` cuelga a su derecha sin participar en
 * el centrado. Con un `Row(numero, %)` centrado, el conjunto quedaria al centro
 * pero el NUMERO se desplazaria a la izquierda la mitad del ancho del simbolo,
 * y en un circulo perfecto ese descuadre se ve a simple vista. Por eso la
 * composicion es un `Layout` propio y no una fila.
 *
 * ## El unico movimiento de la pantalla
 *
 * Al aparecer, el arco se llena desde cero y la cifra cuenta a la par
 * ([MovimientoSalud.REVELADO], salida exponencial). Al registrar una toma, el
 * arco avanza desde donde estaba. En Android el recorrido obedece a la escala de
 * animaciones del sistema, asi que con "Quitar animaciones" la cifra aparece
 * directamente en su valor final.
 *
 * @param avance fraccion cumplida, de 0 a 1.
 * @param cifra el porcentaje entero que se pinta al centro, o nulo cuando no hay
 * nada que medir -- se pinta una raya, nunca un "0%" que regañaria al paciente
 * por un dia sin tratamiento.
 */
@Composable
fun AnilloProgreso(
    avance: Float,
    cifra: Int?,
    unidad: String,
    descripcionAccesible: String,
    color: Color,
    diametro: Dp,
    modifier: Modifier = Modifier,
    grosor: Dp = GROSOR_ANILLO,
) {
    val colores = LocalColoresSalud.current
    val objetivo = avance.coerceIn(0f, 1f)
    val progreso = remember { Animatable(0f) }
    LaunchedEffect(objetivo) {
        progreso.animateTo(
            targetValue = objetivo,
            animationSpec = tween(MovimientoSalud.REVELADO, easing = MovimientoSalud.SALIDA),
        )
    }

    // La cifra cuenta con el arco: el numero que se lee es siempre el del arco
    // que se ve, tambien a mitad del recorrido.
    val cifraVisible = when {
        cifra == null -> null
        objetivo <= 0f -> cifra
        else -> (progreso.value / objetivo * cifra).roundToInt().coerceIn(0, cifra)
    }

    Box(
        modifier = modifier
            .size(diametro)
            .semantics(mergeDescendants = true) { contentDescription = descripcionAccesible },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val anchoTrazo = grosor.toPx()
            val trazo = Stroke(width = anchoTrazo, cap = StrokeCap.Round)
            val margen = anchoTrazo / 2f
            val lado = Size(size.width - anchoTrazo, size.height - anchoTrazo)

            // Pista completa detras: sin ella, un avance bajo se lee como un
            // arco suelto y no como "poco de un total".
            drawArc(
                color = colores.fondoCampo,
                startAngle = 0f,
                sweepAngle = GRADOS_CIRCULO,
                useCenter = false,
                topLeft = Offset(margen, margen),
                size = lado,
                style = trazo,
            )
            val barrido = GRADOS_CIRCULO * progreso.value
            // Un arco de cero grados con remate redondo pinta un PUNTO: un 0%
            // mostraria una gota de color como si hubiera algo cumplido.
            if (barrido > BARRIDO_MINIMO) {
                drawArc(
                    color = color,
                    // Arranca arriba, como un reloj.
                    startAngle = ANGULO_INICIAL,
                    sweepAngle = barrido,
                    useCenter = false,
                    topLeft = Offset(margen, margen),
                    size = lado,
                    style = trazo,
                )
            }
        }
        CifraCentrada(
            cifra = cifraVisible?.toString() ?: SIN_CIFRA,
            unidad = if (cifraVisible != null) unidad else null,
            diametro = diametro,
        )
    }
}

/**
 * La cifra centrada a si misma, con la unidad colgando fuera del centrado y
 * apoyada en la misma linea base.
 */
@Composable
private fun CifraCentrada(cifra: String, unidad: String?, diametro: Dp) {
    val colores = LocalColoresSalud.current
    val separacion = LocalEspaciadoSalud.current.minimo

    Layout(
        modifier = Modifier.fillMaxSize(),
        content = {
            Text(
                text = cifra,
                style = CifraSalud.anillo(diametro),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
            )
            if (unidad != null) {
                Text(
                    text = unidad,
                    style = CifraSalud.unidadAnillo(diametro),
                    color = colores.textoSecundario,
                    maxLines = 1,
                )
            }
        },
    ) { medibles, restricciones ->
        val libres = restricciones.copy(minWidth = 0, minHeight = 0)
        val numero = medibles[0].measure(libres)
        val simbolo = medibles.getOrNull(1)?.measure(libres)

        layout(restricciones.maxWidth, restricciones.maxHeight) {
            val xNumero = (restricciones.maxWidth - numero.width) / 2
            val yNumero = (restricciones.maxHeight - numero.height) / 2
            numero.place(xNumero, yNumero)

            if (simbolo != null) {
                val baseNumero = numero[FirstBaseline]
                val baseSimbolo = simbolo[FirstBaseline]
                val ySimbolo = if (baseNumero > 0 && baseSimbolo > 0) {
                    yNumero + baseNumero - baseSimbolo
                } else {
                    yNumero
                }
                simbolo.place(xNumero + numero.width + separacion.roundToPx(), ySimbolo)
            }
        }
    }
}

/**
 * Punto de semaforo clinico con halo: el triage de un paciente en la cartera.
 *
 * Se conserva para el panel del profesional, donde el nivel va ademas ESCRITO
 * bajo el nombre. La bandeja de conversaciones, que prohibe el texto de riesgo,
 * usa [PuntoLuminoso], que codifica el nivel tambien en intensidad y latido.
 *
 * El difuminado es un `radialGradient` y no `Modifier.blur`: `blur` no existe en
 * Android por debajo de la API 31 y se comporta distinto en iOS.
 */
@Composable
fun PuntoDeTriage(
    color: Color,
    modifier: Modifier = Modifier,
    diametro: Dp = MedidaSalud.puntoTriage,
) {
    val relleno by animateColorAsState(
        targetValue = color,
        animationSpec = tween(MovimientoSalud.MEDIO),
        label = "colorTriage",
    )

    Canvas(
        modifier = modifier
            .size(diametro * HaloTriage.FACTOR_RADIO)
            .clearAndSetSemantics { },
    ) {
        val centro = Offset(size.width / 2f, size.height / 2f)
        val radioHalo = size.minDimension / 2f
        val radioPunto = diametro.toPx() / 2f
        val bordeDelPunto = radioPunto / radioHalo

        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    bordeDelPunto to relleno.copy(alpha = HaloTriage.CENTRO),
                    (bordeDelPunto + 1f) / 2f to relleno.copy(alpha = HaloTriage.MEDIO),
                    1f to Color.Transparent,
                ),
                center = centro,
                radius = radioHalo,
            ),
            radius = radioHalo,
            center = centro,
        )
        drawCircle(color = relleno, radius = radioPunto, center = centro)
    }
}

/**
 * Glow Dot: un punto de color con halo difuminado que flota sobre un avatar.
 *
 * ## Tres canales, ninguno de texto
 *
 * Cuando el nivel de riesgo NO puede escribirse junto al punto, el color no
 * puede ser el unico canal: un circulo verde y uno rojo son el mismo circulo
 * para una de cada doce personas. Este punto codifica el nivel tres veces:
 *
 *  1. **Tono** -- estable, vigilancia, critico.
 *  2. **Intensidad** -- el halo crece y se enciende con la gravedad
 *     ([alcance] e [intensidad]); un paciente critico se ve sin buscarlo aunque
 *     no se distinga su color.
 *  3. **Latido** -- solo el nivel mas grave respira ([late]), a menos de 1Hz.
 *
 * El cuarto canal no es visual: quien lo contiene anuncia el nivel en su
 * descripcion accesible, y por eso este glifo se declara decorativo.
 *
 * ## El aro
 *
 * Un anillo del color del fondo separa el punto del disco sobre el que se posa.
 * Sin el, un punto ambar sobre un disco gris claro pierde el contraste de 3:1
 * que necesita para leerse como senal y no como una mancha del avatar.
 *
 * @param diametro el del punto solido, sin halo.
 * @param alcance cuantas veces [diametro] mide el halo completo.
 * @param intensidad opacidad del halo junto al borde del punto.
 */
@Composable
fun PuntoLuminoso(
    color: Color,
    colorAro: Color,
    diametro: Dp,
    alcance: Float,
    intensidad: Float,
    modifier: Modifier = Modifier,
    late: Boolean = false,
    grosorAro: Dp = MedidaSalud.aroDelPunto,
) {
    // El latido solo existe si hace falta: una transicion infinita en veinte
    // filas estables seria trabajo de cada fotograma para no mover nada.
    val pulso = if (late) {
        val transicion = rememberInfiniteTransition(label = "latidoTriage")
        val valor by transicion.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(MovimientoSalud.LATIDO, easing = MovimientoSalud.VAIVEN),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "fasePulso",
        )
        valor
    } else {
        1f
    }

    Canvas(
        modifier = modifier
            .size(diametro * alcance)
            .clearAndSetSemantics { },
    ) {
        val centro = Offset(size.width / 2f, size.height / 2f)
        val radioPunto = diametro.toPx() / 2f
        val radioAro = radioPunto + grosorAro.toPx()
        // Con latido, el halo respira entre el 78% y el 100% de su alcance y
        // entre el 55% y el 100% de su intensidad. Sin latido, queda fijo arriba.
        val radioHalo = size.minDimension / 2f * (ALCANCE_MINIMO_LATIDO + (1f - ALCANCE_MINIMO_LATIDO) * pulso)
        val opacidad = intensidad * (INTENSIDAD_MINIMA_LATIDO + (1f - INTENSIDAD_MINIMA_LATIDO) * pulso)
        val bordeDelAro = (radioAro / radioHalo).coerceAtMost(0.99f)

        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    bordeDelAro to color.copy(alpha = opacidad),
                    (bordeDelAro + 1f) / 2f to color.copy(alpha = opacidad * CAIDA_HALO),
                    1f to Color.Transparent,
                ),
                center = centro,
                radius = radioHalo,
            ),
            radius = radioHalo,
            center = centro,
        )
        drawCircle(color = colorAro, radius = radioAro, center = centro)
        drawCircle(color = color, radius = radioPunto, center = centro)
    }
}

/**
 * Accion flotante extendida: el `ExtendedFloatingActionButton` de Material 3,
 * plano.
 *
 * ## Por que sin sombra
 *
 * Material eleva el boton flotante 6dp para despegarlo de la lista que pasa por
 * debajo. En este sistema el despegue lo hace el CONTRASTE: el relleno es tinta
 * sobre papel -- 16:1 -- y ninguna fila de la lista se le parece. La sombra no
 * aportaba separacion, solo un gris sucio alrededor del unico elemento negro de
 * la pantalla.
 *
 * Lleva la palabra junto al glifo porque un circulo con un simbolo obliga a
 * adivinar; la etiqueta cuesta 90dp de ancho y elimina la duda.
 */
@Composable
fun AccionExtendidaFlotante(
    etiqueta: String,
    glifo: GlifoSalud,
    descripcionAccesible: String,
    alPulsar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colores = LocalColoresSalud.current
    val fuenteDeInteraccion = remember { MutableInteractionSource() }

    ExtendedFloatingActionButton(
        text = {
            Text(
                text = etiqueta,
                style = MaterialTheme.typography.labelLarge,
                // Una etiqueta de accion NUNCA parte en dos lineas.
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        icon = {
            IconoSalud(glifo = glifo, lado = LADO_GLIFO_FLOTANTE, color = colores.sobreAcentoAccion)
        },
        onClick = alPulsar,
        modifier = modifier
            .heightIn(min = ALTO_FLOTANTE)
            .superficiePulsable(fuenteDeInteraccion, FormaSalud.pastilla)
            .semantics { contentDescription = descripcionAccesible },
        shape = FormaSalud.pastilla,
        containerColor = colores.acentoAccion,
        contentColor = colores.sobreAcentoAccion,
        elevation = FloatingActionButtonDefaults.elevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            focusedElevation = 0.dp,
            hoveredElevation = 0.dp,
        ),
        interactionSource = fuenteDeInteraccion,
    )
}

/**
 * Cabecera a escala de portada: el titulo de una seccion raiz, alineado a la
 * izquierda.
 *
 * [saludo] va ARRIBA y en gris: es la parte que no cambia, y el titulo se queda
 * solo en tinta a tamano de portada. [accion] va en su propia fila superior,
 * alineada a la derecha, para que el titular arranque pegado al margen y no lo
 * desplace un boton de ancho variable.
 */
@Composable
fun CabeceraPortada(
    titulo: String,
    modifier: Modifier = Modifier,
    saludo: String? = null,
    accion: (@Composable () -> Unit)? = null,
) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current

    Column(modifier = modifier.fillMaxWidth()) {
        if (accion != null) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) { accion() }
        }
        if (saludo != null) {
            Text(
                text = saludo,
                style = MaterialTheme.typography.labelLarge,
                color = colores.textoSecundario,
            )
            Spacer(Modifier.height(espaciado.minimo))
        }
        Text(
            text = titulo,
            style = CifraSalud.portada,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() },
        )
    }
}

/**
 * Mosaico de acceso: icono arriba y etiqueta abajo, en formato cuadrado.
 *
 * El panel del paciente ya no lo usa -- sus accesos son un indice tipografico
 * --, pero sigue siendo la pieza del sistema para una rejilla de destinos.
 */
@Composable
fun MosaicoAcceso(
    etiqueta: String,
    glifo: GlifoSalud,
    descripcionAccesible: String,
    alPulsar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    val fuenteDeInteraccion = remember { MutableInteractionSource() }

    Surface(
        modifier = modifier
            .heightIn(min = ALTO_MOSAICO)
            .clickable(
                interactionSource = fuenteDeInteraccion,
                indication = null,
                onClick = alPulsar,
            )
            .superficiePulsable(fuenteDeInteraccion, FormaSalud.grande)
            .semantics(mergeDescendants = true) { contentDescription = descripcionAccesible },
        color = colores.fondoTarjeta,
        shape = FormaSalud.grande,
        border = bordeDeTarjeta(),
        shadowElevation = elevacionDeTarjeta(),
    ) {
        Column(
            modifier = Modifier.padding(espaciado.amplio),
            verticalArrangement = Arrangement.Top,
        ) {
            IconoSalud(
                glifo = glifo,
                lado = LADO_ICONO_MOSAICO,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(espaciado.medio))
            Text(
                text = etiqueta,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** Titulo de un bloque dentro de una pantalla, con el aire que le corresponde. */
@Composable
fun TituloDeBloque(texto: String, modifier: Modifier = Modifier) {
    Text(
        text = texto,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier.semantics { heading() },
    )
}

/** Trazo del anillo. Fijo, no proporcional: el peso no depende del tamano. */
private val GROSOR_ANILLO = 22.dp

/** Por debajo de medio grado el arco no se pinta (ver el remate redondo). */
private const val BARRIDO_MINIMO = 0.5f

/** Lo que se pinta al centro cuando no hay nada que medir. */
private const val SIN_CIFRA = "—"

private const val ALCANCE_MINIMO_LATIDO = 0.78f
private const val INTENSIDAD_MINIMA_LATIDO = 0.55f

/** A media distancia el halo conserva el 40% de su intensidad antes de apagarse. */
private const val CAIDA_HALO = 0.4f

private val ALTO_MOSAICO = 124.dp
private val LADO_ICONO_MOSAICO = 26.dp
private const val GRADOS_CIRCULO = 360f
private const val ANGULO_INICIAL = -90f
private val ALTO_FLOTANTE = 56.dp
private val LADO_GLIFO_FLOTANTE = 20.dp
