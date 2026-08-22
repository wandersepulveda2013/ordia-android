import com.ordia.app.context.ContextIntentEngine
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextCaptureSource

/**
 * Sonda c.889 (persistida): última lateral de la familia «hacerse»
 * (medida NULL y registrada como guard en `ContextIntentEnginePrueba
 * SangreFloorTest.bivalente sonido descartada`) — «hacerme la prueba
 * de sonido». El soundcheck del músico/técnico/speakers arrangement
 * es una diligencia con desplazamiento al local/sala (hermana ERRAND
 * de la familia, doctrina c.862 «la diligencia gobierna»); el objeto
 * bivalente «prueba» sin complemento NO se absorbe («prueba del
 * coche» sigue NULL). Guard de NULL del test c.876 convertida a
 * regresión de captura (precedente c.843).
 *
 * Sonda PRE c.889 (run_probe.sh): 7/7 candidatas NULL — asimetría
 * con la envolvente c.613 («recuérdame hacerme la prueba de sonido…»
 * ya rutea TASK via candado). POST c.889: 7/7 candidatas HIT ERRAND
 * 0.45 con títulos limpios («Hacerme la prueba de sonido») y dueAt
 * correcto; 6/6 guardas NULL intactas (negación, duda, pasado «me
 * hice…», forma desnuda, nominal, objeto bivalente «del coche»);
 * regresiones HIT (sangre c.876/embarazo c.882 ERRAND 0.45;
 * envolvente c.613 TASK 0.54). Guard de NULL del test c.876
 * convertida a regresión de captura (precedente c.843, hermana de
 * c.888).
 */
fun main() {
    fun a(t: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, t, 1_700_000_000_000L)
    )
    fun show(label: String, t: String) {
        val i = a(t)
        val s = if (i == null) "[NULL] $t" else "[HIT] ${i.kind} ${i.confidence} | ${i.title} | dueAt=${i.dueAt != null} ← $t"
        println("$label $s")
    }
    show("C1", "hacerme la prueba de sonido mañana")
    show("C2", "hacerme la prueba de sonido el viernes")
    show("C3", "hacerse la prueba de sonido mañana")
    show("C4", "hacerme una prueba de sonido")
    show("C5", "hacerme las pruebas de sonido mañana")
    show("C6", "mañana hacerme la prueba de sonido")
    show("C7", "vale, hacerme la prueba de sonido")
    show("G1", "no hacerme la prueba de sonido mañana")
    show("G2", "quizá hacerme la prueba de sonido mañana")
    show("G3", "me hice la prueba de sonido ayer")
    show("G4", "hacer la prueba de sonido mañana")
    show("G5", "la prueba de sonido quedó bien")
    show("G6", "hacerme la prueba del coche")
    show("R1", "hacerme la prueba de sangre mañana")
    show("R2", "hacerme la prueba de embarazo mañana")
    show("R3", "recuérdame hacerme la prueba de sonido mañana")
}
