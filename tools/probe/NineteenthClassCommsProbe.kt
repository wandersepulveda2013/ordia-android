import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

/**
 * Sonda de DESCUBRIMIENTO c.1173 (persistida): DECIMONOVENA clase de
 * formas cotidianas — COMUNICACIONES PENDIENTES (llamadas, correos y
 * mensajería instantánea por responder: devolver la llamada, responder
 * el correo, contestar el WhatsApp, escribir a la casera, mandar el
 * informe). Las clases VI–XVIII están abiertas o agotadas; la frontera
 * siguiente es la bandeja de salida cotidiana — dominio con coste real
 * de olvido (el WhatsApp sin contestar, el correo del banco sin
 * responder, la llamada sin devolver).
 *
 * Misma metodología que [EighteenthClassSocialProbe] (c.1165) y
 * anteriores: frases declarativas cotidianas (compromiso plausible) +
 * regresiones (formas que YA capturan) + controles (negación compuesta,
 * duda, pasado, sustantivo aislado, verbo aislado, pasivo-pasado). NO
 * es un test; su salida alimenta el BACKLOG (un ítem por ciclo,
 * doctrina anti-overreach).
 *
 * Criterio de lectura: NULL sobre una forma DECLARATIVA cotidiana es
 * un GAP de captura (olvido silencioso P1) si el enunciado es un
 * compromiso plausible del usuario. NULL sobre controles es CORRECTO.
 *
 * Estado medido en c.1173 (run_probe.sh, motor real; PRE sobre
 * `9220964` marcador propio, RE-MEDIDA FINAL sobre `06d960a` post-
 * integración del fix «felicitar» del hermano (c.1167, `c83c9e8`);
 * suite OK (9499→re-verificada), smoke 25/25, automation 9/9):
 *   CAPTURAS — 12/14 HITs por cobertura HEREDADA (devolver la llamada
 *   ERRAND vía piso «devolver»; responder/contestar el correo TASK vía
 *   keyword «correo»; escribir a/para TASK; mandar informe/WhatsApp
 *   TASK vía keyword «mandar»; llamar a la gestoría/seguro CALL 0.67;
 *   responder al mensaje TASK vía keyword «mensaje») y 2 gaps NULL:
 *     a) «contestar el WhatsApp de Marta esta noche» — «contestar» +
 *        objeto «WhatsApp» sin keyword (C8 «contestar el correo» SÍ
 *        captura por keyword «correo»: asimetría de objeto). El
 *        WhatsApp sin contestar es el olvido social canónico moderno.
 *        CANDIDATA FUERTE.
 *     b) «responder el mail de trabajo esta noche» — anglicismo «mail»
 *        sin keyword mientras «correo» SÍ (C2 HIT). CANDIDATA (fix
 *        barato: keyword sinónima; evaluar «email» en la misma medida).
 *   REGRESIONES — 8/8 HITs intactos (llamar CALL 0.67, leche SHOPPING,
 *   luz PAYMENT, médico APPOINTMENT 0.85, fiesta del cole ERRAND c.1170
 *   propio, facturar la maleta TASK c.1168, hacer la mudanza TASK
 *   c.1169 y felicitar TASK 0.45 c.1167 del hermano — UNIÓN de ambos
 *   lados verificada en vivo sobre la base final; en la PRE de
 *   `9220964` R8 era NULL esperado porque el fix c.1167 aún no estaba
 *   integrado — NO era regresión).
 *   CONTROLES — 8/8 NULLs correctos (negación compuesta, duda
 *   subjuntivo, pasado ×2, sustantivo aislado, verbo aislado, estado
 *   pasado «el WhatsApp sonó…», pasivo-pasado «me llamaron del banco»).
 *   OBSERVACIONES laterales (NO de esta clase):
 *   - C1/C9 «devolver la llamada a/de…» capturan con kind ERRAND
 *     (piso «devolver lo prestado» c.1165-era): el compromiso SÍ se
 *     captura y el título es correcto; el kind es discutible (una
 *     llamada no es un recado físico) → posible P2 honestidad-de-kind,
 *     hermana del hallazgo «campamento» de c.1165. Registrar solo si
 *     se confirma impacto (los kinds alimentan agrupación/sugerencias).
 *   - C4/C6/C9 dueAt=false: sin ancla temporal o «esta semana»-style
 *     (consistente con c.845/c.852/c.1165).
 *   - C10 «escribir a Laura para quedar el sábado» pierde la cola
 *     «el sábado» del título (depuración temporal) pero SÍ ancla
 *     dueAt=true — comportamiento correcto, documentado.
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

    // --- CANDIDATAS (comunicaciones pendientes) ---
    show("C1", "devolver la llamada a Juan esta tarde")
    show("C2", "responder el correo del banco mañana")
    show("C3", "contestar el WhatsApp de Marta esta noche")
    show("C4", "escribir a la casera por la avería")
    show("C5", "mandar el informe a mi jefe el jueves")
    show("C6", "responder al mensaje del grupo del cole")
    show("C7", "enviar un correo a Recursos Humanos el lunes")
    show("C8", "contestar el correo del cole esta tarde")
    show("C9", "devolver la llamada de mamá")
    show("C10", "escribir a Laura para quedar el sábado")
    show("C11", "mandar un WhatsApp al dentista mañana")
    show("C12", "llamar a la gestoría el martes")
    show("C13", "responder el mail de trabajo esta noche")
    show("C14", "llamar al seguro mañana")

    // --- REGRESIONES (formas que YA capturan, canario UNIÓN) ---
    show("R1", "llamar a mamá esta noche")
    show("R2", "comprar leche esta tarde")
    show("R3", "pagar la luz el día 5")
    show("R4", "ir al médico el lunes a las 5")
    show("R5", "llevar a los niños a la fiesta del cole el viernes")
    show("R6", "facturar la maleta mañana")
    show("R7", "hacer la mudanza este finde")
    show("R8", "felicitar a Laura mañana")

    // --- CONTROLES (NULLs correctos esperados) ---
    show("G1", "no voy a responder el correo del banco mañana")
    show("G2", "quizá conteste el WhatsApp de Marta")
    show("G3", "respondí el correo ayer")
    show("G4", "la llamada perdida de Juan")
    show("G5", "responder")
    show("G6", "el WhatsApp sonó toda la noche")
    show("G7", "me llamaron del banco")
    show("G8", "contesté a Marta ayer")
}
