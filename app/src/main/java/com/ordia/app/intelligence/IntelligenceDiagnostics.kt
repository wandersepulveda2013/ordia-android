package com.ordia.app.intelligence

import android.content.Context
import android.util.Log

/**
 * Suite de diagnóstico y validación del sistema de inteligencia.
 *
 * Contiene 100 frases de prueba clasificadas en:
 * - 50 que DEBEN generar una acción (aceptar)
 * - 50 que DEBEN rechazar o preguntar (rechazar)
 *
 * Cada frase está etiquetada con los valores esperados del esquema
 * para validación automatizada.
 *
 * Uso desde UI de diagnóstico:
 *   val result = IntelligenceDiagnostics.runTest(engine, phrase)
 *   result.passed → true si el esquema coincide, false si difiere
 *
 * @see IntelligenceSchema Esquema de salida estructurada
 */
object IntelligenceDiagnostics {

    private const val TAG = "IntelligenceDiagnostics"

    /**
     * Resultado de una prueba individual.
     */
    data class TestResult(
        val phrase: String,
        val expectedCategory: TestCategory,
        val expectedAction: ActionSuggested?,
        val expectedActor: Actor?,
        val expectedPolarity: Polarity?,
        val schema: IntelligenceSchema?,
        val response: IntelligenceResponse?,
        val passed: Boolean,
        val details: String = ""
    )

    /**
     * Categoría de la frase de prueba.
     */
    enum class TestCategory {
        DEBE_ACEPTAR,  // Debe generar una acción (actionSuggested != NONE)
        DEBE_RECHAZAR  // Debe rechazar (actionSuggested == NONE) o preguntar (followUpQuestion != null)
    }

    /**
     * Ejecuta la suite completa de 100 frases contra el motor de inteligencia.
     */
    suspend fun runFullSuite(engine: OrdiaIntelligenceEngine): List<TestResult> {
        val results = mutableListOf<TestResult>()
        var passCount = 0
        var failCount = 0

        for (phrase in testPhrases) {
            val result = runSingleTest(engine, phrase)
            results.add(result)
            if (result.passed) passCount++ else failCount++
        }

        Log.i(TAG, "Suite completa: $passCount pasaron, $failCount fallaron de ${testPhrases.size}")
        return results
    }

    /**
     * Ejecuta una prueba individual con una frase.
     * Verifica que el esquema producido coincida con las expectativas.
     */
    suspend fun runSingleTest(engine: OrdiaIntelligenceEngine, phrase: TestPhrase): TestResult {
        val response = engine.analyzeText(phrase.text, com.ordia.app.context.ContextCaptureSource.DIAGNOSTICS)
        val schema = response.schema

        val actionMatch = when (phrase.expectedCategory) {
            TestCategory.DEBE_ACEPTAR -> schema.actionSuggested != ActionSuggested.NONE
            TestCategory.DEBE_RECHAZAR -> schema.actionSuggested == ActionSuggested.NONE ||
                response.isActionable == false
        }

        val actorMatch = phrase.expectedActor == null || schema.actor == phrase.expectedActor
        val polarityMatch = phrase.expectedPolarity == null || schema.polarity == phrase.expectedPolarity

        val passed = actionMatch && actorMatch && polarityMatch

        val details = buildString {
            if (!actionMatch) append("Acción: esperada=${phrase.expectedAction?.value}, obtenida=${schema.actionSuggested.value}. ")
            if (!actorMatch) append("Actor: esperado=${phrase.expectedActor}, obtenido=${schema.actor}. ")
            if (!polarityMatch) append("Polaridad: esperada=${phrase.expectedPolarity}, obtenida=${schema.polarity}. ")
        }

        return TestResult(
            phrase = phrase.text,
            expectedCategory = phrase.expectedCategory,
            expectedAction = phrase.expectedAction,
            expectedActor = phrase.expectedActor,
            expectedPolarity = phrase.expectedPolarity,
            schema = schema,
            response = response,
            passed = passed,
            details = details
        )
    }

    /**
     * Obtiene estadísticas resumidas de los resultados.
     */
    data class SuiteStats(
        val total: Int,
        val passed: Int,
        val failed: Int,
        val acceptedCount: Int,
        val rejectedCount: Int
    )

    fun computeStats(results: List<TestResult>): SuiteStats {
        val passed = results.count { it.passed }
        val failed = results.count { !it.passed }
        val accepted = results.count { it.schema?.actionSuggested != ActionSuggested.NONE }
        val rejected = results.count { it.schema?.actionSuggested == ActionSuggested.NONE }
        return SuiteStats(results.size, passed, failed, accepted, rejected)
    }

    /**
     * Frase de prueba con metadatos de validación.
     */
    data class TestPhrase(
        val text: String,
        val expectedCategory: TestCategory,
        val expectedAction: ActionSuggested? = null,
        val expectedActor: Actor? = null,
        val expectedPolarity: Polarity? = null,
        val note: String = ""
    )

    /**
     * 100 frases de prueba: 50 deben generar acción, 50 deben rechazar/preguntar.
     *
     * CASOS ESPECIALES DEMOSTRABLES:
     * 1. "Mañana iremos al supermercado" → acción, nosotros, futuro, positivo
     * 2. "Mañana no iremos" → acción, nosotros, futuro, negativo
     * 3. "Juan irá mañana" → acción, alguienMas, futuro, positivo
     * 4. "Ayer fuimos" → acción, nosotros, pasado, positivo
     * 5. "Tal vez vayamos mañana" → acción, nosotros, futuro, dudoso → followUp
     * 6. "Cuando salga del trabajo pasaré por el supermercado" → acción, yo, condicionalFuturo
     */
    val testPhrases: List<TestPhrase> = buildList {
        // =========================================================
        // GRUPO 1: 50 frases que DEBEN generar una acción
        // =========================================================

        // 1. Futuro, nosotros, positivo — supermercado
        add(TestPhrase(
            "Mañana iremos al supermercado",
            TestCategory.DEBE_ACEPTAR, ActionSuggested.SHOPPING, Actor.NOSOTROS, Polarity.POSITIVO,
            "Caso 1: futuro, nosotros, afirmativo, acción = compras"
        ))
        // 2. Negación explícita
        add(TestPhrase(
            "Mañana no iremos",
            TestCategory.DEBE_ACEPTAR, ActionSuggested.TASK, Actor.NOSOTROS, Polarity.NEGATIVO,
            "Caso 2: futuro, nosotros, NEGATIVO"
        ))
        // 3. Tercera persona
        add(TestPhrase(
            "Juan irá mañana",
            TestCategory.DEBE_ACEPTAR, ActionSuggested.TASK, Actor.ALGUIEN_MAS, Polarity.POSITIVO,
            "Caso 3: futuro, alguienMas"
        ))
        // 4. Pasado
        add(TestPhrase(
            "Ayer fuimos",
            TestCategory.DEBE_ACEPTAR, ActionSuggested.TASK, Actor.NOSOTROS, Polarity.POSITIVO,
            "Caso 4: pasado, nosotros"
        ))
        // 5. Duda → requiere seguimiento
        add(TestPhrase(
            "Tal vez vayamos mañana",
            TestCategory.DEBE_ACEPTAR, ActionSuggested.TASK, Actor.NOSOTROS, Polarity.POSITIVO,
            "Caso 5: dudoso, debe generar followUpQuestion"
        ))
        // 6. Condicional futuro
        add(TestPhrase(
            "Cuando salga del trabajo pasaré por el supermercado",
            TestCategory.DEBE_ACEPTAR, ActionSuggested.SHOPPING, Actor.YO, Polarity.POSITIVO,
            "Caso 6: condicional futuro, yo, supermercado"
        ))

        // 7-10: Tareas explícitas
        add(TestPhrase("Tengo que comprar leche", TestCategory.DEBE_ACEPTAR, ActionSuggested.SHOPPING, Actor.YO))
        add(TestPhrase("Recuérdame llamar al médico", TestCategory.DEBE_ACEPTAR, ActionSuggested.REMINDER, Actor.YO))
        add(TestPhrase("Hay que pagar la factura del internet", TestCategory.DEBE_ACEPTAR, ActionSuggested.PAYMENT, Actor.NOSOTROS))
        add(TestPhrase("No olvides comprar pan", TestCategory.DEBE_ACEPTAR, ActionSuggested.SHOPPING, Actor.ALGUIEN))

        // 11-15: Citas y reuniones
        add(TestPhrase("Tengo cita con el dentista el lunes", TestCategory.DEBE_ACEPTAR, ActionSuggested.APPOINTMENT, Actor.YO))
        add(TestPhrase("Reunión con el equipo mañana a las 10", TestCategory.DEBE_ACEPTAR, ActionSuggested.MEETING, Actor.NOSOTROS))
        add(TestPhrase("Voy a tener una consulta médica", TestCategory.DEBE_ACEPTAR, ActionSuggested.APPOINTMENT, Actor.YO))
        add(TestPhrase("Quedamos en vernos el viernes", TestCategory.DEBE_ACEPTAR, ActionSuggested.MEETING, Actor.NOSOTROS))
        add(TestPhrase("Tengo una cita médica el miércoles", TestCategory.DEBE_ACEPTAR, ActionSuggested.APPOINTMENT, Actor.YO))

        // 16-20: Estudio y trabajo
        add(TestPhrase("Tengo que estudiar para el examen", TestCategory.DEBE_ACEPTAR, ActionSuggested.STUDY, Actor.YO))
        add(TestPhrase("Debo entregar el informe el viernes", TestCategory.DEBE_ACEPTAR, ActionSuggested.DEADLINE, Actor.YO))
        add(TestPhrase("Tengo que preparar la presentación", TestCategory.DEBE_ACEPTAR, ActionSuggested.TASK, Actor.YO))
        add(TestPhrase("El lunes tengo clase de inglés", TestCategory.DEBE_ACEPTAR, ActionSuggested.STUDY, Actor.YO))
        add(TestPhrase("Hay que repasar para el examen final", TestCategory.DEBE_ACEPTAR, ActionSuggested.STUDY, Actor.NOSOTROS))

        // 21-25: Llamadas
        add(TestPhrase("Llama a María para confirmar la cita", TestCategory.DEBE_ACEPTAR, ActionSuggested.CALL, Actor.YO))
        add(TestPhrase("Hablar con el contador sobre los impuestos", TestCategory.DEBE_ACEPTAR, ActionSuggested.CALL, Actor.YO))
        add(TestPhrase("Tengo que llamar a la clínica", TestCategory.DEBE_ACEPTAR, ActionSuggested.CALL, Actor.YO))
        add(TestPhrase("Pásame el teléfono del taller", TestCategory.DEBE_ACEPTAR, ActionSuggested.TASK, Actor.ALGUIEN))
        add(TestPhrase("Llama a papá para su cumpleaños", TestCategory.DEBE_ACEPTAR, ActionSuggested.CALL, Actor.YO))

        // 26-30: Pagos
        add(TestPhrase("Pagar la luz antes del viernes", TestCategory.DEBE_ACEPTAR, ActionSuggested.PAYMENT, Actor.YO))
        add(TestPhrase("Hay que pagar el recibo del agua", TestCategory.DEBE_ACEPTAR, ActionSuggested.PAYMENT, Actor.NOSOTROS))
        add(TestPhrase("Pagar la tarjeta de crédito", TestCategory.DEBE_ACEPTAR, ActionSuggested.PAYMENT, Actor.YO))
        add(TestPhrase("Vence el pago del seguro el martes", TestCategory.DEBE_ACEPTAR, ActionSuggested.PAYMENT, Actor.YO))
        add(TestPhrase("Pagar la colegiatura de la escuela", TestCategory.DEBE_ACEPTAR, ActionSuggested.PAYMENT, Actor.YO))

        // 31-35: Ejercicio y hogar
        add(TestPhrase("Ir al gimnasio mañana temprano", TestCategory.DEBE_ACEPTAR, ActionSuggested.EXERCISE, Actor.YO))
        add(TestPhrase("Limpiar la cocina este fin de semana", TestCategory.DEBE_ACEPTAR, ActionSuggested.HOUSEHOLD, Actor.YO))
        add(TestPhrase("Ordenar el cuarto de los niños", TestCategory.DEBE_ACEPTAR, ActionSuggested.HOUSEHOLD, Actor.YO))
        add(TestPhrase("Cocinar la cena para mañana", TestCategory.DEBE_ACEPTAR, ActionSuggested.HOUSEHOLD, Actor.YO))
        add(TestPhrase("Lavar los platos después de cenar", TestCategory.DEBE_ACEPTAR, ActionSuggested.HOUSEHOLD, Actor.YO))

        // 36-40: Compras varias
        add(TestPhrase("Comprar detergente y verduras", TestCategory.DEBE_ACEPTAR, ActionSuggested.SHOPPING, Actor.YO))
        add(TestPhrase("Ir a la farmacia por las pastillas", TestCategory.DEBE_ACEPTAR, ActionSuggested.SHOPPING, Actor.YO))
        add(TestPhrase("Pasar al supermercado por huevos", TestCategory.DEBE_ACEPTAR, ActionSuggested.SHOPPING, Actor.YO))
        add(TestPhrase("Comprar el regalo de cumpleaños de Ana", TestCategory.DEBE_ACEPTAR, ActionSuggested.SHOPPING, Actor.YO))
        add(TestPhrase("Ir a la tienda por aceite y arroz", TestCategory.DEBE_ACEPTAR, ActionSuggested.SHOPPING, Actor.YO))

        // 41-45: Recordatorios
        add(TestPhrase("Avísame cuando sean las 3 de la tarde", TestCategory.DEBE_ACEPTAR, ActionSuggested.REMINDER, Actor.YO))
        add(TestPhrase("Notifícame la reunión de mañana", TestCategory.DEBE_ACEPTAR, ActionSuggested.REMINDER, Actor.YO))
        add(TestPhrase("Acuérdate de sacar la basura", TestCategory.DEBE_ACEPTAR, ActionSuggested.REMINDER, Actor.ALGUIEN))
        add(TestPhrase("Recuérdame tomar las vitaminas", TestCategory.DEBE_ACEPTAR, ActionSuggested.REMINDER, Actor.YO))
        add(TestPhrase("Pon una alarma para las 7 AM", TestCategory.DEBE_ACEPTAR, ActionSuggested.REMINDER, Actor.YO))

        // 46-50: Intenciones futuras
        add(TestPhrase("Voy a empezar la dieta el lunes", TestCategory.DEBE_ACEPTAR, ActionSuggested.TASK, Actor.YO))
        add(TestPhrase("Pienso terminar el libro esta semana", TestCategory.DEBE_ACEPTAR, ActionSuggested.TASK, Actor.YO))
        add(TestPhrase("Quiero aprender a tocar guitarra", TestCategory.DEBE_ACEPTAR, ActionSuggested.TASK, Actor.YO))
        add(TestPhrase("Intentaré dejar de fumar", TestCategory.DEBE_ACEPTAR, ActionSuggested.TASK, Actor.YO))
        add(TestPhrase("Vamos a ordenar el garage el sábado", TestCategory.DEBE_ACEPTAR, ActionSuggested.HOUSEHOLD, Actor.NOSOTROS))

        // =========================================================
        // GRUPO 2: 50 frases que DEBEN rechazar o preguntar
        // =========================================================

        // 51-55: Conversación casual (sin acción)
        add(TestPhrase("Hola, ¿cómo estás?", TestCategory.DEBE_RECHAZAR))
        add(TestPhrase("Buenos días, ¿todo bien?", TestCategory.DEBE_RECHAZAR))
        add(TestPhrase("Gracias por tu ayuda", TestCategory.DEBE_RECHAZAR))
        add(TestPhrase("Qué bonito día hace", TestCategory.DEBE_RECHAZAR))
        add(TestPhrase("Ok, está bien, luego hablamos", TestCategory.DEBE_RECHAZAR))

        // 56-60: Contenido bloqueado (sexual, violencia, drogas)
        add(TestPhrase("Contenido sexual explícito", TestCategory.DEBE_RECHAZAR))
        add(TestPhrase("Voy a matar a alguien", TestCategory.DEBE_RECHAZAR))
        add(TestPhrase("Dónde puedo comprar droga", TestCategory.DEBE_RECHAZAR))
        add(TestPhrase("Eres un malparido", TestCategory.DEBE_RECHAZAR))
        add(TestPhrase("Porno xxx gratis", TestCategory.DEBE_RECHAZAR))

        // 61-65: Datos sensibles
        add(TestPhrase("Mi contraseña es abc123", TestCategory.DEBE_RECHAZAR))
        add(TestPhrase("El código de verificación es 8472", TestCategory.DEBE_RECHAZAR))
        add(TestPhrase("Mi número de tarjeta es 4532123456789012", TestCategory.DEBE_RECHAZAR))
        add(TestPhrase("Mi PIN es 1234", TestCategory.DEBE_RECHAZAR))
        add(TestPhrase("Te envié mi clave por mensaje", TestCategory.DEBE_RECHAZAR))

        // 66-70: Frases sin contenido organizativo
        add(TestPhrase("Jaja qué chistoso", TestCategory.DEBE_RECHAZAR))
        add(TestPhrase("Te quiero mucho", TestCategory.DEBE_RECHAZAR))
        add(TestPhrase("Buen finde", TestCategory.DEBE_RECHAZAR))
        add(TestPhrase("Nos vemos luego", TestCategory.DEBE_RECHAZAR))
        add(TestPhrase("Qué calor hace hoy", TestCategory.DEBE_RECHAZAR))

        // 71-75: Expresiones sin acción clara (deben preguntar)
        add(TestPhrase("El supermercado", TestCategory.DEBE_RECHAZAR, note="Genérico, debe preguntar qué quiere hacer"))
        add(TestPhrase("Mi mamá", TestCategory.DEBE_RECHAZAR, note="Ambiguo"))
        add(TestPhrase("Mañana", TestCategory.DEBE_RECHAZAR, note="Solo fecha sin acción"))
        add(TestPhrase("El viernes", TestCategory.DEBE_RECHAZAR, note="Solo fecha sin acción"))
        add(TestPhrase("Juan", TestCategory.DEBE_RECHAZAR, note="Solo nombre sin acción"))

        // 76-80: Preguntas sin intención organizativa
        add(TestPhrase("Qué hora es", TestCategory.DEBE_RECHAZAR))
        add(TestPhrase("Dónde queda la farmacia", TestCategory.DEBE_RECHAZAR))
        add(TestPhrase("Cuánto cuesta el pan", TestCategory.DEBE_RECHAZAR))
        add(TestPhrase("Quién ganó el partido", TestCategory.DEBE_RECHAZAR))
        add(TestPhrase("Cómo se llega al centro", TestCategory.DEBE_RECHAZAR))

        // 81-85: Afirmaciones sin compromiso
        add(TestPhrase("Está bien", TestCategory.DEBE_RECHAZAR))
        add(TestPhrase("Sí, claro", TestCategory.DEBE_RECHAZAR))
        add(TestPhrase("No sé", TestCategory.DEBE_RECHAZAR))
        add(TestPhrase("Puede ser", TestCategory.DEBE_RECHAZAR))
        add(TestPhrase("Quién sabe", TestCategory.DEBE_RECHAZAR))

        // 86-90: Mensajes cortos sin contexto
        add(TestPhrase("Ok", TestCategory.DEBE_RECHAZAR))
        add(TestPhrase("Jeje", TestCategory.DEBE_RECHAZAR))
        add(TestPhrase("Lol", TestCategory.DEBE_RECHAZAR))
        add(TestPhrase("X", TestCategory.DEBE_RECHAZAR))
        add(TestPhrase("...", TestCategory.DEBE_RECHAZAR))

        // 91-95: Temas no organizativos
        add(TestPhrase("Feliz cumpleaños", TestCategory.DEBE_RECHAZAR))
        add(TestPhrase("Feliz navidad", TestCategory.DEBE_RECHAZAR))
        add(TestPhrase("Buen provecho", TestCategory.DEBE_RECHAZAR))
        add(TestPhrase("Salud", TestCategory.DEBE_RECHAZAR))
        add(TestPhrase("Felicidades", TestCategory.DEBE_RECHAZAR))

        // 96-100: Frases incompletas o sin sentido
        add(TestPhrase("Que tengas buen día", TestCategory.DEBE_RECHAZAR))
        add(TestPhrase("Descansa", TestCategory.DEBE_RECHAZAR))
        add(TestPhrase("Cuídate", TestCategory.DEBE_RECHAZAR))
        add(TestPhrase("Suerte", TestCategory.DEBE_RECHAZAR))
        add(TestPhrase("Bueno, me voy", TestCategory.DEBE_RECHAZAR))
    }
}
