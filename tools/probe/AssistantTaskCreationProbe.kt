import com.ordia.app.assistant.AssistantAction
import com.ordia.app.assistant.AssistantEngine
import java.time.ZoneId
import kotlin.system.exitProcess

// Sonda de descubrimiento PERSISTENTE (c.986): ronda NUEVA sobre la familia
// cotidiana de CREACIÓN de tareas/recordatorios en el asistente — la hermana
// de la captura de notas (c.969…c.985, familia «-melo» AGOTADA). El asistente
// responde consultas (agenda, conteos, búsqueda) y captura NOTAS, pero la
// frase más cotidiana de un asistente personal («recuérdame llamar al banco
// mañana») se medía aquí: PRE (HEAD 173a74b) 9/10 candidatas caían al MENÚ
// genérico — mentira por omisión (la capacidad de crear tareas YA existe:
// vm.addSmartTask → NaturalTaskParser, la misma captura rápida).
// c.986 cerró «recuérdame <contenido>» (± tilde, ± «:») → CREATE_TASK
// (AssistantEngineRecuerdameCaptureTest, 12 tests). c.990 cerró la lateral
// (a) «crea/añade/agrega (una) tarea…» → CREATE_TASK
// (AssistantEngineCreaTareaCaptureTest, 17 tests; pelada y conector pelado
// «de» → guía honesta SIN acción). c.991 cerró la lateral (e) «ponme un
// recordatorio…» → CREATE_TASK (AssistantEnginePonmeRecordatorioCaptureTest,
// 13 tests; robo de rama por la consulta c.808 resuelto: el imperativo de
// creación se evalúa ANTES; pelada → guía honesta SIN acción). Fixture
// vacío: el routing correcto no depende del dato.
// Formato heredado (c.803-b): regresiones fallan (exit 1) ante GAP nuevo;
// laterales abiertas se imprimen toleradas hasta su ciclo.
fun main() {
    val now = 1753495200000L
    val zone = ZoneId.of("America/Bogota")

    // CERRADAS c.986 (regresión: deben seguir rutando a CREATE_TASK).
    val closed = listOf(
        "recuérdame llamar al banco mañana",
        "recuérdame pagar la luz el viernes",
        "recuérdame comprar leche",
        "recuerdame llamar a Ana", // escritura móvil sin tilde
        "recuérdame: sacar al perro", // simetría «:» con notas
        // CERRADAS c.990 (lateral (a)): imperativo explícito de tarea.
        "crea una tarea: llamar a Ana",
        "crear tarea pagar la luz mañana",
        "añade una tarea: sacar al perro",
        "agrega una tarea llamar al dentista",
        // CERRADAS c.991 (lateral (e)): «ponme un recordatorio…» — ya no la
        // roba la consulta c.808.
        "ponme un recordatorio para mañana llamar al banco",
        // CERRADAS c.993 (lateral (c)): «recuérdame que…» — despoje del
        // «que» subordinado (título sin residuo).
        "recuérdame que tengo que llamar al banco",
        // CERRADA c.994 (lateral (b1)): «avísame…» — recordatorio
        // declarativo (temporal intercalado reordenado al payload).
        "avísame mañana de llamar al banco"
    )

    // LATERALES ABIERTAS (documentadas, toleradas hasta su ciclo — doctrina
    // anti-overreach UNA forma por ciclo):
    //  (a) CERRADA c.990: «crea/añade/agrega (una) tarea…» → CREATE_TASK;
    //  (b) CERRADA c.994 (b1): «avísame…» — recordatorio declarativo al
    //      menú genérico (mentira por omisión). Resuelta con rama
    //      avisaMeCapture hermana de remindMeCapture (despoje del «de»,
    //      temporal intercalado reordenado al payload); pins en guards
    //      (pelada, negación tras «de», evento «cuando»). ABIERTA (b2):
    //      «quiero que me recuerdes…» — recordatorio envuelto;
    //  (c) CERRADA c.993: «recuérdame que…» — el «que» subordinado quedaba
    //      en el título; la pelada-con-«que» creaba tarea BASURA «que» y la
    //      negación tras «que» capturaba lo contrario. Resuelta con despoje
    //      LEADING_QUE ANTES de los checks; pin en guards (exit 1 si
    //      reaparece la tarea basura o la captura de la negación);
    //  (d) «recuérdamelo» — deíctico sin contenido explícito;
    //  (e) CERRADA c.991: «ponme un recordatorio…» — el ROBO DE RAMA por la
    //      consulta de recordatorios c.808 («No tienes recordatorios
    //      programados» a una orden de CREAR) se resolvió evaluando la
    //      captura ANTES de la consulta (setReminderCapture).
    //  (f) CERRADA c.992: «recuérdame:» pelada con «:» — creaba tarea BASURA
    //      «:» (medido c.990: payload ":"); el (.+) se tragaba el propio «:»
    //      en REMIND_ME_WITH_CONTENT. Resuelta con extractor ([^:].*);
    //      pin en guards (exit 1 si reaparece).
    val openLaterals = listOf(
        "quiero que me recuerdes pagar la luz"
    )

    // GUARDS: no son imperativos de creación; NO deben capturar CREATE_TASK.
    val guards = listOf(
        "no me recuerdes nada", // negación previa
        "recuerdo la tarea de ayer", // afirmación en pasado
        "el recuerdo llegó ayer", // sustantivo
        "recuérdame no llamar al banco", // contenido negado (anti-overreach)
        "recuérdame:", // pelada CON «:» — c.992: NUNCA tarea basura «:»
        "recuérdame que", // pelada-con-«que» — c.993: NUNCA tarea basura «que»
        "recuérdame que no llame a ana", // negación tras «que» — c.993: NUNCA capturar lo contrario
        "avísame", // pelada — c.994: guía honesta, NUNCA tarea vacía
        "avísame de no llamar al banco", // negación tras «de» — c.994: NUNCA capturar lo contrario
        "avísame cuando llegue Ana" // evento condicional — c.994: no programable, NUNCA capturar
    )

    // REGRESIONES hermanas: notas c.969…c.985 + recordatorios c.808 +
    // agenda + what-now (si alguna cae al menú → exit 1).
    val regressions = listOf(
        "apúntamelo: comprar pan",
        "toma nota: comprar pan",
        "qué recordatorios tengo",
        "qué tengo hoy",
        "qué hago ahora"
    )

    fun isMenu(text: String) =
        text.startsWith("Puedo organizar tu día") || text.startsWith("Puedo organizar tu dia")

    var unexpected = 0

    println("=== CERRADAS c.986+c.990+c.991 (regresión CREATE_TASK) ===")
    for (p in closed) {
        val ans = AssistantEngine.answer(p, emptyList(), emptyList(), emptyList(), now, zone)
        if (ans.action != AssistantAction.CREATE_TASK) {
            unexpected++
            println("[REGRESIÓN] $p -> ${ans.action} | ${ans.text.take(80)}")
        } else println("[ok] $p -> CREATE_TASK | ${ans.actionPayload}")
    }

    println("=== LATERALES ABIERTAS (toleradas) ===")
    for (p in openLaterals) {
        val ans = AssistantEngine.answer(p, emptyList(), emptyList(), emptyList(), now, zone)
        val state = when {
            ans.action == AssistantAction.CREATE_TASK -> "[CERRADA — retirar de laterales]"
            isMenu(ans.text) -> "[GAP al menú]"
            else -> "[otra rama]"
        }
        println("$state $p -> ${ans.action} | ${ans.text.take(80)}")
    }

    println("=== GUARDS (nunca CREATE_TASK) ===")
    for (p in guards) {
        val ans = AssistantEngine.answer(p, emptyList(), emptyList(), emptyList(), now, zone)
        if (ans.action == AssistantAction.CREATE_TASK) {
            unexpected++
            println("[GUARD ROTA] $p -> CREATE_TASK | ${ans.actionPayload}")
        } else println("[ok] $p -> ${ans.action} | ${ans.text.take(80)}")
    }

    println("=== REGRESIONES hermanas ===")
    for (p in regressions) {
        val ans = AssistantEngine.answer(p, emptyList(), emptyList(), emptyList(), now, zone)
        if (isMenu(ans.text)) { unexpected++; println("[GAP inesperado] $p") }
        else println("[ok] $p -> ${ans.action}")
    }

    println("=== RESUMEN: cerradas ${closed.size}, laterales ${openLaterals.size}, inesperados $unexpected ===")
    if (unexpected > 0) exitProcess(1)
}
