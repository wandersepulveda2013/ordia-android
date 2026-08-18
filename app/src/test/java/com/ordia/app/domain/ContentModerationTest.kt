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

    // Reglas canónicas del gate de inteligencia (c.582 + c.630). Se replican aquí
    // para testear el algoritmo sin depender de los filtros de secreto del gate.
    // c.630 separa las raíces flexionadas (desnud/eroti/masturb) de las palabras
    // completas: aquéllas usan `\b` inicial sin `\b` final (ver ContentModeration).
    private val sexual = ContentModeration.ModerationRule(
        stem = Regex("""\b(sexo|sexual|porno|xxx|culos|tetas|pene|vagina|orgasmo)\b"""),
        contain = listOf(
            Regex("""\b(cita con el ur[oó]logo|cita con la ginec[oó]loga?)\b[^.]*\bpene\b"""),
            Regex("""\b(revisi[oó]n de|revisar la|revisar el|examen de la|examen del)[^.]*\b(pene|vagina)\b""")
        ),
        proximity = Regex("""\b(ur[oó]logo|ginec[oó]log[oa]|sex[oó]logo|prostate|m[ée]dico|cl[íi]nica|farmac[ée]utic[oa])\b""")
    )
    private val sexualStems = ContentModeration.ModerationRule(
        stem = Regex("""\b(desnud|eroti|masturb)"""),
        proximity = Regex("""\b(ur[oó]logo|ginec[oó]log[oa]|sex[oó]logo|prostate|m[ée]dico|cl[íi]nica|farmac[ée]utic[oa])\b""")
    )
    private val violencia = ContentModeration.ModerationRule(
        stem = Regex("""\b(matar|asesinar|violar|bomba|amenaza|escopeta|pistola|cuchill)\b"""),
        contain = listOf(
            Regex("""\bmatar\b\s+(el|la|los|las|un|una)?\s*(proceso|hilo|servicio|servidor|demonio|sesi[oó]n|tarea|job|zombie)\b"""),
            Regex("""\bviolar\b\s+(la|el|una|un|las|los)?\s*(pol[ií]tica|contrato|licencia|restricci[oó]n|norma|ley|clausula|cl[áa]usula|t[ée]rminos?)\b"""),
            Regex("""\b(modelo|m[oó]delo)\s+de\s+amenaza\b"""),
            Regex("""\bamenaza\b\s+(de)?\s*(de\s+integridad|de\s+seguridad|de\s+modelo)\b"""),
            Regex("""\b(bomba|pistola|escopeta)\s+de\s+agua\b"""),
            Regex("""\b(matar|asesinar)\s+(un|el)\s+proceso\b""")
        )
    )
    private val secuestroStems = ContentModeration.ModerationRule(
        stem = Regex("""\bsecuestr"""),
        contain = listOf(
            Regex("""\b(revisi[oó]n|revisar|diagn[oó]stico|diag|audit|auditor[íi]a)\s+(de[l]?)\s*secuestro\b"""),
            Regex("""\bsecuestro\s+de\s+(dns|sesi[oó]n|cookie|token|sesiones?)\b""")
        )
    )
    private val drogas = ContentModeration.ModerationRule(
        stem = Regex("""\b(droga|cocaina|heroina|marihuana|metanfetamina|narcotrafico)\b"""),
        proximity = Regex("""\b(farmac[ée]utic[oa]|farmacia|recetad[oa]|m[ée]dic[oa]|medicament[oa]|ur[oó]log[oa]|receta|tratamiento|recetar)\b""")
    )

    private fun sexualHarmful(text: String) = ContentModeration.isHarmful(text, sexual)
    private fun sexualStemHarmful(text: String) = ContentModeration.isHarmful(text, sexualStems)
    private fun violentHarmful(text: String) = ContentModeration.isHarmful(text, violencia)
    private fun secuestroHarmful(text: String) = ContentModeration.isHarmful(text, secuestroStems)
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
        // c.630: `secuestr` ahora SÍ casa "secuestro"; la exención de colocación
        // ("revisar el secuestro de DNS") legitima el sentido técnico.
        assertFalse(secuestroHarmful("revisar el secuestro de DNS del servicio"))
    }

    @Test fun secuestroDeSesion_noEsDanino() {
        assertFalse(secuestroHarmful("auditar el secuestro de sesion del login"))
    }

    // ── Stems muertos por `\b` final en VIOLENCIA (c.630): `secuestr` no casaba
    //    secuestrar/secuestraron → PASABAN el gate. Ahora SÍ. ──

    @Test fun secuestrarAlVecino_esDanino() {
        // PRE-fix RED: `\bsecuestr\b` no casa "secuestrar" → PASABA (amenaza no bloqueada).
        assertTrue(secuestroHarmful("secuestrar al vecino esta noche"))
    }

    @Test fun secuestraronAHermano_esDanino() {
        assertTrue(secuestroHarmful("secuestraron a mi hermano en la carretera"))
    }

    @Test fun secuestroDeNino_esDanino() {
        // "secuestro de un niño" → stem casa, la exención DNS no aplica → bloqueado.
        assertTrue(secuestroHarmful("investigar el secuestro de un niño"))
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

    // ── Stems rotos por `\b` final (c.630): desnud/eroti/masturb no casaban
    //    desnudo/erotico/masturbacion (transición word→word). Ahora sí. ──

    @Test fun fotosDeDesnudos_esDanino() {
        // PRE-fix RED: `\bdesnud\b` no casa "desnudos" → PASABA (contenido explícito no bloqueado).
        assertTrue(sexualStemHarmful("ver fotos de desnudos en el móvil"))
    }

    @Test fun contenidoErotico_esDanino() {
        assertTrue(sexualStemHarmful("buscar contenido erotico en la red"))
    }

    @Test fun masturbacion_esDanina() {
        assertTrue(sexualStemHarmful("videos de masturbacion diaria"))
    }

    @Test fun chicasDesnudas_esDanino() {
        assertTrue(sexualStemHarmful("chicas desnudas en la cam"))
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
