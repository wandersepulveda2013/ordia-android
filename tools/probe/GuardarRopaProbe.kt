package com.ordia.app.probe

import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

// Sonda c.1222 PRE/POST (lateral ABIERTA (e) «guardar (la)? ropa» de la
// auditoría cl.XXVIII ROPA c.1209). Mide: 6 capturas objetivo (A1–A6),
// 4 guards vacíos esperados (G1–G4), 3 regresiones (R1–R3). Persistida
// en tools/probe tras su uso (doctrina).
fun main() {
    fun t(line: String, source: String) {
        val i = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, line, 1000)
        )
        println("$source | ${i?.kind} | ${i?.confidence} | \"${i?.title}\" <- $line")
    }
    // A: capturas objetivo
    t("guardar la ropa", "A1")
    t("guardar la ropa en el armario", "A2")
    t("guardar la ropa mañana", "A3")
    t("mañana guardar la ropa del pasillo", "A4")
    t("voy a guardar la ropa", "A5")
    t("por favor guardar la ropa", "A6")
    // G: guards que deben seguir NULL (no robar)
    t("guardé la ropa ayer", "G1")
    t("no guardar la ropa todavía", "G2")
    t("guardar documentos en la nube", "G3")
    t("guardar el archivo pdf", "G4")
    // R: regresiones (otas formas ROPA/HOUSEHOLD históricas)
    t("colgar la ropa", "R1")
    t("lavar la ropa", "R2")
    t("coser el botón", "R3")
}
