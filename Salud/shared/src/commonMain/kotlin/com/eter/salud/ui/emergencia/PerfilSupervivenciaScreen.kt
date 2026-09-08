package com.eter.salud.ui.emergencia

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.eter.salud.domain.model.Alergia
import com.eter.salud.domain.model.IdentidadSupervivencia
import com.eter.salud.domain.model.PerfilSupervivencia
import com.eter.salud.ui.componentes.BarraAccionInferior
import com.eter.salud.ui.componentes.BotonAccionPrincipal
import com.eter.salud.ui.componentes.BotonSecundarioSalud
import com.eter.salud.ui.theme.LocalColoresSalud
import com.eter.salud.ui.theme.LocalEspaciadoSalud
import org.jetbrains.compose.resources.stringResource
import salud.shared.generated.resources.Res
import salud.shared.generated.resources.a11y_triage_alergia
import salud.shared.generated.resources.a11y_triage_cerrar
import salud.shared.generated.resources.a11y_triage_condicion
import salud.shared.generated.resources.a11y_triage_donador
import salud.shared.generated.resources.a11y_triage_escanear_otra
import salud.shared.generated.resources.a11y_triage_medicacion
import salud.shared.generated.resources.a11y_triage_paciente
import salud.shared.generated.resources.a11y_triage_tipo_sangre
import salud.shared.generated.resources.a11y_triage_tipo_sangre_desconocido
import salud.shared.generated.resources.triage_accion_cerrar
import salud.shared.generated.resources.triage_accion_escanear_otra
import salud.shared.generated.resources.triage_alergia_detalle
import salud.shared.generated.resources.triage_alergias_sin_datos
import salud.shared.generated.resources.triage_alergias_titulo
import salud.shared.generated.resources.triage_aviso_datos_reducidos
import salud.shared.generated.resources.triage_condiciones_sin_datos
import salud.shared.generated.resources.triage_condiciones_titulo
import salud.shared.generated.resources.triage_donador_no
import salud.shared.generated.resources.triage_donador_si
import salud.shared.generated.resources.triage_edad
import salud.shared.generated.resources.triage_medicacion_sin_datos
import salud.shared.generated.resources.triage_medicacion_titulo
import salud.shared.generated.resources.triage_tarjeta
import salud.shared.generated.resources.triage_tipo_sangre_desconocido

/**
 * Perfil de supervivencia: la pantalla de triage que ve el paramedico tras leer
 * la tarjeta.
 *
 * Jerarquia visual extrema a proposito: el nombre es el elemento tipografico
 * mas grande de la pantalla porque identifica al paciente, y la insignia de
 * tipo de sangre es el elemento visual mas agresivo porque es el primer dato
 * que un paramedico necesita antes de una transfusion (DM_Arquitectura_App.md,
 * el resto de la pantalla es jerarquia por tipografia, no por color: solo la
 * sangre y las alergias reclaman color).
 *
 * Solo trae el bloque de emergencia del expediente: es exactamente lo que la
 * API entrega sin token de medico tratante, y la pantalla lo recuerda con el
 * aviso al pie.
 */
@Composable
fun PerfilSupervivenciaScreen(
    perfil: PerfilSupervivencia,
    edad: Int?,
    modifier: Modifier = Modifier,
    alCerrar: () -> Unit = {},
    alEscanearOtra: () -> Unit = {},
) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    val emergencia = perfil.perfilEmergenciaReducido

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = espaciado.amplio),
            verticalArrangement = Arrangement.spacedBy(espaciado.amplio),
        ) {
            Spacer(Modifier.height(espaciado.amplio))
            Cabecera(
                identidad = perfil.datosPersonales,
                edad = edad,
                tipoSangre = emergencia.tipoSangre,
            )

            emergencia.donadorOrganos?.let { esDonador ->
                InformacionDonador(esDonador)
            }

            TarjetaTriage(
                titulo = stringResource(Res.string.triage_alergias_titulo),
                colorAcento = colores.acentoCritico,
            ) {
                if (emergencia.alergias.isEmpty()) {
                    TextoSinDatos(stringResource(Res.string.triage_alergias_sin_datos))
                } else {
                    emergencia.alergias.forEachIndexed { indice, alergia ->
                        FilaAlergia(alergia)
                        if (indice != emergencia.alergias.lastIndex) {
                            HorizontalDivider(color = colores.separador)
                        }
                    }
                }
            }

            TarjetaTriage(titulo = stringResource(Res.string.triage_condiciones_titulo)) {
                if (emergencia.condicionesCriticas.isEmpty()) {
                    TextoSinDatos(stringResource(Res.string.triage_condiciones_sin_datos))
                } else {
                    emergencia.condicionesCriticas.forEach { condicion ->
                        val descripcion =
                            stringResource(Res.string.a11y_triage_condicion, condicion)
                        Text(
                            text = condicion,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.semantics { contentDescription = descripcion },
                        )
                    }
                }
            }

            TarjetaTriage(titulo = stringResource(Res.string.triage_medicacion_titulo)) {
                if (emergencia.medicacionRescate.isEmpty()) {
                    TextoSinDatos(stringResource(Res.string.triage_medicacion_sin_datos))
                } else {
                    emergencia.medicacionRescate.forEach { medicamento ->
                        val descripcion =
                            stringResource(Res.string.a11y_triage_medicacion, medicamento)
                        Text(
                            text = medicamento,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.semantics { contentDescription = descripcion },
                        )
                    }
                }
            }

            Text(
                text = stringResource(Res.string.triage_tarjeta, perfil.idTarjetaRfid),
                style = MaterialTheme.typography.labelMedium,
                color = colores.textoSecundario,
            )
            Text(
                text = stringResource(Res.string.triage_aviso_datos_reducidos),
                style = MaterialTheme.typography.labelMedium,
                color = colores.textoSecundario,
            )
            Spacer(Modifier.height(espaciado.medio))
        }

        BarraAccionInferior {
            BotonAccionPrincipal(
                etiqueta = stringResource(Res.string.triage_accion_escanear_otra),
                alPulsar = alEscanearOtra,
                descripcionAccesible = stringResource(Res.string.a11y_triage_escanear_otra),
            )
            BotonSecundarioSalud(
                etiqueta = stringResource(Res.string.triage_accion_cerrar),
                alPulsar = alCerrar,
                descripcionAccesible = stringResource(Res.string.a11y_triage_cerrar),
            )
        }
    }
}

/** Nombre a maxima jerarquia, edad, e insignia de tipo de sangre junto a el. */
@Composable
private fun Cabecera(
    identidad: IdentidadSupervivencia,
    edad: Int?,
    tipoSangre: String?,
) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    val descripcionPaciente = if (edad != null) {
        stringResource(Res.string.a11y_triage_paciente, identidad.nombreCompleto, edad)
    } else {
        identidad.nombreCompleto
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(espaciado.medio),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .semantics(mergeDescendants = true) { contentDescription = descripcionPaciente },
        ) {
            Text(
                text = identidad.nombreCompleto,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.semantics { heading() },
            )
            if (edad != null) {
                Spacer(Modifier.height(espaciado.minimo))
                Text(
                    text = stringResource(Res.string.triage_edad, edad),
                    style = MaterialTheme.typography.titleMedium,
                    color = colores.textoSecundario,
                )
            }
        }
        InsigniaTipoSangre(tipoSangre)
    }
}

/**
 * El elemento visual mas agresivo de la pantalla: circulo solido en el color de
 * error del tema, con su texto emparejado (`onError`), que es exactamente el
 * par semantico pensado para garantizar contraste sobre ese fondo.
 */
@Composable
private fun InsigniaTipoSangre(tipoSangre: String?) {
    val tieneDato = !tipoSangre.isNullOrBlank()
    val descripcion = if (tieneDato) {
        stringResource(Res.string.a11y_triage_tipo_sangre, tipoSangre)
    } else {
        stringResource(Res.string.a11y_triage_tipo_sangre_desconocido)
    }

    Box(
        modifier = Modifier
            .size(TAMANO_INSIGNIA)
            .background(color = MaterialTheme.colorScheme.error, shape = CircleShape)
            .semantics(mergeDescendants = true) { contentDescription = descripcion },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = tipoSangre?.takeIf { it.isNotBlank() }
                ?: stringResource(Res.string.triage_tipo_sangre_desconocido),
            style = if (tieneDato) {
                MaterialTheme.typography.headlineMedium
            } else {
                MaterialTheme.typography.labelMedium
            },
            color = MaterialTheme.colorScheme.onError,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun InformacionDonador(esDonador: Boolean) {
    val colores = LocalColoresSalud.current
    val texto = if (esDonador) {
        stringResource(Res.string.triage_donador_si)
    } else {
        stringResource(Res.string.triage_donador_no)
    }
    val descripcion = stringResource(Res.string.a11y_triage_donador, texto)
    Text(
        text = texto,
        style = MaterialTheme.typography.bodyLarge,
        color = if (esDonador) colores.exito else colores.textoSecundario,
        modifier = Modifier.semantics { contentDescription = descripcion },
    )
}

/**
 * Tarjeta base de triage: borde muy suave y sombra casi invisible. Si se le da
 * un [colorAcento], una franja a la izquierda marca la categoria como critica
 * (hoy, solo las alergias la usan).
 */
@Composable
private fun TarjetaTriage(
    titulo: String,
    modifier: Modifier = Modifier,
    colorAcento: Color? = null,
    contenido: @Composable ColumnScope.() -> Unit,
) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = colores.fondoTarjeta,
        shape = RoundedCornerShape(espaciado.medio),
        shadowElevation = 1.dp,
    ) {
        Row(Modifier.fillMaxWidth()) {
            if (colorAcento != null) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .width(FRANJA_ACENTO)
                        .background(colorAcento),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(espaciado.amplio),
                verticalArrangement = Arrangement.spacedBy(espaciado.medio),
            ) {
                Text(
                    text = titulo,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.semantics { heading() },
                )
                contenido()
            }
        }
    }
}

@Composable
private fun FilaAlergia(alergia: Alergia) {
    val colores = LocalColoresSalud.current
    val descripcion = stringResource(
        Res.string.a11y_triage_alergia,
        alergia.alergeno,
        alergia.severidad,
        alergia.reaccion,
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = descripcion },
    ) {
        Text(
            text = alergia.alergeno,
            style = MaterialTheme.typography.titleMedium,
            color = colores.acentoCritico,
        )
        Text(
            text = stringResource(
                Res.string.triage_alergia_detalle,
                alergia.severidad,
                alergia.reaccion,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = colores.textoSecundario,
        )
    }
}

@Composable
private fun TextoSinDatos(texto: String) {
    Text(
        text = texto,
        style = MaterialTheme.typography.bodyMedium,
        color = LocalColoresSalud.current.textoSecundario,
    )
}

private val TAMANO_INSIGNIA = 96.dp
private val FRANJA_ACENTO = 4.dp
