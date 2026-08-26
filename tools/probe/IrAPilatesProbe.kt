// Sonda c.1232: médida PRE/POST «ir a pilates» (lateral (d) MEDIA,
// auditoría c.1227 cl.XXX deporte). PRE = NULL en targets = brecha.
package com.ordia.app.context

fun main() {
    val cases = listOf(
        "T1" to "ir a pilates el lunes",
        "T2" to "ir a pilates mañana",
        "T3" to "pilates a las siete",
        "T4" to "empezar pilates la semana que viene",
        "G1" to "el pilates es buena disciplina",
        "G2" to "fui a pilates ayer",
        "G3" to "no voy a pilates",
        "R1" to "ir al gimnasio el lunes",
        "R2" to "hacer yoga los martes"
    )
    for ((label, text) in cases) {
        val t = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1000)
        )
        println(label + (t?.let {
            " [HIT] " + it.kind + " | " + it.title + " | dueAt=" + (it.dueAt != null)
        } ?: " [NULL]"))
    }
    println("sonda c.1232 ok")
}
