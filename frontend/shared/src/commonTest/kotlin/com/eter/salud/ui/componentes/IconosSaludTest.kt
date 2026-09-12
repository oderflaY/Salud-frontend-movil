package com.eter.salud.ui.componentes

import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.addPathNodes
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Los trazos de Material Symbols se incrustan como texto SVG y se parsean al
 * primer uso. Un trazo mal copiado no falla al compilar: falla al abrir la
 * primera pantalla que lo pinta, en el telefono del paciente. Esta prueba lo
 * adelanta a la maquina del desarrollador.
 */
class IconosSaludTest {

    @Test
    fun todos_los_glifos_se_parsean_a_un_trazo_que_empieza_moviendo_la_pluma() {
        GlifoSalud.entries.forEach { glifo ->
            val nodos = addPathNodes(glifo.trazo)
            assertTrue(nodos.size > 1, "${glifo.name}: trazo vacio")
            assertTrue(
                nodos.first() is PathNode.MoveTo || nodos.first() is PathNode.RelativeMoveTo,
                "${glifo.name}: el trazo no empieza con un movimiento",
            )
        }
    }
}
