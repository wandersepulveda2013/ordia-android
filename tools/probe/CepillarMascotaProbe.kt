import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

/**
 * Sonda persistida c.1226 (lateral (a) ABIERTA por la auditoría c.1225,
 * clase VIGESIMONOVENA hogar+mascotas — `HogarMascotasClassXXIXProbe.kt`
 * M4 «cepillar al gato» NULL medido): «cepillar al perro/gato» es la
 * higiene del pelaje, hermana estructural de «bañar» (c.761) y vecina de
 * la familia VET/FEED/WALK_DOG. «Cepillar» suelto es BIVALENTE (los
 * dientes / reflexivo «cepillarse»), así el piso se ACOTA al objeto
 * mascota `(?:perrit[oa]|perr[oa]|gatit[oa]|gat[oa])s?` — destinatario
 * humano FUERA (anti-overreach). Lockstep 2 puntos (lección c.616, hermano
 * c.1017/c.1202 sin keyword-verb): piso acotado [PET_BRUSH_FLOOR] +
 * plantilla matchCepillarMascota en extractTitle; gate c.751 intacto (la
 * keyword-mascota gato/gata ya existe c.744 y dispara el análisis).
 * Determinista (regex), sin IA fingida.
 */
fun main() {
    fun a(t: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, t, 1_700_000_000_000L)
    )
    fun show(label: String, t: String) {
        val i = a(t)
        if (i == null) println("$label [NULL] $t")
        else println("$label [HIT] ${i.kind} ${i.confidence} | ${i.title} | dueAt=${i.dueAt != null} <- $t")
    }
    show("T1", "cepillar al gato")
    show("T2", "cepillar al perro")
    show("T3", "cepillar a mi gata")
    show("T4", "cepillar al perro mañana")
    show("T5", "cepillar a los perros")
    show("G1", "no cepillar al gato")
    show("G2", "cepillé al perro")
    show("G3", "cepillarse los dientes")
    show("G4", "cepillar los dientes")
    show("G5", "cepillar al niño")
    show("R1", "bañar al perro")
    show("R2", "desparasitar al gato")
    show("R3", "castrar al perro")
    show("E1", "recuérdame cepillar al gato")
    show("E2", "tengo que cepillar al perro")
}
