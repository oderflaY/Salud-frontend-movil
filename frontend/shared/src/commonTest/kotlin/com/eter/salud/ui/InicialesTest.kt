package com.eter.salud.ui

import com.eter.salud.ui.componentes.inicialesDe
import kotlin.test.Test
import kotlin.test.assertEquals

class InicialesTest {

    @Test
    fun se_saltan_los_tratamientos() {
        assertEquals("CS", inicialesDe("Dr. Carlos Silva Rodriguez"))
        assertEquals("ML", inicialesDe("Dra. Maria Lopez"))
    }

    @Test
    fun un_nombre_sin_apellido_o_raro_no_deja_el_avatar_vacio() {
        assertEquals("J", inicialesDe("Juan"))
        assertEquals("D", inicialesDe("Dr."))
    }
}
