#!/usr/bin/env python3
"""c.1252 — documenta auditoría clase XXXVI belleza/cuidado personal en AI_AUTONOMY."""
from pathlib import Path

# 1) BACKLOG: ledger + sección de auditoría
p = Path("AI_AUTONOMY/BACKLOG.md")
t = p.read_text(encoding="utf-8")
ledger = (
    "c.1252 AUDITORÍA-EN-CURSO (este lado — clase TRIGÉSIMA SEXTA [XXXVI] CUIDADO PERSONAL/"
    "BELLEZA; DISJUNTO del hermano c.1251 «afinar-instrumento»). Sonda persistida "
    "`tools/probe/BellezaClassXXXVIProbe.kt`. CERO producto (descubrimiento).\n"
)
t = ledger + t
seccion = (
    "### 🔒 AUDIT-EN-CURSO — c.1252 (este lado, OpenHands): AUDITORÍA clase XXXVI CUIDADO "
    "PERSONAL/BELLEZA\n\n"
    "Unidad: auditoría de DESCUBRIMIENTO de la clase TRIGÉSIMA SEXTA [XXXVI]: CUIDADO "
    "PERSONAL/BELLEZA dichas como se hablan (peluquería, barbería, salón, corte de pelo, "
    "peinar, tinte, manicura, pedicura, uñas, cejas, depilación, cera, barba, tratamiento "
    "facial) — dominio fresco: clases I–XXXV ya cubiertas; hermano ocupado en c.1251 "
    "«afinar-instrumento» (DISJUNTO). CERO producto (descubrimiento, convención "
    "c.1127/c.1165/c.1194/c.1225/c.1227). Sonda persistida "
    "`tools/probe/BellezaClassXXXVIProbe.kt` (14 candidatas + 8 guards + 8 regresiones) "
    "medida PRE sobre HEAD `f17986f` (contiene c.1250 clase-fitness + docs hermanos).\n\n"
    "Resultados medidos (sonda real vía `tools/run_probe.sh`, motor "
    "`ContextIntentEngine.analyze`):\n\n"
    "- **1/14 HIT-heredado APPOINTMENT**: «cita con el barbero el sábado» (título «Cita con "
    "el barbero», dueAt) — cubierto por la fórmula genérica «cita con <término>» del área "
    "APPOINTMENT (familia de «cita con el médico»). Lección lavar-coche c.1223: mide antes "
    "de abrir.\n"
    "- **13/14 NULL medidos, familias por keyword/objeto** (laterales ABIERTAS, UNA por "
    "ciclo, anti-overreach):\n"
    "  - (a) «peluquería/barbería/salón + (weekday|temporal)» — **FUERTE**: la cita de "
    "peluquería es el compromiso de belleza canónico del español hablado (olvido social y "
    "ancias real; «cita en la peluquería el viernes» y «peluquería el martes» ya hablan de "
    "compromiso y suenan a VAGA pese a ser deterministas). Vía: keyword-OBJETO monosemántica "
    "«peluquería|barbería|salón (de belleza)» acotada con piso nominal (precedente «partido» "
    "c.1231 / «clase-fitness» c.1250; gate c.751 CERO keywords nuevas si piso nominal "
    "nominal); «corte de pelo» anexa).\n"
    "  - (b) (manicura|pedicura|uñas|cejas) — **MEDIA**: objeto-acotado, igual gate-DOS "
    "puntos (piso nominal con temporal/ancla).\n"
    "  - (c) (depilación|cera) — **MEDIA**: nominal-acotado; guard cera-inerte («la cera "
    "para el malt)/(cera de parafina)) registrada.\n"
    "  - (d) (barba: recortar|afeitar|arreglar la barba) — **DÉBIL**: verbo acotado al "
    "objeto; pretérito/negación ya guardan.\n"
    "  - (e) (tinte|tratamiento facial) — **DÉBIL**: tinte nominal-acotado; tratamiento "
    "facial EVITA dominio médico (tratamiento de <enfermedad> guard).\n"
    "- **8/8 guards NULL correctos**: negación «no voy a la peluquería mañana», pretérito "
    "«fui al barbero ayer», pretérito copulativo «la manicura fue el lunes», declarativa "
    "«el corte de pelo se canceló» / «el salón de belleza está cerrado», hablar «habla del "
    "corte de pelo», sustantivo solo «el tinte», 3a persona «mi hermana se hace la manicura "
    "los lunes».\n"
    "- **8/8 regresiones HIT estables**: recuérdame TASK, médico APPOINTMENT, leche SHOPPING, "
    "llamar CALL, luz PAYMENT, perro HOUSEHOLD, yoga/gimnasio EXERCISE (gato del "
    "cierre c.1226 intacto).\n"
    "- **Verificación**: suite UNIÓN medida en este run (bits de producto idénticos — la "
    "sonda no toca dominio): **OK (10242, exit 0)**; smoke dominio 25/25; probe "
    "compilable. Determinista (regex), cero random, cero IA fingida, cero UI. "
    "**NO VERIFICADO** Android/gradle/lint/assemble/UI/Room DAOs reales (sin SDK).\n\n"
    "Laterales ABIERTAS (a)–(e) UNA por ciclo; primer-marcador-gana (c.1077); nunca force, "
    "nunca main. Próxima: (a) peluquería/salón FUERTE (o toma que diga el hermano).\n"
)
t = t + "\n" + seccion
p.write_text(t, encoding="utf-8")

# 2) RUN_LOG append
p = Path("AI_AUTONOMY/RUN_LOG.md")
t = p.read_text(encoding="utf-8")
entry = """
## Run c.1252 (este lado, 2026-08-26) — AUDITORÍA clase XXXVI CUIDADO PERSONAL/BELLEZA

- HEAD inicial: `f17986f` (post-push c.1250 close + marker c.1252 EN CURSO).
- Problema: medir la clase XXXVI belleza/cuidado personal (peluquería, barbería, manicura, uñas, cejas, depilación, cera, barba, tinte, tratamiento facial) — descubrimiento puro, CERO producto. Prioridad descubrimiento (área parser longitudinal).
- Sonda persistida `tools/probe/BellezaClassXXXVIProbe.kt`: 14 candidatas + 8 guards + 8 regresiones, PRE sobre HEAD.
- MEDIDA: 1/14 HIT-heredado APPOINTMENT («cita con el barbero» via «cita con» genérica — similar a R2 médico). 13/14 NULL familia: (a) peluquería/barbería/salón FUERTE; (b) manicura|pedicura|uñas|cejas MEDIA; (c) depilación|cera MEDIA; (d) barba DÉBIL; (e) tinte|tratamiento facial DÉBIL.
- Guards 8/8 NULL (negación, pretérito, pretérito-copulativo, declarativa, hablar, sustantivo, 3a persona).
- Regresiones 8/8 HIT (recuérdame TASK, médico APPOINTMENT, leche SHOPPING, llamar CALL, luz PAYMENT, perro HOUSEHOLD, yoga/gimnasio EXERCISE).
- Vía plan abiertas de gate c.751: keyword-OBJETO nominal-acotado (precedente «partido» c.1231 / «clase-fitness» c.1250), CERO keywords nuevas; título arranca en el nominal/objeto (paridad matchPartido/matchClase). Locks (a)–(e) UNA por ciclo.
- Verificación enclosure: suite UNIÓN OK (10242, exit 0) re-medida sobre el mismo árbol de producto; smoke 25/25; probe compila.
- Determinista (regex), cero random, cero IA fingida, cero UI. **NO VERIFICADO** Android/gradle/lint (sin SDK). Nunca force, nunca main.
- Próxima prioridad: (a) «peluquería/salón» FUERTE o toma del hermano; luego (b)–(e) en orden.
- HEAD final: commit docs(ai) c.1252 (siguiente).
"""
p.write_text(t + entry, encoding="utf-8")
print("OK: backlog + run_log")
