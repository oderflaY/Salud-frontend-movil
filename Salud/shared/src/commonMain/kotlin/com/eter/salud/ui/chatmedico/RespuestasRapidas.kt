package com.eter.salud.ui.chatmedico

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.eter.salud.ui.theme.AreaTactilMinima
import com.eter.salud.ui.theme.LocalColoresSalud
import com.eter.salud.ui.theme.LocalEspaciadoSalud
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import salud.shared.generated.resources.Res
import salud.shared.generated.resources.a11y_chatmedico_respuesta_rapida
import salud.shared.generated.resources.a11y_chatmedico_respuestas_rapidas
import salud.shared.generated.resources.opciones_respuestas_rapidas

/**
 * Fila de respuestas rapidas de triage, encima del campo de texto.
 *
 * Al elegir una NO se envia sola: se escribe en el campo para que el medico la
 * revise o la complete antes de mandarla. Un mensaje clinico que sale sin que
 * su autor lo relea es justo lo que no queremos.
 *
 * El texto de cada respuesta sale de `strings.xml` (array `opciones_respuestas_rapidas`),
 * nunca del codigo.
 */
@Composable
fun RespuestasRapidasFila(
    alElegir: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val espaciado = LocalEspaciadoSalud.current
    val respuestas = stringArrayResource(Res.array.opciones_respuestas_rapidas)
    val descripcionGrupo = stringResource(Res.string.a11y_chatmedico_respuestas_rapidas)

    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = descripcionGrupo },
        contentPadding = PaddingValues(horizontal = espaciado.amplio),
        horizontalArrangement = Arrangement.spacedBy(espaciado.compacto),
    ) {
        items(respuestas) { respuesta ->
            ChipRespuestaRapida(texto = respuesta, alPulsar = { alElegir(respuesta) })
        }
    }
}

@Composable
private fun ChipRespuestaRapida(texto: String, alPulsar: () -> Unit) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    val descripcion = stringResource(Res.string.a11y_chatmedico_respuesta_rapida, texto)

    Text(
        text = texto,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier
            .heightIn(min = AreaTactilMinima)
            .background(colores.fondoCampo, RoundedCornerShape(percent = 50))
            .clickable(onClick = alPulsar)
            .semantics { contentDescription = descripcion }
            .padding(horizontal = espaciado.medio, vertical = espaciado.compacto + 2.dp),
    )
}
