package com.eter.salud.data.demo

import com.eter.salud.domain.model.Alergia
import com.eter.salud.domain.model.AutorMensaje
import com.eter.salud.domain.model.Cita
import com.eter.salud.domain.model.DatosContactoCita
import com.eter.salud.domain.model.DatosPersonales
import com.eter.salud.domain.model.DispositivoRfid
import com.eter.salud.domain.model.EstadoCita
import com.eter.salud.domain.model.EstadoToma
import com.eter.salud.domain.model.MensajeChat
import com.eter.salud.domain.model.PacienteDto
import com.eter.salud.domain.model.PacienteVinculado
import com.eter.salud.domain.model.PerfilEmergenciaReducido
import com.eter.salud.domain.model.RiesgoPaciente
import com.eter.salud.domain.model.TomaDelDia
import com.eter.salud.domain.model.TratamientoActivo
import com.eter.salud.domain.time.CalendarioSalud
import com.eter.salud.domain.tratamiento.HorarioDeTratamiento

/**
 * Datos de demostracion: un consultorio pequeno pero completo.
 *
 * Existe para que la app se pueda juzgar SIN backend: seis pacientes con nombre,
 * tratamiento con horario, citas repartidas en las proximas dos semanas,
 * conversaciones con sus medicos y anotaciones en su diario. Antes todos los
 * repositorios en memoria devolvian lo mismo a cualquier paciente -- las mismas
 * dos pastillas, un expediente sin nombre --, y asi no hay forma de ver como se
 * comporta una bandeja ordenada por triage ni una agenda con citas reales.
 *
 * Todo vive aqui y no repartido por los repositorios para que las piezas
 * encajen: el paciente que la Dra. Ruiz ve en su bandeja es el mismo que inicia
 * sesion con `juan@salud.local`, con las mismas medicinas y las mismas citas.
 *
 * Las fechas se calculan desde [hoy]: una cita "pasado manana" tiene que seguir
 * siendo pasado manana cuando alguien abra la app la semana que viene.
 *
 * Lo siembra `SembradorDemo` en la base local la primera vez que se abre la
 * app. NUNCA se usa en produccion: cuando exista el backend, la siembra se
 * retira y estos datos dejan de escribirse.
 */
object CatalogoDemo {

    /** Contrasena comun de todas las cuentas de demostracion. */
    const val CONTRASENA = "salud12345"

    // ------------------------------------------------------------- Medicos

    const val DRA_RUIZ = "doc_889900A"
    const val DR_MENDOZA = "doc_local_2"
    const val DR_SALCEDO = "doc_local_6"
    const val DRA_NAVARRO = "doc_local_7"
    const val DR_GUTIERREZ = "doc_local_8"
    const val DRA_CASTILLO = "doc_local_9"
    const val DR_REYES = "doc_local_10"

    /**
     * Cuentas de personal medico ademas de la Dra. Ruiz: correo -> datos.
     * Todas verificadas, para poder entrar directo a su panel.
     */
    val cuentasMedicas: List<CuentaMedicaDemo> = listOf(
        CuentaMedicaDemo("dr.carlos.mendoza@hospital.com", DR_MENDOZA, "Carlos", "Mendoza Ibarra", "Dr.", "23456789"),
        CuentaMedicaDemo("dra.lucia.navarro@hospital.com", DRA_NAVARRO, "Lucia", "Navarro Pena", "Dra.", "34567890"),
    )

    // ------------------------------------------------------------ Pacientes

    /** Cuenta principal de paciente: la que el acceso sugiere para probar la app. */
    const val ID_PRINCIPAL = "pac_local_dev"
    const val CORREO_PRINCIPAL = "paciente@salud.local"

    /**
     * El paciente de la cuenta principal.
     *
     * Entra con el expediente completo y un tratamiento en curso: quien prueba
     * la app ve desde el primer toque tomas que marcar, citas en las proximas
     * dos semanas y medicos con quien hablar. Es la misma persona de la tarjeta
     * RFID de pruebas (`C38610A8`), asi que el escaner de emergencia del medico
     * muestra el mismo perfil.
     */
    val principal = PacienteDemo(
        id = ID_PRINCIPAL,
        correo = CORREO_PRINCIPAL,
        nombre = "Alfredo",
        apellidos = "Valadez Gonzalez",
        fechaNacimiento = "1998-05-15",
        genero = "Masculino",
        telefono = "+526181112233",
        riesgo = RiesgoPaciente.MEDIO,
        medicos = listOf(DRA_CASTILLO, DRA_RUIZ, DR_MENDOZA),
        tipoSangre = "O+",
        condiciones = listOf("Asma reactiva", "Rinitis alergica"),
        alergias = listOf(Alergia("Penicilina", "Alta (Anafilaxia)", "Cierre de vias respiratorias")),
        medicacionRescate = listOf("Salbutamol (Inhalador)"),
        idTarjetaRfid = "C38610A8",
        tomas = listOf(
            toma("av_1", "Budesonida/Formoterol inhalado", "160/4.5 mcg, 2 disparos", "08:00"),
            toma("av_2", "Loratadina", "10 mg, 1 tableta", "09:00"),
            toma("av_3", "Omeprazol", "20 mg, antes de la comida", "14:00"),
            toma("av_4", "Budesonida/Formoterol inhalado", "160/4.5 mcg, 2 disparos", "20:00"),
            toma("av_5", "Montelukast", "10 mg, 1 tableta", "21:00"),
        ),
    )

    val pacientes: List<PacienteDemo> = listOf(
        principal,
        PacienteDemo(
            id = "pac_01H8X9A",
            correo = "juan@salud.local",
            nombre = "Juan",
            apellidos = "Perez Gomez",
            fechaNacimiento = "1953-04-12",
            genero = "Masculino",
            telefono = "+526181234567",
            riesgo = RiesgoPaciente.ALTO,
            medicos = listOf(DRA_RUIZ, DR_MENDOZA),
            tipoSangre = "O+",
            condiciones = listOf("Hipertension arterial", "Fibrilacion auricular"),
            alergias = listOf(Alergia("Penicilina", "alta", "Urticaria y dificultad para respirar")),
            tomas = listOf(
                toma("jp_1", "Losartan", "50 mg, 1 tableta", "08:00"),
                toma("jp_2", "Apixaban", "5 mg, 1 tableta", "09:00"),
                toma("jp_3", "Aspirina protect", "100 mg, 1 tableta", "14:00"),
                toma("jp_4", "Losartan", "50 mg, 1 tableta", "20:00"),
                toma("jp_5", "Apixaban", "5 mg, 1 tableta", "21:00"),
            ),
        ),
        PacienteDemo(
            id = "pac_02J9Y8B",
            correo = "maria@salud.local",
            nombre = "Maria",
            apellidos = "Lopez Hernandez",
            fechaNacimiento = "1957-09-30",
            genero = "Femenino",
            telefono = "+526182345678",
            riesgo = RiesgoPaciente.MEDIO,
            medicos = listOf(DRA_RUIZ, DRA_NAVARRO),
            tipoSangre = "A+",
            condiciones = listOf("Diabetes tipo 2"),
            alergias = emptyList(),
            tomas = listOf(
                toma("ml_1", "Metformina", "850 mg, con el desayuno", "08:00"),
                toma("ml_2", "Metformina", "850 mg, con la comida", "14:00"),
                toma("ml_3", "Insulina glargina", "18 unidades, subcutanea", "22:00"),
            ),
        ),
        PacienteDemo(
            id = "pac_03K1Z7C",
            correo = "rosa@salud.local",
            nombre = "Rosa",
            apellidos = "Martinez Diaz",
            fechaNacimiento = "1946-01-18",
            genero = "Femenino",
            telefono = "+526183456789",
            riesgo = RiesgoPaciente.ALTO,
            medicos = listOf(DRA_RUIZ, DR_GUTIERREZ),
            tipoSangre = "B+",
            condiciones = listOf("Insuficiencia cardiaca", "Artrosis de rodilla"),
            alergias = listOf(Alergia("Ibuprofeno", "media", "Dolor de estomago")),
            tomas = listOf(
                toma("rm_1", "Furosemida", "40 mg, 1 tableta", "08:00"),
                toma("rm_2", "Carvedilol", "6.25 mg, 1 tableta", "08:30"),
                toma("rm_3", "Paracetamol", "500 mg, si hay dolor", "15:00"),
                toma("rm_4", "Carvedilol", "6.25 mg, 1 tableta", "20:30"),
                toma("rm_5", "Atorvastatina", "20 mg, 1 tableta", "22:00"),
            ),
        ),
        PacienteDemo(
            id = "pac_04L2A6D",
            correo = "jorge@salud.local",
            nombre = "Jorge",
            apellidos = "Ramirez Castro",
            fechaNacimiento = "1959-06-05",
            genero = "Masculino",
            telefono = "+526184567890",
            riesgo = RiesgoPaciente.MEDIO,
            medicos = listOf(DR_MENDOZA, DRA_CASTILLO),
            tipoSangre = "O-",
            condiciones = listOf("EPOC"),
            alergias = emptyList(),
            tomas = listOf(
                toma("jr_1", "Salbutamol inhalado", "2 disparos", "09:00"),
                toma("jr_2", "Tiotropio inhalado", "1 capsula", "10:00"),
                toma("jr_3", "Salbutamol inhalado", "2 disparos", "21:00"),
            ),
        ),
        PacienteDemo(
            id = "pac_05M3B5E",
            correo = "carmen@salud.local",
            nombre = "Carmen",
            apellidos = "Sanchez Ortiz",
            fechaNacimiento = "1950-11-22",
            genero = "Femenino",
            telefono = "+526185678901",
            riesgo = RiesgoPaciente.MEDIO,
            medicos = listOf(DR_SALCEDO, DR_GUTIERREZ),
            tipoSangre = "AB+",
            condiciones = listOf("Depresion mayor en tratamiento", "Hipotiroidismo"),
            alergias = emptyList(),
            tomas = listOf(
                toma("cs_1", "Levotiroxina", "75 mcg, en ayunas", "07:00"),
                toma("cs_2", "Sertralina", "50 mg, 1 tableta", "09:00"),
                toma("cs_3", "Clonazepam", "0.5 mg, 1/2 tableta", "22:30"),
            ),
        ),
        PacienteDemo(
            id = "pac_06N4C4F",
            correo = "alberto@salud.local",
            nombre = "Alberto",
            apellidos = "Flores Vega",
            fechaNacimiento = "1955-03-03",
            genero = "Masculino",
            telefono = "+526186789012",
            riesgo = RiesgoPaciente.ALTO,
            medicos = listOf(DRA_RUIZ, DR_REYES),
            tipoSangre = "A-",
            condiciones = listOf("Infarto agudo de miocardio (2025)", "Dislipidemia"),
            alergias = listOf(Alergia("Sulfas", "alta", "Erupcion en la piel")),
            tomas = listOf(
                toma("af_1", "Metoprolol", "50 mg, 1 tableta", "08:00"),
                toma("af_2", "Clopidogrel", "75 mg, 1 tableta", "09:00"),
                toma("af_3", "Rosuvastatina", "20 mg, 1 tableta", "21:00"),
                toma("af_4", "Metoprolol", "50 mg, 1 tableta", "20:00"),
            ),
        ),
    )

    /** Tomas del dia de cada paciente, ordenadas por hora. */
    val tomasPorPaciente: Map<String, List<TomaDelDia>>
        get() = pacientes.associate { it.id to it.tomas.sortedBy(TomaDelDia::horaProgramada) }

    /** Expediente de cada paciente, con nombre, tarjeta activa y tratamiento. */
    val expedientes: Map<String, PacienteDto>
        get() = pacientes.associate { p ->
            p.id to PacienteDto(
                idPaciente = p.id,
                estadoCuenta = "activo",
                dispositivosRfid = listOf(
                    DispositivoRfid(
                        idTarjetaRfid = p.idTarjetaRfid ?: "rfid_${p.id}",
                        estado = "activa",
                        fechaAsignacion = "2026-01-10T10:00:00Z",
                    ),
                ),
                datosPersonales = DatosPersonales(
                    nombre = p.nombre,
                    apellidos = p.apellidos,
                    fechaNacimiento = p.fechaNacimiento,
                    genero = p.genero,
                    telefono = p.telefono,
                ),
                perfilEmergenciaReducido = PerfilEmergenciaReducido(
                    tipoSangre = p.tipoSangre,
                    alergias = p.alergias,
                    condicionesCriticas = p.condiciones,
                    medicacionRescate = p.medicacionRescate,
                ),
                tratamientosActivos = p.tomas
                    .groupBy { it.medicamento }
                    .map { (medicamento, tomas) ->
                        TratamientoActivo(
                            idTratamiento = tomas.first().idTratamiento,
                            medicamento = medicamento,
                            dosis = tomas.first().dosis,
                            frecuenciaHoras = if (tomas.size > 1) HORAS_DIA / tomas.size else HORAS_DIA,
                            horariosSugeridos = tomas.map { it.horaProgramada },
                        )
                    },
            )
        }

    /** Con que medicos esta vinculado cada paciente. */
    val vinculaciones: Map<String, List<String>>
        get() = pacientes.associate { it.id to it.medicos }

    /** Cartera de cada medico, para su panel y su bandeja. */
    val carteras: Map<String, List<PacienteVinculado>>
        get() = pacientes
            .flatMap { p -> p.medicos.map { medico -> medico to p } }
            .groupBy({ it.first }, { (medico, p) ->
                PacienteVinculado(
                    idPaciente = p.id,
                    nombreCompleto = "${p.nombre} ${p.apellidos}",
                    riesgo = p.riesgo,
                    idConversacion = idConversacion(p.id, medico),
                )
            })

    // --------------------------------------------------------------- Citas

    /**
     * Citas de las proximas dos semanas (y un par ya pasadas), repartidas entre
     * pacientes y medicos. Los folios son fijos para que el comprobante del chat
     * y la agenda del medico digan lo mismo.
     *
     * @param lote distingue una tanda de otra. La siembra renueva las citas de
     * demostracion cuando las anteriores ya pasaron, y con el mismo
     * identificador la tanda nueva chocaria con la vieja en la base.
     */
    fun citas(hoy: String, nombresDeMedicos: Map<String, String>, lote: String = ""): List<Cita> {
        fun cita(
            id: String,
            paciente: String,
            medico: String,
            dias: Int,
            inicio: String,
            fin: String,
            estado: EstadoCita,
            motivo: String,
        ): Cita {
            val p = pacientes.first { it.id == paciente }
            val idCompleto = "$lote$id"
            return Cita(
                idCita = "cita_demo_$idCompleto",
                folio = "CITA-${FOLIO_BASE + idCompleto.hashCode().mod(FOLIO_RANGO)}",
                idMedico = medico,
                nombreMedico = nombresDeMedicos[medico].orEmpty(),
                idPaciente = paciente,
                fecha = diaHabil(CalendarioSalud.sumarDias(hoy, dias)),
                horaInicio = inicio,
                horaFin = fin,
                estado = estado,
                contacto = DatosContactoCita(
                    nombreCompleto = "${p.nombre} ${p.apellidos}",
                    telefono = p.telefono,
                    correo = p.correo,
                    motivo = motivo,
                ),
            )
        }
        return listOf(
            cita("av1", ID_PRINCIPAL, DRA_CASTILLO, 2, "10:00", "10:30", EstadoCita.CONFIRMADA, "Control de asma y espirometria"),
            cita("av2", ID_PRINCIPAL, DR_MENDOZA, 5, "17:00", "17:30", EstadoCita.PENDIENTE, "Revision general"),
            cita("av3", ID_PRINCIPAL, DRA_RUIZ, 9, "12:00", "12:30", EstadoCita.CONFIRMADA, "Electrocardiograma de rutina"),
            cita("av4", ID_PRINCIPAL, DRA_CASTILLO, 13, "09:30", "10:00", EstadoCita.PENDIENTE, "Resultados de laboratorio"),
            cita("av5", ID_PRINCIPAL, DR_MENDOZA, -3, "11:00", "11:30", EstadoCita.CONFIRMADA, "Vacuna de influenza"),
            cita("jp1", "pac_01H8X9A", DRA_RUIZ, 0, "12:00", "12:30", EstadoCita.CONFIRMADA, "Revision de presion arterial"),
            cita("jp2", "pac_01H8X9A", DR_MENDOZA, 6, "09:30", "10:00", EstadoCita.PENDIENTE, "Renovar receta"),
            cita("jp3", "pac_01H8X9A", DRA_RUIZ, -5, "10:00", "10:30", EstadoCita.CONFIRMADA, "Electrocardiograma"),
            cita("ml1", "pac_02J9Y8B", DRA_NAVARRO, 1, "11:00", "11:30", EstadoCita.CONFIRMADA, "Control de glucosa"),
            cita("ml2", "pac_02J9Y8B", DRA_RUIZ, 8, "16:00", "16:30", EstadoCita.PENDIENTE, "Revision general"),
            cita("rm1", "pac_03K1Z7C", DRA_RUIZ, 2, "10:00", "10:30", EstadoCita.CONFIRMADA, "Falta de aire al caminar"),
            cita("rm2", "pac_03K1Z7C", DR_GUTIERREZ, 4, "12:30", "13:00", EstadoCita.CONFIRMADA, "Valoracion geriatrica"),
            cita("jr1", "pac_04L2A6D", DRA_CASTILLO, 3, "09:00", "09:30", EstadoCita.CONFIRMADA, "Espirometria"),
            cita("jr2", "pac_04L2A6D", DR_MENDOZA, -2, "17:00", "17:30", EstadoCita.NO_ASISTIO, "Consulta general"),
            cita("cs1", "pac_05M3B5E", DR_SALCEDO, 1, "18:00", "18:45", EstadoCita.CONFIRMADA, "Seguimiento de tratamiento"),
            cita("cs2", "pac_05M3B5E", DR_GUTIERREZ, 10, "11:30", "12:00", EstadoCita.PENDIENTE, "Revision de tiroides"),
            cita("af1", "pac_06N4C4F", DRA_RUIZ, 1, "09:00", "09:30", EstadoCita.CONFIRMADA, "Control post infarto"),
            cita("af2", "pac_06N4C4F", DR_REYES, 5, "13:00", "13:30", EstadoCita.CONFIRMADA, "Mareos ocasionales"),
            cita("af3", "pac_06N4C4F", DRA_RUIZ, 12, "10:30", "11:00", EstadoCita.PENDIENTE, "Prueba de esfuerzo"),
        )
    }

    // ------------------------------------------------------- Conversaciones

    /**
     * Conversaciones iniciales por canal. Cada una termina distinto -- a veces
     * escribio el paciente, a veces el medico -- para que la bandeja muestre la
     * variedad real de "quien tiene la pelota".
     */
    fun conversaciones(hoy: String): Map<String, List<MensajeChat>> {
        val ayer = CalendarioSalud.restarDias(hoy, 1)
        fun m(id: String, autor: AutorMensaje, texto: String, fecha: String, hora: String) =
            MensajeChat(idMensaje = "msg_demo_$id", autor = autor, texto = texto, instante = "${fecha}T$hora:00Z")
        val p = AutorMensaje.PACIENTE
        val d = AutorMensaje.MEDICO
        return mapOf(
            idConversacion(ID_PRINCIPAL, DRA_CASTILLO) to listOf(
                m("av1", d, "Alfredo, como le ha ido con el inhalador de mantenimiento?", ayer, "17:00"),
                m("av2", p, "Mucho mejor, doctora. Ya casi no uso el salbutamol en la noche.", hoy, "08:40"),
            ),
            idConversacion("pac_01H8X9A", DRA_RUIZ) to listOf(
                m("jp1", d, "Buenos dias Juan. Recuerde tomarse la presion antes del desayuno.", ayer, "14:00"),
                m("jp2", p, "Doctora, hoy amaneci con 160/100 y un poco de dolor de cabeza.", hoy, "13:05"),
            ),
            idConversacion("pac_02J9Y8B", DRA_RUIZ) to listOf(
                m("ml1", p, "Me salio la glucosa en 145 despues de comer.", ayer, "20:10"),
                m("ml2", d, "Es aceptable. Siga con la metformina y camine 20 minutos al dia.", ayer, "21:00"),
            ),
            idConversacion("pac_03K1Z7C", DRA_RUIZ) to listOf(
                m("rm1", p, "Se me hinchan mucho los tobillos por la tarde.", hoy, "11:40"),
            ),
            idConversacion("pac_06N4C4F", DRA_RUIZ) to listOf(
                m("af1", d, "Alberto, como sigue despues del ajuste de metoprolol?", ayer, "16:00"),
                m("af2", p, "Anoche senti opresion en el pecho unos minutos y me desmaye un momento.", hoy, "07:20"),
            ),
            idConversacion("pac_04L2A6D", DR_MENDOZA) to listOf(
                m("jr1", p, "Perdi la cita del martes, se me complico el transporte.", ayer, "18:30"),
            ),
            idConversacion("pac_01H8X9A", DR_MENDOZA) to listOf(
                m("jpm1", d, "Juan, le deje lista la receta del losartan.", ayer, "10:00"),
            ),
            idConversacion("pac_05M3B5E", DR_SALCEDO) to listOf(
                m("cs1", p, "Estos dias tengo mucha ansiedad y casi no duermo.", hoy, "02:15"),
            ),
        )
    }

    // ---------------------------------------------------------------- Diario

    /**
     * Anotaciones del diario: (paciente, dias atras, hora UTC, texto). El
     * triage se calcula al guardarlas, igual que si el paciente las escribiera.
     */
    val anotacionesDiario: List<AnotacionDemo> = listOf(
        AnotacionDemo(ID_PRINCIPAL, 1, "22:00", "Un poco de tos en la noche, use el inhalador de rescate una vez."),
        AnotacionDemo(ID_PRINCIPAL, 4, "10:00", "Dormi bien, sin molestias."),
        AnotacionDemo("pac_06N4C4F", 0, "07:10", "Anoche senti opresion en el pecho y me desmaye unos segundos. Hoy tengo mareo."),
        AnotacionDemo("pac_01H8X9A", 0, "12:50", "Me duele la cabeza y tengo mareo desde la manana."),
        AnotacionDemo("pac_01H8X9A", 3, "09:00", "Dormi bien, sin molestias."),
        AnotacionDemo("pac_02J9Y8B", 1, "20:00", "Todo tranquilo, sin sintomas."),
        AnotacionDemo("pac_03K1Z7C", 0, "11:30", "Tobillos hinchados y cansancio al subir escaleras."),
        AnotacionDemo("pac_05M3B5E", 0, "02:10", "Mucha ansiedad, insomnio y ataques de panico por la noche."),
        AnotacionDemo("pac_04L2A6D", 1, "19:00", "Tos por la tarde, sin fiebre."),
    )

    fun idConversacion(idPaciente: String, idMedico: String): String = "conv_${idPaciente}_$idMedico"

    /**
     * Mueve una fecha de sabado o domingo al lunes siguiente. Las citas se fechan
     * relativas a hoy, y sin esto caian en fin de semana segun el dia en que se
     * abriera la app: una consulta en domingo no la cree nadie.
     */
    private fun diaHabil(fecha: String): String {
        val dia = CalendarioSalud.diaDeLaSemana(fecha) ?: return fecha
        return if (dia >= SABADO) CalendarioSalud.sumarDias(fecha, DIAS_SEMANA - dia) else fecha
    }

    private fun toma(id: String, medicamento: String, dosis: String, hora: String) = TomaDelDia(
        idToma = "toma_$id",
        idTratamiento = HorarioDeTratamiento.idPorDefecto(medicamento),
        medicamento = medicamento,
        dosis = dosis,
        horaProgramada = hora,
        estado = EstadoToma.PENDIENTE,
    )

    private const val HORAS_DIA = 24
    private const val DIAS_SEMANA = 7

    /** `CalendarioSalud.diaDeLaSemana`: lunes = 0, sabado = 5. */
    private const val SABADO = 5
    private const val FOLIO_BASE = 5000
    private const val FOLIO_RANGO = 4000
}

/** Un paciente de demostracion, con todo lo que las pantallas necesitan de el. */
data class PacienteDemo(
    val id: String,
    val correo: String,
    val nombre: String,
    val apellidos: String,
    val fechaNacimiento: String,
    val genero: String,
    val telefono: String,
    val riesgo: RiesgoPaciente,
    val medicos: List<String>,
    val tipoSangre: String,
    val condiciones: List<String>,
    val alergias: List<Alergia>,
    val tomas: List<TomaDelDia>,
    val medicacionRescate: List<String> = emptyList(),
    /** Tarjeta fisica asignada; sin ella se inventa una por paciente. */
    val idTarjetaRfid: String? = null,
)

data class CuentaMedicaDemo(
    val correo: String,
    val idMedico: String,
    val nombre: String,
    val apellidos: String,
    val tratamiento: String,
    val cedula: String,
)

data class AnotacionDemo(
    val idPaciente: String,
    val diasAtras: Int,
    val horaUtc: String,
    val texto: String,
)
