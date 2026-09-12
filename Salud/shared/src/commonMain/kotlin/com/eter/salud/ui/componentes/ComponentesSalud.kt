package com.eter.salud.ui.componentes

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.eter.salud.ui.theme.AlturaAccion
import com.eter.salud.ui.theme.AreaTactilMinima
import com.eter.salud.ui.theme.FormaSalud
import com.eter.salud.ui.theme.LocalColoresSalud
import com.eter.salud.ui.theme.LocalEspaciadoSalud
import com.eter.salud.ui.theme.MovimientoSalud
import org.jetbrains.compose.resources.stringResource
import salud.shared.generated.resources.Res
import salud.shared.generated.resources.a11y_accion_reintentar
import salud.shared.generated.resources.a11y_boton_atras
import salud.shared.generated.resources.a11y_dato_critico
import salud.shared.generated.resources.a11y_eliminar_elemento
import salud.shared.generated.resources.a11y_login_isotipo
import salud.shared.generated.resources.a11y_progreso
import salud.shared.generated.resources.accion_cancelar
import salud.shared.generated.resources.accion_eliminar
import salud.shared.generated.resources.accion_reintentar
import salud.shared.generated.resources.accion_seleccionar
import salud.shared.generated.resources.etiqueta_dato_critico
import salud.shared.generated.resources.etiqueta_lista_vacia

/**
 * Componentes base del sistema de diseno.
 *
 * Reglas del DM aplicadas en todos ellos:
 *  - Sin emojis ni iconografia decorativa.
 *  - Color siempre desde el tema, nunca literal.
 *  - Texto siempre desde `strings.xml`.
 *  - Cada elemento interactivo declara su etiqueta para TalkBack / VoiceOver.
 */

/** Encabezado de una pregunta: titulo grande, apoyo breve y mucho aire. */
@Composable
fun EncabezadoPregunta(
    titulo: String,
    descripcion: String,
    numeroPregunta: Int?,
    totalPreguntas: Int,
    esDatoCritico: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    Column(modifier = modifier.fillMaxWidth()) {
        if (numeroPregunta != null) {
            Text(
                text = stringResource(Res.string.a11y_progreso, numeroPregunta, totalPreguntas),
                style = MaterialTheme.typography.labelMedium,
                color = colores.textoSecundario,
            )
            Spacer(Modifier.height(espaciado.compacto))
        }
        Text(
            text = titulo,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(espaciado.compacto))
        Text(
            text = descripcion,
            style = MaterialTheme.typography.bodyMedium,
            color = colores.textoSecundario,
        )
        if (esDatoCritico) {
            Spacer(Modifier.height(espaciado.medio))
            EtiquetaDatoCritico()
        }
    }
}

/** Marca visual y sonora de que el dato viaja en la tarjeta de emergencia. */
@Composable
fun EtiquetaDatoCritico(modifier: Modifier = Modifier) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    val descripcion = stringResource(Res.string.a11y_dato_critico)
    Surface(
        modifier = modifier.semantics { contentDescription = descripcion },
        color = colores.fondoCritico,
        shape = FormaSalud.sutil,
    ) {
        Text(
            text = stringResource(Res.string.etiqueta_dato_critico),
            style = MaterialTheme.typography.labelMedium,
            color = colores.acentoCritico,
            modifier = Modifier.padding(
                horizontal = espaciado.medio,
                vertical = espaciado.compacto,
            ),
        )
    }
}

/** Campo de texto con etiqueta, error y descripcion accesible obligatorias. */
@Composable
fun CampoTextoSalud(
    valor: String,
    alCambiar: (String) -> Unit,
    etiqueta: String,
    descripcionAccesible: String,
    modifier: Modifier = Modifier,
    marcador: String? = null,
    error: String? = null,
    tipoTeclado: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = valor,
        onValueChange = alCambiar,
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = descripcionAccesible },
        label = { Text(etiqueta) },
        placeholder = marcador?.let { { Text(it) } },
        supportingText = error?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
        isError = error != null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = tipoTeclado),
    )
}

/**
 * Selector de opcion unica presentado como action sheet nativo
 * (DM_Arquitectura_App.md, seccion 5: componentes y controles nativos).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectorSalud(
    etiqueta: String,
    seleccion: String,
    opciones: List<String>,
    alSeleccionar: (String) -> Unit,
    descripcionAccesible: String,
    modifier: Modifier = Modifier,
    error: String? = null,
) {
    val espaciado = LocalEspaciadoSalud.current
    var hojaVisible by remember { mutableStateOf(false) }
    val estadoHoja = rememberModalBottomSheetState()

    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = seleccion,
            onValueChange = {},
            readOnly = true,
            enabled = false,
            label = { Text(etiqueta) },
            supportingText = error?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
            isError = error != null,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = AreaTactilMinima)
                .clickable { hojaVisible = true }
                .semantics { contentDescription = descripcionAccesible },
        )
    }

    if (hojaVisible) {
        ModalBottomSheet(
            onDismissRequest = { hojaVisible = false },
            sheetState = estadoHoja,
        ) {
            Column(Modifier.padding(bottom = espaciado.generoso)) {
                Text(
                    text = etiqueta,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .padding(horizontal = espaciado.amplio, vertical = espaciado.medio)
                        .semantics { heading() },
                )
                opciones.forEach { opcion ->
                    val descripcionOpcion = stringResource(Res.string.accion_seleccionar) +
                        ": " + opcion
                    Text(
                        text = opcion,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = AreaTactilMinima)
                            .clickable {
                                alSeleccionar(opcion)
                                hojaVisible = false
                            }
                            .padding(horizontal = espaciado.amplio, vertical = espaciado.medio)
                            .semantics { contentDescription = descripcionOpcion },
                    )
                }
                HorizontalDivider()
                TextButton(
                    onClick = { hojaVisible = false },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = AreaTactilMinima)
                        .padding(horizontal = espaciado.medio, vertical = espaciado.compacto),
                ) {
                    Text(stringResource(Res.string.accion_cancelar))
                }
            }
        }
    }
}

/** Casilla para declarar explicitamente la ausencia de un dato clinico. */
@Composable
fun CasillaDeclaracion(
    marcada: Boolean,
    alCambiar: (Boolean) -> Unit,
    etiqueta: String,
    modifier: Modifier = Modifier,
) {
    val espaciado = LocalEspaciadoSalud.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = AreaTactilMinima)
            .clickable { alCambiar(!marcada) }
            .semantics(mergeDescendants = true) { contentDescription = etiqueta },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(espaciado.compacto),
    ) {
        Checkbox(checked = marcada, onCheckedChange = null)
        Text(text = etiqueta, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Lista de elementos capturados, cada uno con su accion de borrado accesible. */
@Composable
fun ListaCapturada(
    titulo: String,
    elementos: List<String>,
    descripcionesAccesibles: List<String>,
    alEliminar: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = titulo,
            style = MaterialTheme.typography.titleSmall,
            color = colores.textoSecundario,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(espaciado.compacto))
        if (elementos.isEmpty()) {
            Text(
                text = stringResource(Res.string.etiqueta_lista_vacia),
                style = MaterialTheme.typography.bodyMedium,
                color = colores.textoSecundario,
            )
            return@Column
        }
        elementos.forEachIndexed { indice, elemento ->
            val descripcion = descripcionesAccesibles.getOrElse(indice) { elemento }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = AreaTactilMinima),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = elemento,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .weight(1f)
                        .clearAndSetSemantics { contentDescription = descripcion },
                )
                TextButton(onClick = { alEliminar(indice) }) {
                    Text(
                        text = stringResource(Res.string.accion_eliminar),
                        modifier = Modifier.clearAndSetSemantics {
                            contentDescription = elemento
                        },
                    )
                }
            }
            HorizontalDivider(color = colores.separador)
        }
    }
}

/** Descripcion accesible del boton de borrado, resuelta con el nombre del item. */
@Composable
fun descripcionEliminar(elemento: String): String =
    stringResource(Res.string.a11y_eliminar_elemento, elemento)

/**
 * Campo de captura con contenedor relleno y sin linea inferior: el borde
 * agresivo se sustituye por el gris contenedor del tema, que separa el campo
 * del fondo sin ruido visual.
 *
 * [accion] ocupa la ranura final del campo (por ejemplo, mostrar la contrasena).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampoTextoRellenoSalud(
    valor: String,
    alCambiar: (String) -> Unit,
    etiqueta: String,
    descripcionAccesible: String,
    modifier: Modifier = Modifier,
    marcador: String? = null,
    error: String? = null,
    tipoTeclado: KeyboardType = KeyboardType.Text,
    ocultarTexto: Boolean = false,
    accion: (@Composable () -> Unit)? = null,
) {
    val colores = LocalColoresSalud.current
    val espaciado = LocalEspaciadoSalud.current
    TextField(
        value = valor,
        onValueChange = alCambiar,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = AreaTactilMinima)
            .semantics { contentDescription = descripcionAccesible },
        label = { Text(etiqueta) },
        placeholder = marcador?.let { { Text(it) } },
        supportingText = error?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
        isError = error != null,
        singleLine = true,
        trailingIcon = accion,
        visualTransformation = if (ocultarTexto) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.None
        },
        keyboardOptions = KeyboardOptions(keyboardType = tipoTeclado),
        shape = FormaSalud.sutil,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = colores.fondoCampo,
            unfocusedContainerColor = colores.fondoCampo,
            errorContainerColor = colores.fondoCampo,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            errorIndicatorColor = MaterialTheme.colorScheme.error,
        ),
    )
}

/**
 * Accion principal de una pantalla: ancho completo, esquinas suaves y el
 * Turquesa de Salud como relleno. Es el unico elemento dominante de la vista.
 */
@Composable
fun BotonAccionPrincipal(
    etiqueta: String,
    alPulsar: () -> Unit,
    descripcionAccesible: String,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true,
    cargando: Boolean = false,
    etiquetaCargando: String? = null,
    descripcionCargando: String? = null,
) {
    val colores = LocalColoresSalud.current
    val espaciado = LocalEspaciadoSalud.current

    if (cargando) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = AreaTactilMinima)
                .semantics {
                    if (descripcionCargando != null) contentDescription = descripcionCargando
                    liveRegion = LiveRegionMode.Polite
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(espaciado.medio),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.height(espaciado.amplio),
                color = colores.acentoAccion,
            )
            if (etiquetaCargando != null) {
                Text(text = etiquetaCargando, style = MaterialTheme.typography.bodyMedium)
            }
        }
        return
    }

    val fuenteDeInteraccion = remember { MutableInteractionSource() }
    Button(
        onClick = alPulsar,
        enabled = habilitado,
        interactionSource = fuenteDeInteraccion,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = AlturaAccion)
            .hundirAlPulsar(fuenteDeInteraccion)
            .semantics { contentDescription = descripcionAccesible },
        shape = FormaSalud.media,
        colors = ButtonDefaults.buttonColors(
            containerColor = colores.acentoAccion,
            contentColor = colores.sobreAcentoAccion,
            // El deshabilitado no se apaga con transparencia sobre el fondo:
            // sobre el negro pizarra del Modo Oscuro eso lo volvia invisible.
            // Se apaga contra una superficie declarada, que existe en ambos modos.
            disabledContainerColor = colores.fondoCampo,
            disabledContentColor = colores.textoSecundario,
        ),
    ) {
        Text(text = etiqueta, style = MaterialTheme.typography.titleMedium)
    }
}

/** Accion secundaria: solo borde, para que nunca compita con la principal. */
@Composable
fun BotonSecundarioSalud(
    etiqueta: String,
    alPulsar: () -> Unit,
    descripcionAccesible: String,
    modifier: Modifier = Modifier,
) {
    val fuenteDeInteraccion = remember { MutableInteractionSource() }
    OutlinedButton(
        onClick = alPulsar,
        interactionSource = fuenteDeInteraccion,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = AlturaAccion)
            .hundirAlPulsar(fuenteDeInteraccion)
            .semantics { contentDescription = descripcionAccesible },
        shape = FormaSalud.media,
        border = BorderStroke(GROSOR_HAIRLINE, LocalColoresSalud.current.separador),
    ) {
        Text(text = etiqueta, style = MaterialTheme.typography.titleMedium)
    }
}

/**
 * Separador tenue con una palabra al centro. Se marca como decorativo para que
 * el lector de pantalla no lo lea como si fuera contenido.
 */
@Composable
fun DivisorConTexto(texto: String, modifier: Modifier = Modifier) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics { },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(espaciado.medio),
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = colores.separador)
        Text(
            text = texto,
            style = MaterialTheme.typography.labelMedium,
            color = colores.textoSecundario,
        )
        HorizontalDivider(modifier = Modifier.weight(1f), color = colores.separador)
    }
}

/**
 * Isotipo vectorial de la app: cruz medica sobre un cuadrado redondeado.
 *
 * Se dibuja con primitivas en lugar de importar un paquete de iconos para que
 * herede el color del tema y escale sin perdida en ambos modos.
 */
@Composable
fun IsotipoSalud(modifier: Modifier = Modifier, lado: Dp = 72.dp) {
    val fondo = MaterialTheme.colorScheme.primary
    val trazo = MaterialTheme.colorScheme.onPrimary
    val descripcion = stringResource(Res.string.a11y_login_isotipo)
    Canvas(
        modifier = modifier
            .size(lado)
            .semantics { contentDescription = descripcion },
    ) {
        val esquina = CornerRadius(size.minDimension * PROPORCION_ESQUINA_ISOTIPO)
        drawRoundRect(color = fondo, cornerRadius = esquina)

        val grosor = size.minDimension * PROPORCION_GROSOR_CRUZ
        val largo = size.minDimension * PROPORCION_LARGO_CRUZ
        val centro = Offset(size.width / 2f, size.height / 2f)
        drawRect(
            color = trazo,
            topLeft = Offset(centro.x - grosor / 2f, centro.y - largo / 2f),
            size = Size(grosor, largo),
        )
        drawRect(
            color = trazo,
            topLeft = Offset(centro.x - largo / 2f, centro.y - grosor / 2f),
            size = Size(largo, grosor),
        )
    }
}

private const val PROPORCION_ESQUINA_ISOTIPO = 0.28f
private const val PROPORCION_GROSOR_CRUZ = 0.16f
private const val PROPORCION_LARGO_CRUZ = 0.52f

/**
 * Hunde ligeramente la pieza mientras el dedo esta encima.
 *
 * Es el unico movimiento que el sistema aplica a TODAS las acciones, y no es
 * adorno: en una pantalla tactil no hay cursor ni estado "hover", asi que sin
 * esta respuesta el usuario no sabe si el toque entro hasta que la pantalla
 * cambia. Un 3% de escala basta para sentirlo y no se ve como una animacion.
 */
@Composable
internal fun Modifier.hundirAlPulsar(fuenteDeInteraccion: InteractionSource): Modifier {
    val pulsado by fuenteDeInteraccion.collectIsPressedAsState()
    val escala by animateFloatAsState(
        targetValue = if (pulsado) ESCALA_PULSADO else 1f,
        animationSpec = tween(MovimientoSalud.INMEDIATO),
        label = "escalaPulsacion",
    )
    return graphicsLayer {
        scaleX = escala
        scaleY = escala
    }
}

/**
 * Como se despega una tarjeta del fondo: con un borde suave de 1dp.
 *
 * La tarjeta y el fondo quedan a 1.13:1 en claro y a 1.23:1 en oscuro OLED, y
 * el borde a 1.38:1 y 1.45:1 de la tarjeta.
 * Para una persona mayor el borde no es decoracion: la sensibilidad al contraste
 * cae con la edad, y dos superficies casi blancas sin limite dibujado se funden
 * en una sola. El borde dice "esto es un bloque que se puede tocar" sin el peso
 * de una sombra.
 *
 * Devuelve un tipo nulable porque asi se decide aqui, para toda la app, si las
 * tarjetas llevan limite o no.
 */
@Composable
fun bordeDeTarjeta(): BorderStroke? =
    BorderStroke(GROSOR_HAIRLINE, LocalColoresSalud.current.separador)

/**
 * Sin sombra, en ningun modo.
 *
 * Se conserva la funcion en lugar de borrar la llamada en catorce pantallas
 * porque el valor sigue siendo una decision del sistema, no una constante: si
 * algun dia una superficie flotante necesita elevacion, se decide aqui.
 */
@Composable
fun elevacionDeTarjeta(): Dp = 0.dp

/**
 * Bloque agrupado del sistema: superficie propia despegada del fondo.
 *
 * En Modo Claro es blanco puro sobre el fondo perla, que es justo la inversion
 * del esquema anterior y lo que hace que el contenido flote sin gritar.
 */
@Composable
fun TarjetaSalud(
    modifier: Modifier = Modifier,
    color: Color = LocalColoresSalud.current.fondoTarjeta,
    contenido: @Composable ColumnScope.() -> Unit,
) {
    val espaciado = LocalEspaciadoSalud.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = color,
        shape = FormaSalud.grande,
        border = bordeDeTarjeta(),
        shadowElevation = elevacionDeTarjeta(),
    ) {
        Column(
            modifier = Modifier.padding(espaciado.amplio),
            verticalArrangement = Arrangement.spacedBy(espaciado.compacto),
            content = contenido,
        )
    }
}

private const val ESCALA_PULSADO = 0.97f
internal val GROSOR_HAIRLINE = 1.dp

/**
 * Flecha de retroceso de la cabecera.
 *
 * Sustituye al boton de TEXTO "Atras" que llevaba cada pantalla. El cambio no es
 * cosmetico: una palabra en la esquina superior izquierda compite tipograficamente
 * con el titulo que tiene al lado, se traduce a longitudes distintas en cada
 * idioma y desplaza el titulo de sitio segun el idioma. Una flecha ocupa siempre
 * lo mismo, se reconoce sin leer y deja el titulo donde debe estar.
 *
 * La palabra no se pierde: sigue siendo la etiqueta que anuncian TalkBack y
 * VoiceOver, que es donde de verdad hacia falta.
 */
@Composable
fun BotonAtras(alPulsar: () -> Unit, modifier: Modifier = Modifier) {
    val descripcion = stringResource(Res.string.a11y_boton_atras)
    val fuenteDeInteraccion = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(AreaTactilMinima)
            .clip(FormaSalud.pastilla)
            .clickable(interactionSource = fuenteDeInteraccion, indication = null, onClick = alPulsar)
            .hundirAlPulsar(fuenteDeInteraccion)
            .semantics { contentDescription = descripcion },
        contentAlignment = Alignment.Center,
    ) {
        IconoSalud(
            glifo = GlifoSalud.ATRAS,
            lado = LADO_FLECHA_ATRAS,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

private val LADO_FLECHA_ATRAS = 22.dp

/**
 * Estado de error de una pantalla, con salida.
 *
 * Sustituye a los mensajes de error sueltos que habia en siete pantallas. Todos
 * decian que algo habia fallado y NINGUNO ofrecia que hacer al respecto: la
 * unica manera de reintentar era abandonar la pantalla y volver a entrar, y en
 * las secciones con la carga atada al ciclo de vida del ViewModel ni siquiera
 * eso funcionaba -- el ViewModel seguia vivo y no volvia a intentarlo.
 *
 * [alReintentar] es opcional porque no todo error se puede reintentar desde la
 * Vista; cuando no se pasa, el bloque se comporta como el aviso de antes.
 *
 * Se anuncia como region viva ASERTIVA y no cortes: un fallo de carga interrumpe
 * lo que el lector de pantalla estuviera diciendo, porque cambia por completo lo
 * que el usuario puede hacer a continuacion.
 */
@Composable
fun BloqueDeError(
    mensaje: String,
    modifier: Modifier = Modifier,
    alReintentar: (() -> Unit)? = null,
) {
    val espaciado = LocalEspaciadoSalud.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = espaciado.amplio)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Assertive },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(espaciado.medio),
    ) {
        Text(
            text = mensaje,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        if (alReintentar != null) {
            BotonSecundarioSalud(
                etiqueta = stringResource(Res.string.accion_reintentar),
                alPulsar = alReintentar,
                descripcionAccesible = stringResource(Res.string.a11y_accion_reintentar),
                modifier = Modifier.widthIn(max = ANCHO_MAXIMO_REINTENTO),
            )
        }
    }
}

private val ANCHO_MAXIMO_REINTENTO = 240.dp

/**
 * Superficie pulsable del sistema: hundimiento al tocar Y anillo de foco.
 *
 * ## Por que existe
 *
 * El proyecto quito la onda de Material (`indication = null`) en doce sitios
 * para que la retroalimentacion la diera el color, y no puso nada en su lugar.
 * La onda no era solo decoracion: era tambien el UNICO indicador de foco. Sin
 * ella, quien navega con teclado, con un mando de television o con un
 * conmutador de accesibilidad no ve donde esta parado -- recorre la pantalla a
 * ciegas.
 *
 * Este modificador conserva la decision original (sin onda) y devuelve lo que
 * se habia perdido: un anillo de foco que aparece INSTANTANEAMENTE, sin
 * transicion. Un anillo que se desvanece hacia dentro llega tarde para quien lo
 * necesita, que es justo el punto.
 *
 * @param fuenteDeInteraccion la misma que se pasa al `clickable`, para que el
 * hundimiento y el foco lean el mismo estado.
 */
@Composable
fun Modifier.superficiePulsable(
    fuenteDeInteraccion: MutableInteractionSource,
    forma: Shape = FormaSalud.media,
): Modifier {
    val enfocado by fuenteDeInteraccion.collectIsFocusedAsState()
    val colorAnillo = MaterialTheme.colorScheme.primary
    return this
        .hundirAlPulsar(fuenteDeInteraccion)
        // Sin `animateColorAsState`: el anillo de foco NO se anima. Aparecer
        // gradualmente es exactamente lo que un indicador de foco no debe hacer.
        .border(
            width = if (enfocado) GROSOR_ANILLO_FOCO else 0.dp,
            color = if (enfocado) colorAnillo else Color.Transparent,
            shape = forma,
        )
}

private val GROSOR_ANILLO_FOCO = 2.dp
