import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

/* Sonda efímera-persistida c.889 — decisión de dominio «hacerme la prueba
 * de sonido…» (soundcheck previo al evento, gestión propia con
 * desplazamiento; ÚLTIMA lateral viva de la familia «hacerme/se la
 * prueba…» c.862/c.876/c.882, registrada como guard-sentinel NULL en el
 * test del hermano c.876 `bivalente sonido descartada`).
 *
 * Decisión de dominio c.889: el complemento «de sonido» es tan inequívoco
 * como «de sangre» (c.876) / «de embarazo» (c.882) — se ancla como
 * complemento hermano en el objeto del piso ERRAND («la diligencia
 * gobierna», c.842); «prueba del coche» (ITV/formalismo vehicular)
 * sigue FUERA (lateral registrada, UNA por ciclo); enclítico reflexivo
 * EXIGIDO (doctrina c.862: la forma desnuda «hacer la prueba de
 * sonido…» es bivalente y sigue FUERA).
 *
 * Sonda PRE c.889 (run_probe.sh): candidatas NULL (8/8); guards NULL
 * (negación, duda, pasado, forma desnuda, complemento coche, afirmación
 * nominal); regresiones HIT (sangre c.876 / embarazo c.882 / tatuaje
 * c.881) y envolvente c.613 TASK. POST c.889: 8/8 HIT ERRAND títulos
 * limpios («Hacerme la prueba de sonido»); guards NULL intactas;
 * regresiones/envolvente HIT. Sentinel c.876 convertida a regresión de
 * captura (precedente c.843, hermana del procedimiento c.882).
 */
fun main() {
    fun a(t: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, t, 1_700_000_000_000L)
    )
    for ((l, s) in listOf(
        "C1" to "hacerme la prueba de sonido mañana",
        "C2" to "hacerme la prueba de sonido el viernes",
        "C3" to "hacerme una prueba de sonido mañana",
        "C4" to "hacerte la prueba de sonido mañana",
        "C5" to "hacerse la prueba de sonido el viernes",
        "C6" to "mañana hacerme la prueba de sonido",
        "C7" to "vale, hacerme la prueba de sonido mañana",
        "C8" to "hacernos la prueba de sonido mañana",
        "G1" to "no hacerme la prueba de sonido mañana",
        "G2" to "quizá hacerme la prueba de sonido mañana",
        "G3" to "me hice la prueba de sonido ayer",
        "G4" to "hacer la prueba de sonido mañana",
        "G5" to "hacerme la prueba del coche mañana",
        "G6" to "la prueba de sonido quedó hecha",
        "R1" to "hacerme la prueba de sangre mañana",
        "R2" to "hacerte la prueba de embarazo mañana",
        "R3" to "hacernos un tatuaje mañana",
        "R4" to "recuérdame hacerme la prueba de sonido mañana",
    )) {
        val r = a(s); println(
            "%s [%s] %s %.2f | %s | dueAt=%s ← %s".format(
                l, if (r == null) "NULL" else "HIT",
                r?.kind, r?.confidence ?: 0f, r?.title, (r?.dueAt != null), s
            )
        )
    }
}
