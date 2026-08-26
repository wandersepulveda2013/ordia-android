import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

/**
 * Sonda de DESCUBRIMIENTO c.1179 (persistida): VIGÉSIMA clase de
 * formas cotidianas — QUEHACER DOMÉSTICO / MANTENIMIENTO DEL HOGAR
 * (arreglar el grifo, cambiar la bombilla, poner la lavadora, tirar
 * la basura, limpiar el garaje, colgar la ropa, hacer la cama, fregar
 * los platos, pasar la aspiradora, regar las plantas, sacar al perro,
 * llevar el coche al taller). Las clases VI–XIX están abiertas o
 * agotadas; la frontera siguiente es el mantenimiento cotidiano del
 * hogar — dominio con coste real de olvido (la lavadora sin tender,
 * la basura sin bajar, el grifo que gotea semanas).
 *
 * Misma metodología que [NineteenthClassCommsProbe] (c.1173) y
 * anteriores: frases declarativas cotidianas (compromiso plausible) +
 * regresiones (formas que YA capturan, canario UNIÓN de ambos lados)
 * + controles (negación compuesta, duda subjuntivo, pasado, estado,
 * verbo aislado, sustantivo aislado). NO es un test; su salida
 * alimenta el BACKLOG (un ítem por ciclo, doctrina anti-overreach).
 *
 * Criterio de lectura: NULL sobre una forma DECLARATIVA cotidiana es
 * un GAP de captura (olvido silencioso P1) si el enunciado es un
 * compromiso plausible del usuario. NULL sobre controles es CORRECTO.
 *
 * Estado medido en c.1179 (run_probe.sh, motor real; PRE sobre
 * `e6f74681` — punta integrada con c.1175 propio y c.1172/c.1176 del
 * hermano; suite UNIÓN OK (9565), smoke 25/25):
 *   CAPTURAS — 13/14 HITs por cobertura HEREDADA (piso HOUSEHOLD
 *   robusto: arreglar el grifo, cambiar la bombilla, poner la
 *   lavadora, limpiar el garaje, colgar la ropa, hacer la cama,
 *   fregar los platos, pasar la aspiradora, regar las plantas, sacar
 *   al perro, ordenar el trastero — todos HOUSEHOLD 0.45; llevar el
 *   coche al taller ERRAND 0.45 vía piso taller c.684; comprar
 *   detergente SHOPPING 0.47) y 1 gap NULL:
 *     a) FUERTE «tirar la basura esta noche» (C4) — el olvido
 *        doméstico canónico (la basura sin bajar). El piso HOUSEHOLD
 *        cubre muchos verbos pero no «tirar»; «basura» no es keyword.
 *        El guard de negación G1 «no voy a tirar la basura…» ya es
 *        NULL correcto (guard c.1009 gobierna sin piso).
 *   REGRESIONES — 8/8 HITs intactos (leche SHOPPING, luz PAYMENT,
 *   médico APPOINTMENT 0.85, fiesta del cole ERRAND c.1170, «haré la
 *   mudanza» TASK 0.45 c.1175 propio, contestar el correo TASK, «mi
 *   hijo al médico» ERRAND c.1176 del hermano, llamar CALL 0.67 —
 *   UNIÓN de ambos lados verificada en vivo).
 *   CONTROLES — 8/8 NULLs correctos (negación compuesta, duda
 *   subjuntivo, pasado ×2, estado «la lavadora está rota», verbo
 *   aislado, estado-pasado «el perro salió…», sustantivo-estado «la
 *   basura huele mal»).
 *   OBSERVACIONES laterales (NO de esta clase):
 *   - C1 «esta semana», C8 «después de comer», C13 «este finde»:
 *     dueAt=false con residuo en título — familia conocida de colas
 *     (c.845/c.852/c.1079/c.1102/c.1165), consistente.
 *   - C6 «ahora» dueAt=false — «ahora» no ancla (consistente con
 *     clases anteriores).
 */
fun main() {
    fun a(t: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, t, 1_700_000_000_000L)
    )
    fun show(label: String, t: String) {
        val i = a(t)
        val s = if (i == null) "[NULL] $t"
            else "[HIT] ${i.kind} ${i.confidence} | ${i.title} | dueAt=${i.dueAt != null} ← $t"
        println("$label $s")
    }

    // --- CANDIDATAS (quehacer doméstico / mantenimiento del hogar) ---
    show("C1", "arreglar el grifo de la cocina esta semana")
    show("C2", "cambiar la bombilla del pasillo mañana")
    show("C3", "poner la lavadora esta tarde")
    show("C4", "tirar la basura esta noche")
    show("C5", "limpiar el garaje el sábado")
    show("C6", "colgar la ropa ahora")
    show("C7", "hacer la cama por la mañana")
    show("C8", "fregar los platos después de comer")
    show("C9", "pasar la aspiradora mañana")
    show("C10", "regar las plantas esta noche")
    show("C11", "sacar al perro a las 8")
    show("C12", "llevar el coche al taller el lunes")
    show("C13", "ordenar el trastero este finde")
    show("C14", "comprar detergente esta tarde")

    // --- REGRESIONES (formas que YA capturan, canario UNIÓN) ---
    show("R1", "comprar leche esta tarde")
    show("R2", "pagar la luz el día 5")
    show("R3", "ir al médico el lunes a las 5")
    show("R4", "llevar a los niños a la fiesta del cole el viernes")
    show("R5", "haré la mudanza en octubre")
    show("R6", "contestar el correo del cole esta tarde")
    show("R7", "llevar a mi hijo al médico mañana")
    show("R8", "llamar a mamá esta noche")

    // --- CONTROLES (NULLs correctos esperados) ---
    show("G1", "no voy a tirar la basura esta noche")
    show("G2", "quizá limpie el garaje el sábado")
    show("G3", "arreglé el grifo ayer")
    show("G4", "la lavadora está rota")
    show("G5", "fregar")
    show("G6", "el perro salió esta mañana")
    show("G7", "saqué al perro ayer")
    show("G8", "la basura huele mal")
}
