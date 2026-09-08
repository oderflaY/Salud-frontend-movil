package com.eter.salud.domain.mapper

import com.eter.salud.domain.model.Alergia
import com.eter.salud.domain.model.Cirugia
import com.eter.salud.domain.model.ContactoEmergencia
import com.eter.salud.domain.model.DatosPersonales
import com.eter.salud.domain.model.HistorialClinico
import com.eter.salud.domain.model.Identificaciones
import com.eter.salud.domain.model.InventarioTratamiento
import com.eter.salud.domain.model.MetricasVitalesActuales
import com.eter.salud.domain.model.PacienteDto
import com.eter.salud.domain.model.PerfilEmergenciaReducido
import com.eter.salud.domain.model.TratamientoActivo
import com.eter.salud.presentation.onboarding.RelojFijo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Hidratacion inversa: del DTO que devuelve la API al borrador editable de la
 * Fase 2. Sin esta traduccion la pantalla de ajustes arrancaria en blanco y
 * sobrescribiria datos que el paciente ya habia capturado.
 */
class PacienteDtoAMapperTest {

    @Test
    fun el_dto_completo_se_convierte_en_un_borrador_editable_equivalente() {
        val dto = PacienteDto(
            idPaciente = "pac_01H8X9A",
            identificaciones = Identificaciones(
                curp = "PAGJ850412HDFRXX09",
                nss = "12345678901",
                aseguradora = "IMSS",
            ),
            datosPersonales = DatosPersonales(
                nombre = "Juan",
                apellidos = "Perez Gomez",
                fechaNacimiento = "1985-04-12",
                genero = "Masculino",
                telefono = "+526181234567",
            ),
            contactosEmergencia = listOf(
                ContactoEmergencia("Maria Gomez", "Madre", "+526189876543", 1),
            ),
            perfilEmergenciaReducido = PerfilEmergenciaReducido(
                tipoSangre = "O+",
                donadorOrganos = true,
                alergias = listOf(Alergia("Penicilina", "Alta (Anafilaxia)", "Cierre de vias")),
                condicionesCriticas = listOf("Hipertension arterial"),
                medicacionRescate = listOf("Anticoagulantes activos"),
            ),
            metricasVitalesActuales = MetricasVitalesActuales(
                pesoKg = 78.5,
                alturaCm = 175,
                imc = 25.6,
                ultimaPresionArterial = "120/80",
                fechaTomaMetricas = "2026-09-01T08:00:00Z",
            ),
            historialClinico = HistorialClinico(
                cirugias = listOf(Cirugia("Apendicectomia", "2015-08-20", "Sin complicaciones")),
                antecedentesHeredofamiliares = listOf("Diabetes tipo 2"),
            ),
            tratamientosActivos = listOf(
                TratamientoActivo(
                    medicamento = "Losartan",
                    dosis = "50mg",
                    frecuenciaHoras = 12,
                    viaAdministracion = "Oral",
                    inventario = InventarioTratamiento(cantidadRestante = 14),
                ),
            ),
        )

        val borrador = dto.aBorradorPaciente()

        assertEquals("Juan", borrador.nombre)
        assertEquals("PAGJ850412HDFRXX09", borrador.curp)
        assertEquals("IMSS", borrador.aseguradora)
        assertEquals(true, borrador.donadorOrganos)
        assertEquals("78.5", borrador.pesoKg)
        assertEquals("175", borrador.alturaCm)
        assertEquals("120", borrador.presionSistolica)
        assertEquals("80", borrador.presionDiastolica)
        assertEquals(1, borrador.cirugias.size)
        assertEquals(listOf("Diabetes tipo 2"), borrador.antecedentesHeredofamiliares)
        assertEquals("12", borrador.tratamientos.single().frecuenciaHoras)
        assertEquals("14", borrador.tratamientos.single().cantidadRestante)
    }

    @Test
    fun una_lista_vacia_del_backend_se_lee_como_declaracion_explicita_de_ausencia() {
        val dto = PacienteDto(
            perfilEmergenciaReducido = PerfilEmergenciaReducido(tipoSangre = "O+"),
            historialClinico = HistorialClinico(),
        )

        val borrador = dto.aBorradorPaciente()

        assertTrue(borrador.sinAlergiasConocidas)
        assertTrue(borrador.sinCondicionesCriticas)
        assertTrue(borrador.sinMedicacionRescate)
        assertTrue(borrador.sinCirugias)
        assertTrue(borrador.sinAntecedentes)
    }

    @Test
    fun un_nodo_ausente_no_se_confunde_con_una_declaracion_de_ausencia() {
        val borrador = PacienteDto().aBorradorPaciente()

        assertFalse(borrador.sinAlergiasConocidas)
        assertFalse(borrador.sinCirugias)
        assertFalse(borrador.sinAntecedentes)
        assertFalse(borrador.sinTratamientos)
    }

    @Test
    fun la_ida_y_vuelta_conserva_los_datos_del_historial() {
        val original = PacienteDto(
            idPaciente = "pac_01H8X9A",
            identificaciones = Identificaciones(curp = "PAGJ850412HDFRXX09"),
            perfilEmergenciaReducido = PerfilEmergenciaReducido(
                tipoSangre = "O+",
                donadorOrganos = true,
            ),
            historialClinico = HistorialClinico(
                cirugias = listOf(Cirugia("Apendicectomia", "2015-08-20", null)),
            ),
        )

        val reconstruido = original.aBorradorPaciente()
            .aPacienteDto(RelojFijo(), idPaciente = original.idPaciente)

        assertEquals(original.identificaciones?.curp, reconstruido.identificaciones?.curp)
        assertEquals(
            original.perfilEmergenciaReducido?.donadorOrganos,
            reconstruido.perfilEmergenciaReducido?.donadorOrganos,
        )
        assertEquals(original.historialClinico?.cirugias, reconstruido.historialClinico?.cirugias)
        assertEquals("pac_01H8X9A", reconstruido.idPaciente)
    }
}
