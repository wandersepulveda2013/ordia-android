// Sonda efímera de PRE/POST ciclo c.1154 — hallazgo P1 ABIERTO de c.1135
// (1b7c509, BACKLOG): pretérito de 1ª/3ª persona por el camino KEYWORD
// EXERCISE («campamento» c.1135, «natación», «extraescolar» c.1146):
// «inscribí al niño en el campamento ayer» → EXERCISE 0.45 con dueAt en el
// PASADO (hecho cumplido persistido como compromiso, hermano de c.1138).
//
// Uso: bash tools/run_probe.sh tools/probe/PastExerciseEnrollGuardProbe.kt
//
// Resultados PRE (HEAD 7e0e655, JVM kotlin 2.1.20, JDK 21) — 5/5 HIT
// EXERCISE 0.45 con dueAt PASADO (T1-T3, A4-A5; T4/T5 ya NULL):
//   T1 «inscribí al niño en el campamento ayer» → EXERCISE 0.45 dueAt=true [GAP]
//   T2 «inscribí a la niña en natación ayer» → EXERCISE 0.45 dueAt=true [GAP]
//   T3 «apunté a los niños en extraescolares la semana pasada» → 0.45 [GAP]
//   T4 «no inscribí al niño en el campamento» → NULL (guard c.648 ya lo cubre)
//   T5 «inscribí al niño en el campamento» (sin temporal) → NULL (bajo umbral)
//   R1 «inscribir al niño en el campamento mañana» → EXERCISE 0.45 (piso c.1135)
//   R2 «inscribir a la niña en natación el lunes» → EXERCISE 0.45 (piso c.616)
//   R3 «voy a inscribir al niño en natación mañana» → EXERCISE 0.50
//   R4 «recuérdame inscribir al niño en el campamento» → TASK 0.45 (c.613)
//   R5 «hay que inscribir al niño en extraescolares» → TASK 0.45 (c.1114)
//   X1 «mi mujer inscribió al niño en natación ayer» → EXERCISE 0.45 [GAP 3ª]
//   X2 «inscribo al niño en natación mañana» (presente) → EXERCISE 0.45
//   X3 «el campamento de los niños empieza en julio» → NULL (pin c.1141)
//   A1 «inscribimos al niño en natación en septiembre» → EXERCISE 0.45
//      (AMBIGUA presente/pretérito: EXCLUIDA del guard, lateral documentada)
//   A2 «inscribimos a los niños en el campamento ayer» → EXERCISE 0.45
//      (misma ambigua; lateral documentada)
//   A3 «apuntamos a la niña en extraescolares en septiembre» → EXERCISE 0.45
//      (AMBIGUA; EXCLUIDA del guard)
//   A4 «inscribieron a los niños en el campamento ayer» → EXERCISE 0.45 [GAP]
//   A5 «apuntó a la niña en natación ayer» → EXERCISE 0.45 dueAt=true [GAP]
//
// Resultados POST (guard c.1154 PAST_EXERCISE_ENROLL_PATTERN +
// pastExerciseEnrollGoverns): T1-T3/A4/A5/X1 → NULL; T4/T5/A1-A3 y TODAS las
// regresiones (R1-R5, X2, X3) byte-idénticas (kind/score/dueAt/título).

import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

fun main() {
    @Suppress("DEPRECATION")
    fun probe(tag: String, text: String) {
        val r = ContextIntentEngine.analyze(ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1000))
        println("$tag [${if (r == null) "NULL" else "HIT"}] ${r?.let { "${it.kind} ${it.confidence} | ${it.title} | dueAt=${it.dueAt != null}" } ?: text}")
    }
    // Pretérito 1ª persona + keyword EXERCISE (GAP c.1135-i)
    probe("T1", "inscribí al niño en el campamento ayer")
    probe("T2", "inscribí a la niña en natación ayer")
    probe("T3", "apunté a los niños en extraescolares la semana pasada")
    // Controles de pretérito ya correctos
    probe("T4", "no inscribí al niño en el campamento")
    probe("T5", "inscribí al niño en el campamento")
    // Regresiones de captura legítima
    probe("R1", "inscribir al niño en el campamento mañana")
    probe("R2", "inscribir a la niña en natación el lunes")
    probe("R3", "voy a inscribir al niño en natación mañana")
    probe("R4", "recuérdame inscribir al niño en el campamento")
    probe("R5", "hay que inscribir al niño en extraescolares")
    // Extras: 3ª persona, presente, pin declarativo
    probe("X1", "mi mujer inscribió al niño en natación ayer")
    probe("X2", "inscribo al niño en natación mañana")
    probe("X3", "el campamento de los niños empieza en julio")
    // Ambiguas presente/pretérito (EXCLUIDAS del guard — lateral documentada)
    probe("A1", "inscribimos al niño en natación en septiembre")
    probe("A2", "inscribimos a los niños en el campamento ayer")
    probe("A3", "apuntamos a la niña en extraescolares en septiembre")
    // Pretérito 3ª plural / apuntó 3ª (misma familia que T1-T3)
    probe("A4", "inscribieron a los niños en el campamento ayer")
    probe("A5", "apuntó a la niña en natación ayer")
}
