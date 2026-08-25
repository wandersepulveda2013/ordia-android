import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

/**
 * Sonda de DESCUBRIMIENTO c.1132 (persistida): DECIMOQUINTA clase de
 * formas cotidianas — BUROCRACIA Y ADMINISTRACIÓN PÚBLICA dichas como
 * se hablan (sellar el paro, empadronarse, dar de alta/baja suministros,
 * pagar/recurrir multas, certificados, matrículas, tasas, notaría,
 * registro, hacienda, tráfico). La clase VIII (gestiones de la vida
 * adulta, c.857–c.865) agotó SIETE gaps concretos (medicación, correo,
 * contestar, análisis, declaración de la renta, escanear DNI, reclamar
 * factura) y las clases IX–XIV cerraron dinero/mascotas/digital/
 * vehículo/salud/escuela — pero el dominio burocrático español tiene
 * frontera real NO sondeada: el olvido tiene coste económico directo
 * (recargo de multa, pérdida de prestación, plazo de matrícula).
 *
 * Misma metodología que [FourteenthClassSchoolProbe] (c.1127),
 * [ThirteenthClassHealthProbe] (c.1102) y [EighthClassAdminProbe]
 * (c.857): frases declarativas cotidianas (compromiso plausible) +
 * regresiones (formas que YA capturan) + controles (negación, duda,
 * pasado, sustantivo/verbo aislado, declarativo sin compromiso).
 * NO es un test; su salida alimenta el BACKLOG (un ítem por ciclo,
 * doctrina anti-overreach).
 *
 * Criterio de lectura: NULL sobre una forma DECLARATIVA cotidiana es
 * un GAP de captura (olvido silencioso P1) si el enunciado es un
 * compromiso plausible del usuario. NULL sobre controles es CORRECTO
 * (intencionado). HIT sobre un control de PASADO es FALSO POSITIVO
 * (familia hermana del pretérito+MEETING medida c.1127-bis).
 *
 * Cobertura PREVIA relevante medida (keywords heredadas): piso
 * «renovar <objeto>» (c.698); piso «recoger <objeto>» (c.857);
 * piso «hacer la declaración de la renta» (c.863); piso «escanear
 * el DNI» (c.864); piso «reclamar la factura» (c.865); pisos PAYMENT
 * abiertos «pagar <objeto>»; keywords «cita previa»/«multa»/«pasaporte»
 * (regresiones VIII); «votar» (c.752).
 *
 * Estado medido en c.1132 (HEAD base `626027be` post-marcador,
 * run_probe.sh, motor real; suite UNIÓN OK 8921 medida en ESTE run;
 * smoke 25/25; automation 9/9):
 *   CAPTURAS — 13/20 HITs por cobertura HEREDADA (pagar la multa
 *   PAYMENT; pedir cita ayuntamiento TASK; pedir certificado TASK;
 *   renovar carnet TASK [piso c.698; cola «en marzo» dueAt=false —
 *   familia de colas conocida c.845/c.852/c.1079/c.1102/c.1127];
 *   recoger pasaporte ERRAND [piso c.857]; pagar tasas PAYMENT;
 *   notaría ERRAND; sacar cita ITV TASK [c.1117]; cambiar
 *   titularidad TASK [piso «cambiar» heredado]; recoger nota simple
 *   ERRAND; avisar a hacienda TASK [piso «avisar a» heredado];
 *   pedir hora en tráfico TASK) y 7 gaps NULL en CUATRO familias:
 *     a) «presentar <documento/trámite>» — 3/3 NULL (C6 recurso de
 *        multa, C11 matrícula universidad, C17 papeles ayuda
 *        alquiler): el verbo «presentar» sin keyword propia y sin
 *        objeto anclado → ni llega al análisis. CANDIDATA FUERTE
 *        (plazos oficiales: recurso antes de que la multa suba,
 *        matrícula, ayuda del alquiler). Piso NUEVO acotado
 *        «presentar (el|la|los|las) <objeto>» + keyword-VERBO
 *        «presentar» (lockstep c.751, lección c.616).
 *     b) «dar de alta/baja <suministro>» — 2/2 NULL (C3 luz piso
 *        nuevo, C4 internet piso viejo): la perífrasis «dar de
 *        alta/baja» no es keyword. CANDIDATA (mudanza sin luz).
 *        Laterales del MISMO piso: agua/gas/seguro/gimnasio.
 *     c) «sellar el paro el día N» — 1/1 NULL (C1): verbo
 *        monosemántico «sellar» + objeto «paro». CANDIDATA (pérdida
 *        de prestación, el olvido más caro de la clase).
 *     d) «empadronarse / hacer la mudanza» — 2/2 NULL (C2
 *        reflexivo, C20): CANDIDATAS hermanas menores.
 *   REGRESIONES — 8/8 HITs intactos (renovar pasaporte c.698,
 *   declaración renta c.863, escanear DNI c.864, reclamar factura
 *   c.865, hipoteca PAYMENT, dentista, llamar banco CALL 0.67,
 *   médico).
 *   CONTROLES — 8/8 NULLs correctos; DESTACADO: K8 «pagué la multa
 *   ayer» NULL — el guard de pretérito SÍ gobierna PAYMENT (a
 *   diferencia de MEETING, falso positivo c.1127-bis en manos del
 *   hermano c.1129-bis): el guard es por-familia-de-piso, NO global.
 *   OBSERVACIONES laterales (NO gaps de esta clase): colas
 *   «en <mes>»/«este mes» quedan en el TÍTULO con dueAt=false
 *   (C9/R1/R2) — familia ya documentada, área parser del hermano.
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

    // --- CANDIDATAS (burocracia cotidiana: compromisos plausibles) ---
    show("C1",  "sellar el paro el día 4")
    show("C2",  "empadronarme en el nuevo piso este mes")
    show("C3",  "dar de alta la luz del piso nuevo mañana")
    show("C4",  "dar de baja el internet del piso viejo")
    show("C5",  "pagar la multa del coche antes del viernes")
    show("C6",  "presentar el recurso de la multa esta semana")
    show("C7",  "pedir cita en el ayuntamiento mañana")
    show("C8",  "pedir el certificado de empadronamiento el lunes")
    show("C9",  "renovar el carnet de conducir en marzo")
    show("C10", "recoger el pasaporte en comisaría el jueves")
    show("C11", "presentar la matrícula de la universidad antes del día 15")
    show("C12", "pagar las tasas de la universidad mañana")
    show("C13", "ir a la notaría a firmar la escritura el martes")
    show("C14", "sacar cita para la ITV la semana que viene")
    show("C15", "cambiar la titularidad del contrato de la luz")
    show("C16", "recoger la nota simple en el registro el viernes")
    show("C17", "presentar los papeles de la ayuda del alquiler")
    show("C18", "avisar a hacienda del cambio de domicilio")
    show("C19", "pedir hora en tráfico para el cambio de nombre del coche")
    show("C20", "hacer la mudanza del piso el fin de semana")

    // --- REGRESIONES (formas que YA capturan — deben seguir HIT) ---
    show("R1", "renovar el pasaporte este mes")
    show("R2", "hacer la declaración de la renta este mes")
    show("R3", "escanear el DNI esta tarde")
    show("R4", "reclamar la factura del banco mañana")
    show("R5", "pagar la hipoteca el día 1")
    show("R6", "pedir hora al dentista el lunes")
    show("R7", "llamar al banco mañana")
    show("R8", "tengo que ir al médico mañana")

    // --- CONTROLES (deben quedarse NULL) ---
    show("K1", "ya sellé el paro la semana pasada")
    show("K2", "no voy a pagar la multa")
    show("K3", "quizá me empadronen en el nuevo piso")
    show("K4", "la multa era de 90 euros")
    show("K5", "empadronarme")
    show("K6", "el certificado de empadronamiento")
    show("K7", "el ayuntamiento abre a las 9")
    show("K8", "pagué la multa ayer")
}
