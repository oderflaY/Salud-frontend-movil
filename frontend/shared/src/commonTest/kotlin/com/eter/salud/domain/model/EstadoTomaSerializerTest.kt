package com.eter.salud.domain.model

import com.eter.salud.data.red.JsonRed
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `EstadoToma` viaja al backend en minusculas (`"tomado"`), no como el nombre
 * del enum de Kotlin (`"TOMADO"`): son los valores que persiste el DM. Esta
 * prueba fija ese contrato para que un cambio futuro en el serializador no lo
 * rompa en silencio.
 */
class EstadoTomaSerializerTest {

    @Test
    fun serializa_en_minusculas_segun_valorApi() {
        assertEquals("\"tomado\"", JsonRed.encodeToString(EstadoToma.serializer(), EstadoToma.TOMADO))
        assertEquals("\"tomado_tarde\"", JsonRed.encodeToString(EstadoToma.serializer(), EstadoToma.TOMADO_TARDE))
    }

    @Test
    fun deserializa_desde_minusculas() {
        assertEquals(EstadoToma.OMITIDO, JsonRed.decodeFromString(EstadoToma.serializer(), "\"omitido\""))
    }

    @Test
    fun un_valor_desconocido_cae_a_pendiente() {
        assertEquals(EstadoToma.PENDIENTE, JsonRed.decodeFromString(EstadoToma.serializer(), "\"desconocido\""))
    }
}
