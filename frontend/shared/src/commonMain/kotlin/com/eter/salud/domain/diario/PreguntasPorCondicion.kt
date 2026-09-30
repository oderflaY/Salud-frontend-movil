package com.eter.salud.domain.diario

/**
 * Una pregunta guiada del diario.
 *
 * @property pregunta lo que se le muestra al paciente ("¿Te tomaste la presion
 * hoy?"). Va en su idioma de todos los dias, no en terminos clinicos.
 * @property inicioDeRespuesta el texto que se escribe en el diario al tocarla.
 * Queda a medias A PROPOSITO ("Mi presion hoy fue de ") para que el paciente
 * complete el dato con sus palabras: la respuesta tiene que ser suya, no una
 * casilla que alguien marco por el.
 */
data class PreguntaGuiada(
    val clave: String,
    val pregunta: String,
    val inicioDeRespuesta: String,
)

/**
 * Las preguntas que el diario le hace a CADA paciente segun lo que tiene.
 *
 * ## Por que preguntar y no dejar la hoja en blanco
 *
 * Un campo vacio con "¿como te sientes?" produce "bien" un dia tras otro. Lo
 * que el medico necesita de un hipertenso no es lo mismo que de un asmatico:
 * al primero le sirve la cifra de presion y si hubo dolor de cabeza; al
 * segundo, cuantas veces uso el inhalador de rescate. Preguntar lo que
 * corresponde a su condicion es lo que convierte el diario en algo util.
 *
 * ## Como se decide
 *
 * Por las condiciones del expediente ([com.eter.salud.domain.model.PerfilEmergenciaReducido.condicionesCriticas]),
 * cotejadas por palabra clave y sin acentos: el expediente puede decir
 * "Hipertension arterial", "HTA" o "hipertensión", y las tres son lo mismo.
 *
 * Un paciente con varias condiciones recibe las preguntas de todas, sin
 * repetir, y siempre cierra con las generales: nadie deberia quedarse sin
 * poder contar algo que no encaja en ninguna casilla.
 *
 * ## Que NO es
 *
 * No es un cuestionario clinico validado ni un diagnostico. Son recordatorios
 * de que contar; lo que el paciente escribe sigue pasando por el mismo triage
 * ([TriageDelDiario]) y lo lee su medico.
 */
object PreguntasPorCondicion {

    /**
     * Preguntas para [condiciones], sin repetir y con las generales al final.
     * Una lista vacia (paciente sin condiciones registradas) devuelve solo las
     * generales.
     */
    fun para(condiciones: List<String>): List<PreguntaGuiada> {
        val normalizadas = condiciones.map(::normalizar)
        val propias = GUIAS
            .filter { guia -> normalizadas.any { condicion -> guia.palabras.any(condicion::contains) } }
            .flatMap { it.preguntas }
        return (propias + GENERALES).distinctBy { it.clave }
    }

    /** Las condiciones que esta version sabe acompanar, para mostrarlas en pantalla. */
    fun condicionesReconocidas(condiciones: List<String>): List<String> {
        val normalizadas = condiciones.map { it to normalizar(it) }
        return normalizadas
            .filter { (_, norma) -> GUIAS.any { guia -> guia.palabras.any(norma::contains) } }
            .map { it.first }
    }

    private class Guia(val palabras: List<String>, val preguntas: List<PreguntaGuiada>)

    private val GUIAS = listOf(
        // ------------------------------------------------------- Hipertension
        Guia(
            palabras = listOf("hipertension", "hipertenso", "presion alta", "hta"),
            preguntas = listOf(
                PreguntaGuiada(
                    "presion_cifra",
                    "¿Te tomaste la presion hoy? ¿Cuanto salio?",
                    "Mi presion hoy fue de ",
                ),
                PreguntaGuiada(
                    "presion_sintomas",
                    "¿Dolor de cabeza, zumbido en los oidos o vision borrosa?",
                    "Hoy senti ",
                ),
                PreguntaGuiada(
                    "presion_hinchazon",
                    "¿Notaste hinchazon en pies o tobillos?",
                    "Sobre la hinchazon de pies: ",
                ),
            ),
        ),
        // ---------------------------------------------------------- Diabetes
        Guia(
            palabras = listOf("diabetes", "diabetico", "glucosa alta"),
            preguntas = listOf(
                PreguntaGuiada(
                    "glucosa_cifra",
                    "¿Cuanto salio tu glucosa y a que hora la mediste?",
                    "Mi glucosa salio en ",
                ),
                PreguntaGuiada(
                    "glucosa_sintomas",
                    "¿Mucha sed, orinaste mas de lo normal o te dio debilidad?",
                    "Hoy senti ",
                ),
                PreguntaGuiada(
                    "glucosa_pies",
                    "¿Revisaste tus pies? ¿Alguna herida o ampolla?",
                    "Al revisar mis pies vi ",
                ),
            ),
        ),
        // ---------------------------------------------- Asma / EPOC (respirar)
        Guia(
            palabras = listOf("asma", "epoc", "bronquitis", "respiratori"),
            preguntas = listOf(
                PreguntaGuiada(
                    "aire_falta",
                    "¿Te falto el aire hoy? ¿Haciendo que?",
                    "Me falto el aire cuando ",
                ),
                PreguntaGuiada(
                    "aire_rescate",
                    "¿Usaste tu inhalador de rescate? ¿Cuantas veces?",
                    "Use el inhalador de rescate ",
                ),
                PreguntaGuiada(
                    "aire_tos",
                    "¿Tos o flema? ¿De que color?",
                    "Sobre la tos: ",
                ),
            ),
        ),
        // --------------------------------------------- Insuficiencia cardiaca
        Guia(
            palabras = listOf("insuficiencia cardiaca", "falla cardiaca"),
            preguntas = listOf(
                PreguntaGuiada(
                    "corazon_peso",
                    "¿Te pesaste hoy? ¿Subiste mas de un kilo desde ayer?",
                    "Hoy peso ",
                ),
                PreguntaGuiada(
                    "corazon_acostado",
                    "¿Te falta el aire al acostarte o necesitas mas almohadas?",
                    "Al acostarme ",
                ),
                PreguntaGuiada(
                    "corazon_piernas",
                    "¿Se te hincharon las piernas o los tobillos?",
                    "Sobre la hinchazon: ",
                ),
            ),
        ),
        // ---------------------------------------------- Fibrilacion auricular
        Guia(
            palabras = listOf("fibrilacion", "arritmia", "taquicardia"),
            preguntas = listOf(
                PreguntaGuiada(
                    "ritmo_palpitaciones",
                    "¿Sentiste el corazon acelerado o irregular?",
                    "Senti palpitaciones ",
                ),
                PreguntaGuiada(
                    "ritmo_mareo",
                    "¿Mareo, desmayo o te falto el aire de repente?",
                    "Hoy senti ",
                ),
            ),
        ),
        // ------------------------------------------------------- Animo/mental
        Guia(
            palabras = listOf("depresion", "ansiedad", "panico", "animo"),
            preguntas = listOf(
                PreguntaGuiada(
                    "animo_dia",
                    "¿Como estuvo tu animo hoy?",
                    "Hoy mi animo estuvo ",
                ),
                PreguntaGuiada(
                    "animo_sueno",
                    "¿Como dormiste anoche?",
                    "Anoche dormi ",
                ),
                PreguntaGuiada(
                    "animo_ganas",
                    "¿Pudiste hacer lo que tenias planeado?",
                    "Sobre lo que hice hoy: ",
                ),
            ),
        ),
        // ------------------------------------------------------- Tiroides
        Guia(
            palabras = listOf("tiroid", "hipotiroid", "hipertiroid"),
            preguntas = listOf(
                PreguntaGuiada(
                    "tiroides_energia",
                    "¿Cansancio fuera de lo normal, frio o caida de cabello?",
                    "Hoy senti ",
                ),
                PreguntaGuiada(
                    "tiroides_ayunas",
                    "¿Tomaste tu pastilla en ayunas, sin desayunar antes?",
                    "Mi pastilla de tiroides la tome ",
                ),
            ),
        ),
        // ------------------------------------------------- Artrosis / dolor
        Guia(
            palabras = listOf("artrosis", "artritis", "lumbalgia", "dolor cronico"),
            preguntas = listOf(
                PreguntaGuiada(
                    "dolor_escala",
                    "Del 1 al 10, ¿cuanto te dolio hoy?",
                    "Hoy mi dolor fue de ",
                ),
                PreguntaGuiada(
                    "dolor_movimiento",
                    "¿Pudiste caminar y moverte como siempre?",
                    "Para moverme hoy ",
                ),
            ),
        ),
        // ------------------------------------------------------------ Alergias
        Guia(
            palabras = listOf("rinitis", "alergic", "alergia"),
            preguntas = listOf(
                PreguntaGuiada(
                    "alergia_sintomas",
                    "¿Estornudos, ojos llorosos o nariz tapada?",
                    "Hoy tuve ",
                ),
            ),
        ),
    )

    /**
     * Para todos, siempre al final: la medicacion y el hueco libre.
     *
     * La adherencia se pregunta aqui y no por condicion porque el motivo por el
     * que alguien deja una pastilla (efecto, costo, olvido) es el mismo dato
     * util tenga lo que tenga.
     */
    private val GENERALES = listOf(
        PreguntaGuiada(
            "medicacion",
            "¿Tomaste todas tus medicinas hoy?",
            "Sobre mis medicinas de hoy: ",
        ),
        PreguntaGuiada(
            "otra_cosa",
            "¿Algo mas que quieras contarle a tu medico?",
            "",
        ),
    )

    /** Minusculas y sin acentos, para que "Hipertensión" y "hipertension" sean lo mismo. */
    private fun normalizar(texto: String): String =
        texto.lowercase().map { ACENTOS[it] ?: it }.joinToString("")

    private val ACENTOS = mapOf(
        'á' to 'a', 'é' to 'e', 'í' to 'i', 'ó' to 'o', 'ú' to 'u', 'ü' to 'u', 'ñ' to 'n',
    )
}
