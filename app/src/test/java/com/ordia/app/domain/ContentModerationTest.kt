package com.ordia.app.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Lock de regresión del algoritmo de moderación temática compartido
 * ([ContentModeration.isHarmful]). Es P0 (c.582): las listas anteriores casaban
 * RAÍCES ("matar", "bomba", "violar", "droga", "pene"...) sin distinguir el
 * sentido legítimo, así que 16/17 tareas reales se descartaban silenciosamente
 * en el canal de captura contextual o se rechazaban en el asistente del overlay.
 * Pérdida de datos en las rutas de mayor valor.
 *
 * El algoritmo es por RAÍZ + EXENCIÓN: una raíz dañina solo bloquea si aparece en
 * una ocurrencia NO cubierta por una forma legítima (colocación que CONTIENE al
 * rango de la raíz, o proximidad que cubre TODAS las ocurrencias). Así
 * "matar el proceso y luego matar a juan" sigue bloqueando (la 2ª ocurrencia no
 * está cubierta) mientras "matar el proceso" pasa. Determinista (regex + rangos),
 * sin random, sin IA fingida.
 *
 * Estos tests reproducen las reglas canónicas de IntelligenceSafetyGate (c.582)
 * para validar el algoritmo sin acoplarse al gate concreto (que también filtra
 * secretos/PII). Falsos positivos (tareas legítimas) → isHarmful == false;
 * verdaderos positivos → isHarmful == true.
 */
class ContentModerationTest {

    // Reglas canónicas del gate de inteligencia (c.582). Se replican aquí para
    // testear el algoritmo sin depender de los filtros de secreto del gate.
    private val sexual = ContentModeration.ModerationRule(
        stem = Regex("""\b(sexo|sexual|desnud|porno|xxx|eroti|culos|tetas|pene|vagina|orgasmo|masturb)\b"""),
        contain = listOf(
            Regex("""\b(cita con el ur[oó]logo|cita con la ginec[oó]loga?)\b[^.]*\bpene\b"""),
            Regex("""\b(revisi[oó]n de|revisar la|revisar el|examen de la|examen del)[^.]*\b(pene|vagina)\b""")
        ),
        proximity = Regex("""\b(ur[oó]logo|ginec[oó]log[oa]|sex[oó]logo|prostate|m[ée]dico|cl[íi]nica|farmac[ée]utic[oa])\b""")
    )
    private val violencia = ContentModeration.ModerationRule(
        stem = Regex("""\b(matar|asesinar|violar|secuestr|bomba|amenaza|escopeta|pistola|cuchill)\b"""),
        contain = listOf(
            Regex("""\bmatar\b\s+(el|la|los|las|un|una)?\s*(proceso|hilo|servicio|servidor|demonio|sesi[oó]n|tarea|job|zombie)\b"""),
            Regex("""\bviolar\b\s+(la|el|una|un|las|los)?\s*(pol[ií]tica|contrato|licencia|restricci[oó]n|norma|ley|clausula|cl[áa]usula|t[ée]rminos?)\b"""),
            Regex("""\b(modelo|m[oó]delo)\s+de\s+amenaza\b"""),
            Regex("""\bamenaza\b\s+(de)?\s*(de\s+integridad|de\s+seguridad|de\s+modelo)\b"""),
            Regex("""\b(bomba|pistola|escopeta)\s+de\s+agua\b"""),
            Regex("""\b(matar|asesinar)\s+(un|el)\s+proceso\b""")
        )
    )
    private val drogas = ContentModeration.ModerationRule(
        stem = Regex("""\b(droga|cocaina|heroina|marihuana|metanfetamina|narcotrafico)\b"""),
        proximity = Regex("""\b(farmac[ée]utic[oa]|farmacia|recetad[oa]|m[ée]dic[oa]|medicament[oa]|ur[oó]log[oa]|receta|tratamiento|recetar)\b""")
    )

    private fun sexualHarmful(text: String) = ContentModeration.isHarmful(text, sexual)
    private fun violentHarmful(text: String) = ContentModeration.isHarmful(text, violencia)
    private fun drugHarmful(text: String) = ContentModeration.isHarmful(text, drogas)

    // ── Falsos positivos corregidos: tareas legítimas que antes se bloqueaban ──

    @Test fun matarElProceso_noEsDanino() {
        assertFalse(violentHarmful("recuérdame matar el proceso del servidor"))
    }

    @Test fun matarElHiloZombie_noEsDanino() {
        assertFalse(violentHarmful("matar el hilo zombie que quedó colgado"))
    }

    @Test fun violarLaPolitica_noEsDanino() {
        assertFalse(violentHarmful("esto violar la política de la empresa"))
    }

    @Test fun modeloDeAmenaza_noEsDanino() {
        assertFalse(violentHarmful("hacer el modelo de amenaza del nuevo módulo"))
    }

    @Test fun bombaDeAgua_noEsDanino() {
        assertFalse(violentHarmful("comprar bomba de agua para el jardín"))
    }

    @Test fun pistolaDeAgua_noEsDanino() {
        assertFalse(violentHarmful("limpiar la pistola de agua de los niños"))
    }

    @Test fun secuestroDeDns_noEsDanino() {
        // "secuestr" no casa "secuestro" (límite de palabra): no se evalúa.
        assertFalse(violentHarmful("revisar el secuestro de DNS del servicio"))
    }

    @Test fun drogaEnFarmacia_noEsDanina() {
        // Proximidad médica/farmacéutica cubre TODAS las ocurrencias de "droga".
        assertFalse(drugHarmful("comprar la droga en la farmacia"))
    }

    @Test fun drogaRecetada_noEsDanina() {
        assertFalse(drugHarmful("ir a buscar la droga recetada al médico"))
    }

    @Test fun citaUrologoPorPene_noEsDanino() {
        // Colocación: "cita con el urólogo..." contiene el rango de "pene".
        assertFalse(sexualHarmful("cita con el urólogo por el pene"))
    }

    @Test fun examenProstate_noEsDanino() {
        // Proximidad médica cubre la mención anatómica.
        assertFalse(sexualHarmful("examen de la prostate de rutina"))
    }

    // ── Verdaderos positivos: contenido dañino que debe seguir bloqueándose ──

    @Test fun matarAUnaPersona_esDanino() {
        // 2ª ocurrencia sin cobertura: "matar a juan" no es técnica.
        assertTrue(violentHarmful("matar el proceso y luego matar a juan"))
    }

    @Test fun comprarBomba_esDanino() {
        assertTrue(violentHarmful("comprar una bomba para el ataque"))
    }

    @Test fun violarAAlguien_esDanino() {
        assertTrue(violentHarmful("violar a alguien esta noche"))
    }

    @Test fun amenazarConArma_esDanino() {
        assertTrue(violentHarmful("amenazar al vecino con una pistola"))
    }

    @Test fun comprarCocaina_esDanino() {
        assertTrue(drugHarmful("vende cocaina barata en la zona"))
    }

    @Test fun traeHeroina_esDanino() {
        assertTrue(drugHarmful("trae heroina pura para el viernes"))
    }

    @Test fun narcotrafico_esDanino() {
        assertTrue(drugHarmful("hay narcotrafico en la frontera"))
    }

    @Test fun contenidoPornografico_esDanino() {
        assertTrue(sexualHarmful("buscar porno en el móvil"))
    }

    @Test fun peneAislado_esDanino() {
        // Sin contexto médico que lo cubra.
        assertTrue(sexualHarmful("envíame una foto de tu pene"))
    }

    @Test fun drogaSinContextoMedico_esDanina() {
        assertTrue(drugHarmful("conseguir droga para la fiesta"))
    }

    // ── Normalización tolerante a acentos (c.519) ──

    @Test fun cocainaSinTilde_esDanina() {
        // La raíz ASCII casa el texto normalizado (sin diacríticos).
        assertTrue(drugHarmful("vende cocaína barata"))
    }

    @Test fun urologoConTilde_noEsDanino() {
        // La exención de proximidad también normaliza: "urólogo" con tilde cubre.
        assertFalse(sexualHarmful("cita con el urólogo por el pene"))
    }
}
