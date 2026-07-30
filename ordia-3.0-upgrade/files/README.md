# Ordia 3.0

Ordia 3.0 integra la base organizativa local-first, guardianes virtuales y atención contextual privada.

# Ordia 2.0

Ordia es un sistema personal Android, local primero, que conecta tareas, proyectos, planificación, notas, hábitos, rutinas, enfoque y un guardián virtual.

## Incluido

- rediseño de inicio, tareas, estadísticas, componentes y navegación;
- seis especies y cinco etapas de guardianes;
- personalidad, energía, vínculo, estados, refugio e interacciones;
- progreso derivado de actividad real y resistente a doble contabilización;
- mascota flotante arrastrable, accesible y respetuosa de horas silenciosas;
- backup versión 3 con validación completa, preferencias y recordatorios reconstruidos;
- canal sideload con actualización gestionada y verificación de APK privada;
- variante release/tienda sin permisos ni componentes del autoactualizador;
- GitHub Actions con permisos mínimos, Actions fijadas a SHA y firma estable obligatoria;
- aplicador con respaldo, rollback temprano y preservación de builds válidos;
- funcionamiento local sin cuenta obligatoria.

## Compilar

```bash
./gradlew clean test lintDebug assembleDebug --stacktrace
```

APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Documentación

- `docs/ORDIA_2_DEEP_AUDIT.md`
- `docs/GUARDIANS.md`
- `docs/AUTO_UPDATES.md`
- `docs/ORDIA_2_IMPLEMENTATION.md`
- `docs/ORDIA_AUDIT.md`

## Requisitos

- JDK 17
- Android SDK 36
- Gradle 8.13
- Android Gradle Plugin 8.13.2
