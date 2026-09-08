package com.eter.salud.ui.componentes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import salud.shared.generated.resources.a11y_login_isotipo
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import com.eter.salud.ui.theme.AreaTactilMinima
import com.eter.salud.ui.theme.LocalColoresSalud
import com.eter.salud.ui.theme.LocalEspaciadoSalud
import org.jetbrains.compose.resources.stringResource
import salud.shared.generated.resources.Res
import salud.shared.generated.resources.a11y_dato_critico
import salud.shared.generated.resources.a11y_eliminar_elemento
import salud.shared.generated.resources.a11y_progreso
import salud.shared.generated.resources.accion_cancelar
import salud.shared.generated.resources.accion_eliminar
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
        shape = RoundedCornerShape(espaciado.compacto),
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
        shape = RoundedCornerShape(espaciado.compacto + espaciado.minimo),
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

    Button(
        onClick = alPulsar,
        enabled = habilitado,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = AreaTactilMinima)
            .semantics { contentDescription = descripcionAccesible },
        shape = RoundedCornerShape(espaciado.compacto + espaciado.minimo),
        colors = ButtonDefaults.buttonColors(
            containerColor = colores.acentoAccion,
            contentColor = colores.sobreAcentoAccion,
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
    val espaciado = LocalEspaciadoSalud.current
    OutlinedButton(
        onClick = alPulsar,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = AreaTactilMinima)
            .semantics { contentDescription = descripcionAccesible },
        shape = RoundedCornerShape(espaciado.compacto + espaciado.minimo),
        border = BorderStroke(1.dp, LocalColoresSalud.current.separador),
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
