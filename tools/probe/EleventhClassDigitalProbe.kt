import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

/**
 * Sonda de DESCUBRIMIENTO c.1026 (persistida): UNDÉCIMA clase de
 * formas cotidianas — VIDA DIGITAL (los trámites tecnológicos de
 * cada día dichos como se habla: imprimir/descargar documentos,
 * contraseñas, actualizaciones, copias de seguridad,
 * suscripciones, dispositivos domésticos). Las clases VI
 * (enclíticos, c.834), VII (diligencias sociales, c.845), VIII
 * (vida adulta, c.857), IX (dinero y banca cotidiana, c.890/c.892)
 * y X/DÉCIMA (mascotas, c.1007) quedaron AGOTADAS; la frontera
 * siguiente es el ámbito digital — dominio cotidiano con coste
 * real de olvido (billetes sin imprimir, copias de seguridad no
 * hechas, suscripciones que renuevan solas, contraseñas filtradas).
 *
 * Misma metodología que [TenthClassPetProbe] (c.1007),
 * [NinthClassMoneyProbe] (c.892), [EighthClassAdminProbe] (c.857)
 * y [SeventhClassErrandProbe] (c.845): frases declarativas
 * cotidianas (compromiso plausible) + regresiones (formas que YA
 * capturan) + controles (negación, duda, pasado, sustantivo
 * aislado, figurado, no-compromiso). NO es un test; su salida
 * alimenta el BACKLOG (un ítem por ciclo, doctrina
 * anti-overreach).
 *
 * Criterio de lectura: NULL sobre una forma DECLARATIVA cotidiana
 * es un GAP de captura (olvido silencioso P1) si el enunciado es
 * un compromiso plausible del usuario. NULL sobre controles es
 * CORRECTO (intencionado).
 *
 * Estado medido en c.1026 [renumerado c.1025->c.1026 por colisión cycle-ID con SU c.1025 docs-close verificación UNIÓN `95496e61`] (HEAD `8af00df1` c.1024 propio,
 * run_probe.sh, motor real; suite OK (7434), smoke 25/25):
 *   CAPTURAS — 11/14 HITs (imprimir billetes TASK, descargar
 *   factura TASK, actualizar móvil TASK, copia seguridad TASK,
 *   reiniciar router TASK, subir fotos TASK, cancelar suscripción
 *   TASK, renovar suscripción TASK, vaciar bandeja HOUSEHOLD,
 *   enviar justificante TASK, devolver router ERRAND) y 3 gaps
 *   NULL confirmados:
 *     1) «cambiar la contraseña del banco esta tarde» — el piso
 *        «cambiar <objeto>» c.710 EXISTE (paradoja medida:
 *        «cambiar euros» HIT), pero «contraseña» no es keyword de
 *        ningún kind → la frase ni llega al análisis (lección
 *        c.751). CANDIDATA P1.
 *     2) «configurar el móvil nuevo por la noche» — verbo
 *        «configurar» sin piso ni keyword. CANDIDATA P1.
 *        → RESUELTA c.1032 (piso acotado «configurar <dispositivo>»
 *        + keyword + plantilla; HIT TASK 0.45 «Configurar el móvil
 *        nuevo»).
 *     3) «formatear el ordenador el sábado» — verbo «formatear»
 *        sin piso ni keyword. CANDIDATA P1.
 *        → RESUELTA c.1036 (piso acotado «formatear <dispositivo>»
 *        + keyword + plantilla; HIT TASK 0.45 «Formatear el
 *        ordenador» dueAt=true).
 *   RE-MEDICIÓN c.1036 (POST): 13/14 candidatas HIT (sólo C3
 *   «cambiar la contraseña del banco esta tarde» sigue NULL —
 *   keyword-objeto ausente, próxima frontera medida), regresiones
 *   8/8 intactas, controles 7/8 NULL correctos + G7 falso positivo
 *   conocido (candidata P2).
 *   REGRESIONES — 8/8 HITs intactos (correo TASK c.860, DNI TASK
 *   c.864, luz PAYMENT, euros TASK c.710, llamar CALL 0.67,
 *   cajero ERRAND c.893, baja gimnasio TASK c.892, cargar móvil
 *   TASK c.851).
 *   CONTROLES — 7/8 NULLs correctos (negación plan c.1009, duda
 *   subjuntivo, pasado «ya imprimí», sustantivo aislado,
 *   «router» aislado, estado «está hecha», pasado figurado
 *   «descargó») y 1 FALSO POSITIVO: G7 «subir de peso este
 *   verano» → HIT TASK 0.45 (piso abierto «subir <objeto>» c.724
 *   captura el figurado corporal — mismo patrón que el G1 de la
 *   DÉCIMA; CANDIDATA P2 de precisión, guard acotado).
 *   OBSERVACIONES laterales (parser, NO de esta clase): «por la
 *   tarde/noche» no ancla dueAt (C6/R8 dueAt=false); «este mes»
 *   tampoco (ya registrado c.845/c.852) y queda en el título
 *   (C8 — lateral de colas ABIERTA de la familia de pisos).
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

    // --- CANDIDATAS (vida digital: compromisos cotidianos plausibles) ---
    show("C1", "imprimir los billetes del avión esta noche")
    show("C2", "descargar la factura de la luz mañana")
    show("C3", "cambiar la contraseña del banco esta tarde")
    show("C4", "actualizar el móvil esta noche")
    show("C5", "hacer la copia de seguridad del móvil el domingo")
    show("C6", "reiniciar el router por la tarde")
    show("C7", "subir las fotos al drive esta noche")
    show("C8", "cancelar la suscripción de Netflix este mes")
    show("C9", "renovar la suscripción del antivirus el viernes")
    show("C10", "configurar el móvil nuevo por la noche")
    show("C11", "formatear el ordenador el sábado")
    show("C12", "vaciar la bandeja de entrada mañana")
    show("C13", "enviar el justificante de pago por correo")
    show("C14", "devolver el router a la operadora el lunes")

    // --- REGRESIONES (formas que YA capturan, canario) ---
    show("R1", "responder el correo del trabajo mañana")
    show("R2", "escanear el DNI esta tarde")
    show("R3", "pagar la luz el día 5")
    show("R4", "cambiar euros en el banco")
    show("R5", "llamar a mamá esta noche")
    show("R6", "sacar dinero del cajero mañana")
    show("R7", "dar de baja el gimnasio la semana que viene")
    show("R8", "cargar el móvil por la noche")

    // --- CONTROLES (NULLs correctos esperados) ---
    show("G1", "no voy a imprimir los billetes hoy")
    show("G2", "quizá actualice el móvil esta noche")
    show("G3", "ya imprimí los billetes ayer")
    show("G4", "la contraseña del banco")
    show("G5", "router")
    show("G6", "la copia de seguridad está hecha")
    show("G7", "subir de peso este verano")
    show("G8", "la tormenta descargó anoche")
}
