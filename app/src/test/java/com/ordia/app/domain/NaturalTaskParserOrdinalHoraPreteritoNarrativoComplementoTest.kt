package com.ordia.app.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1023: ordinal de hora con conector «a» (sin artículo) TRAS una cláusula
 * narrativa en pretérito con COMPLEMENTO («me quedé dormido a primera hora»,
 * «sonó la alarma a primera hora», «fui al banco a primera hora») — la
 * lateral registrada FUERA en el test de c.1016 («pretérito + complemento
 * no es predicado SOLO»), heredera directa del guard H4.
 * Medida PRE con sonda efímera `/tmp/probe1017/Probe.kt`+`Probe2.kt` (motor
 * real vía `tools/run_probe.sh`, now=domingo 2026-08-23 12:00
 * America/Santo_Domingo, HEAD `9920a228` [mi c.1016, PUSHED]): 13/13
 * candidatas con DOBLE daño P1 — fecha FALSA ya PASADA (hoy 09:00/18:00:
 * compromiso vencido al nacer que ensucia What Now) y título MUTILADO
 * («me quedé dormido» sin su marca temporal). Guards ancla correctos
 * (encargo puro «avisar…», compromiso embebido «avisé a Juan de llamar…»,
 * subordinada «me pidió que llamara…», sujeto primero «la alarma que
 * sonó…», ambigua 1ª plural «salimos…», «tengo que salir…»), regresiones
 * c.1016 intactas, pines FUERA medidos («…del lunes…», «salí a comprar
 * pan…» [infinitivo embebido], «el lunes sonó la alarma…» [weekday
 * primero], «llegué ayer…» [ancla «ayer»]). El pin «a la primera hora
 * llegó…» (artículo tras «a») quedó RESUELTO por el delta c.1019 del
 * hermano en la UNIÓN — re-pin MÁS estricto como narrativa abajo
 * (precedente c.957/c.965/c.1016).
 * Fix mínimo (1 punto): extensión H5 de la rama prefijo de
 * [ordinalHoraOccurrenceIsPreteriteNarrative] — el prefijo es CONTENIDO
 * narrativo si ABRE con pretérito inequívoco (clítico opcional + la misma
 * [preteriteNarrativeVerbAlternation] de c.950/c.1016) Y su complemento NO
 * contiene infinitivo ni subordinador «que» (anti-compromiso-embebido:
 * «avisé a Juan de llamar al banco…» conserva su ancla real — doctrina
 * c.950: la ambigüedad se resuelve SIEMPRE a favor de no suprimir un
 * compromiso). Fecha, título y parte del día gobernada comparten el mismo
 * predicado (nunca divergen, doctrina c.930/c.950).
 * Limitaciones conservadoras registradas (no-bloqueo narrativo por falso
 * infinitivo: «ayer», «lugar»…): laterales FUERA pineadas abajo.
 * Determinista (regex), cero random, cero IA fingida, cero UI.
 */
class NaturalTaskParserOrdinalHoraPreteritoNarrativoComplementoTest {

    private val zone: ZoneId = ZoneId.of("America/Santo_Domingo")
    private val now = LocalDate.of(2026, 8, 23).atTime(LocalTime.NOON)
        .atZone(zone).toInstant().toEpochMilli()

    private fun parse(text: String) = NaturalTaskParser.parse(text, now, zone)

    private fun dueAt(date: LocalDate, time: LocalTime) =
        date.atTime(time).atZone(zone).toInstant().toEpochMilli()

    private val domingo = LocalDate.of(2026, 8, 23)
    private val lunes = LocalDate.of(2026, 8, 24)
    private val sabado = LocalDate.of(2026, 8, 22)
    private val nueve = LocalTime.of(9, 0)
    private val dieciocho = LocalTime.of(18, 0)

    // ---------- capturas: narrativa íntegra (due null + título intacto) ----------

    private fun assertNarrativaIntacta(text: String) {
        val p = parse(text)
        assertNull("due debe ser null: $text", p.dueAt)
        assertEquals("título intacto: $text", text, p.title)
    }

    @Test fun meQuedeDormidoAPrimeraHora() = assertNarrativaIntacta("me quedé dormido a primera hora")
    @Test fun sonoLaAlarmaAPrimeraHora() = assertNarrativaIntacta("sonó la alarma a primera hora")
    @Test fun llegoElCarteroAPrimeraHora() = assertNarrativaIntacta("llegó el cartero a primera hora")
    @Test fun meDesperteTardeAPrimeraHora() = assertNarrativaIntacta("me desperté tarde a primera hora")
    @Test fun cerroLaTiendaAUltimaHora() = assertNarrativaIntacta("cerró la tienda a última hora")
    @Test fun empezoLaReunionAPrimeraHora() = assertNarrativaIntacta("empezó la reunión a primera hora")
    @Test fun seRompioLaTuberiaAPrimeraHora() = assertNarrativaIntacta("se rompió la tubería a primera hora")
    @Test fun meQuedeDormidoAUltimaHora() = assertNarrativaIntacta("me quedé dormido a última hora")
    @Test fun llegueTardeAPrimeraHora() = assertNarrativaIntacta("llegué tarde a primera hora")
    @Test fun pasoElCamionAPrimeraHora() = assertNarrativaIntacta("pasó el camión a primera hora")
    @Test fun fuiAlBancoAPrimeraHora() = assertNarrativaIntacta("fui al banco a primera hora")
    @Test fun estuveEnElMedicoAPrimeraHora() = assertNarrativaIntacta("estuve en el médico a primera hora")
    @Test fun vineTempranoAPrimeraHora() = assertNarrativaIntacta("vine temprano a primera hora")
    @Test fun hableConMariaAPrimeraHora() = assertNarrativaIntacta("hablé con María a primera hora")
    @Test fun hableConElMedicoAUltimaHora() = assertNarrativaIntacta("hablé con el médico a última hora")

    // ---------- guards: encargo / compromiso embebido conservan el ancla ----------

    private fun assertAncla(text: String, due: Long, title: String) {
        val p = parse(text)
        assertEquals("due ancla: $text", due, p.dueAt)
        assertEquals("título ancla: $text", title, p.title)
    }

    @Test fun guardAvisarAPrimeraHora() =
        assertAncla("avisar a primera hora", dueAt(domingo, nueve), "avisar")

    @Test fun guardLlamarAlBancoAPrimeraHora() =
        assertAncla("llamar al banco a primera hora", dueAt(domingo, nueve), "llamar al banco")

    @Test fun guardAviseAJuanDeLlamarAlBanco() =
        assertAncla(
            "avisé a Juan de llamar al banco a primera hora",
            dueAt(domingo, nueve),
            "avisé a Juan de llamar al banco"
        )

    @Test fun guardMePidioQueLlamaraAlBanco() =
        assertAncla(
            "me pidió que llamara al banco a primera hora",
            dueAt(domingo, nueve),
            "me pidió que llamara al banco"
        )

    @Test fun guardRecordarLoQuePaso() =
        assertAncla("recordar lo que pasó a primera hora", dueAt(domingo, nueve), "recordar lo que pasó")

    @Test fun guardLaAlarmaQueSono() =
        assertAncla("la alarma que sonó a primera hora", dueAt(domingo, nueve), "la alarma que sonó")

    @Test fun guardSalimosAmbigua() =
        assertAncla("salimos a primera hora", dueAt(domingo, nueve), "salimos")

    @Test fun guardTengoQueSalir() =
        assertAncla("tengo que salir a primera hora", dueAt(domingo, nueve), "tengo que salir")

    @Test fun guardQuedeConAnaEsCita() =
        assertAncla("quedé con Ana a primera hora", dueAt(domingo, nueve), "quedé con Ana")

    @Test fun guardYaQuedeConAnaEsCita() =
        assertAncla("ya quedé con Ana a primera hora", dueAt(domingo, nueve), "quedé con Ana")

    @Test fun guardQuedeEnLlamarAlBanco() =
        assertAncla("quedé en llamar al banco a primera hora", dueAt(domingo, nueve), "quedé en llamar al banco")

    // ---------- regresiones c.1016 (H4 cubierto, no deben cambiar) ----------

    @Test fun regresionAPrimeraHoraLlegoElCartero() =
        assertNarrativaIntacta("a primera hora llegó el cartero")

    @Test fun regresionLlegueAPrimeraHora() =
        assertNarrativaIntacta("llegué a primera hora")

    @Test fun regresionMeDesperteAPrimeraHora() =
        assertNarrativaIntacta("me desperté a primera hora")

    @Test fun regresionAUltimaHoraDeLaNocheCerro() =
        assertNarrativaIntacta("a última hora de la noche cerró la tienda")

    // ---------- pins FUERA (laterales, byte-idénticos) ----------

    // Re-pin legítimo MÁS estricto (precedente c.957/c.965/c.1016): la lateral
    // artículo tras «a» quedó RESUELTA por el delta c.1019 del hermano (rama
    // «a la/las» del guard comparte la misma evidencia de pretérito adyacente)
    // → narrativa intacta (due=null, título íntegro).
    @Test fun pinArticuloTrasConector_resueltoC1019RePin() =
        assertNarrativaIntacta("a la primera hora llegó el cartero")

    @Test fun pinWeekdayGenitivo() =
        assertAncla("a primera hora del lunes llegó el paquete", dueAt(lunes, nueve), "llegó el paquete")

    @Test fun pinInfinitivoEmbebidoEnNarrativa() =
        assertAncla("salí a comprar pan a primera hora", dueAt(domingo, nueve), "salí a comprar pan")

    @Test fun pinWeekdayAntesDeClausulaNarrativa() =
        assertAncla("el lunes sonó la alarma a primera hora", dueAt(domingo, nueve), "el lunes sonó la alarma")

    @Test fun pinAyerConOrdinal() =
        assertAncla("llegué ayer a primera hora", dueAt(sabado, nueve), "llegué")

    @Test fun regresionSalirAPrimeraHoraManana() =
        assertAncla("salir a primera hora mañana", dueAt(lunes, nueve), "salir")
}
