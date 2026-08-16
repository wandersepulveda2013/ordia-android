# Auditoría adversarial de Ordia 2.0

Fecha de cierre: 30 de julio de 2026.

## Alcance

La revisión cubrió código Kotlin incorporado por el lote, manifiestos por variante, Gradle, GitHub Actions, instalador PowerShell, restauración de datos, recordatorios, DataStore, guardián virtual, servicio flotante, descarga e instalación de APK, integridad del paquete y documentación.

La compilación Android definitiva se ejecuta en el repositorio completo mediante:

```text
gradlew clean test lintDebug assembleDebug --stacktrace
```

## Resultado ejecutivo

La auditoría anterior mejoró sustancialmente el lote, pero todavía quedaban rutas capaces de causar pérdida de datos, instalar bytes antes de validarlos o dejar un repositorio correcto en un estado peor por un fallo tardío de red. Esta revisión corrige esas rutas y añade pruebas negativas ejecutables.

## Hallazgos críticos corregidos

1. **Notificación nativa de DownloadManager abría una APK sin validar.** La descarga ya no publica una notificación de “completada” que Android pueda abrir directamente. Solo Ordia muestra la acción de instalación después de copiar y verificar la APK en almacenamiento privado.
2. **Copia incompleta podía vaciar Room.** Todas las colecciones obligatorias, preferencias, tipos, relaciones, límites y fechas se validan antes de ejecutar un solo `deleteAll()`.
3. **JSON válido solo en apariencia.** Se rechazan objetos raíz múltiples, datos posteriores, claves duplicadas —incluidas claves equivalentes mediante `\uXXXX`—, profundidad excesiva, UTF-8 malformado y sustitutos Unicode aislados.
4. **Restauraciones simultáneas.** Exportación e importación están serializadas mediante `Mutex`; dos restauraciones no pueden borrar e insertar datos de forma intercalada.
5. **Recordatorios inconsistentes después de restaurar.** Se espera la cancelación de WorkManager antes de reprogramar únicamente tareas futuras, pendientes y no archivadas.
6. **Servicio flotante iniciado desde segundo plano.** `OrdiaApplication` nunca inicia el foreground service. La reconciliación ocurre desde una `MainActivity` visible, evitando restricciones de Android recientes.
7. **Autoactualizador presente en la variante de tienda.** `INTERNET`, `REQUEST_INSTALL_PACKAGES`, FileProvider, receptor y actividad de instalación existen solo en el manifiesto `debug` destinado a distribución directa. La variante `release` declara `SELF_UPDATE_ENABLED=false`.
8. **Archivo cambiado entre verificación e instalación.** Ordia copia la APK a `files/verified-updates`, vuelve a validar hash, paquete, `versionCode` y certificado sobre esos bytes privados y solo entonces genera un URI temporal.

## Hallazgos altos corregidos

9. **Metadatos de descarga no confirmados.** La escritura usa `commit()` y, si falla, elimina la descarga huérfana.
10. **Tamaño publicado no persistido.** Se conserva y compara el tamaño anunciado, el reportado por DownloadManager y el realmente copiado.
11. **Selección ambigua por mayúsculas.** La Release debe contener exactamente `Ordia-2.0-code-N.apk`; variantes de mayúsculas o duplicados confundibles se rechazan.
12. **Checksum flexible en exceso.** Se acepta una única línea canónica `hash␠␠archivo`; rutas, formato con asterisco, líneas adicionales y nombres distintos se rechazan.
13. **Firma incompatible.** Se compara el historial de certificados de la APK candidata con la instalación actual, además del paquete y `versionCode` exactos.
14. **Progreso del guardián cultivable.** La experiencia se sincroniza de forma monotónica desde registros reales; completar–desmarcar–completar no crea XP adicional reconstruible.
15. **Interacciones sustituyendo productividad.** La bonificación de vínculo está limitada y no permite alcanzar las etapas superiores por toques repetidos.
16. **DataStore corrupto.** Existe `ReplaceFileCorruptionHandler`, recuperación de errores de lectura y validación de nombres, eventos y Unicode del guardián.
17. **Firma remota no garantizada.** El workflow de `main` falla si falta cualquiera de los cuatro secretos y el aplicador no hace push hasta confirmarlos.
18. **Cálculo diferente de `versionCode`.** Gradle y Bash validan el intento 1–99, usan la misma fórmula de 64 bits y rechazan valores superiores al límite entero de Android.

## Hallazgos medios corregidos

19. **Intent compartido repetido tras recrear la actividad.** Acción y extras se consumen una sola vez.
20. **Overlay ignoraba accesibilidad.** Respeta “Reducir movimiento”, horas silenciosas, TalkBack y límites seguros de pantalla.
21. **Navegación de primer nivel dentro de detalles.** Barra inferior y rail desaparecen en editores y pantallas de detalle.
22. **Adjuntos inseguros al restaurar.** Solo se admiten `content://` jerárquicos, sin fragmentos, con propietario existente y MIME válido.
23. **Relaciones inconsistentes.** Se rechazan IDs duplicados, proyectos inexistentes, ciclos de subtareas, enlaces de etiquetas inválidos, posiciones repetidas y sesiones temporalmente imposibles.
24. **Aplicador bloqueado por GitHub CLI.** Cada escritura de secreto tiene un timeout de 60 segundos y mata el proceso si no responde.
25. **Rollback destructivo tras un fallo tardío.** Una compilación válida o un commit publicado nunca se revierte por fallos posteriores de GitHub, ADB o Explorer. Antes del build válido, sí se restaura el SHA original.
26. **Stash mezclado con cambios no confirmados.** Si el build pasa pero el commit falla, el stash anterior se conserva sin aplicarlo encima de un árbol modificado.
27. **Remoto equivocado.** Secretos y push solo operan contra `wandersepulveda2013/ordia-android` mediante una lista cerrada de URLs.
28. **Actions referenciadas por etiquetas mutables.** Checkout, Java, Gradle, upload y download artifact están fijadas a SHA oficiales verificados.
29. **Reejecución que sobrescribía una Release.** La publicación es inmutable: si el tag ya existe, el workflow falla en vez de reemplazar bytes.
30. **Paquete con enlaces simbólicos.** El validador los rechaza y exige cobertura SHA-256 exacta de todos los archivos regulares distribuidos.

## Mejoras adicionales

- copia de seguridad versión 3 con preferencias y progreso del guardián;
- límites por colección, total de registros y tamaño UTF-8;
- validación previa completa antes de tocar Room o DataStore;
- compensación de preferencias si la transacción Room falla;
- limpieza de APK privadas y descargas obsoletas;
- descargas periódicas sin redes medidas;
- pruebas puras ejecutables para guardián, JSON, UTF-8, URLs, nombres y checksums;
- análisis de XML de `main` y `debug`, YAML, delimitadores Kotlin y PowerShell;
- acciones de CI con credenciales de checkout deshabilitadas.

## Riesgos residuales reales

1. El paquete contiene archivos de reemplazo, no el repositorio completo ni Android SDK; por eso la compilación integral solo puede confirmarse en el equipo que conserva el proyecto.
2. La primera transición desde una APK firmada con otra clave puede exigir exportar la copia, desinstalar una vez e instalar la nueva identidad estable.
3. Fabricantes con ahorro de batería agresivo pueden detener el overlay o retrasar WorkManager; debe probarse en dispositivos reales.
4. Android siempre exige confirmación final para instalar una APK en una aplicación ordinaria.
5. Los adjuntos del respaldo conservan referencias `content://`; no incorporan los bytes de documentos externos.
6. Room y DataStore no comparten una transacción atómica de proceso. El código valida todo primero y compensa preferencias si Room falla, pero una terminación forzada del proceso en esa ventana excepcional requiere repetir la restauración.
7. La variante `debug` de distribución directa sigue siendo el canal de autoactualización por compatibilidad con la APK existente. Antes de una publicación comercial conviene crear un build type `sideload` no depurable y migrar una sola vez.

## Criterio de aceptación

El lote solo se acepta cuando:

- `python tools/validate_package.py .` pasa dentro del paquete extraído;
- el ZIP externo coincide con el SHA-256 publicado;
- `gradlew clean test lintDebug assembleDebug --stacktrace` pasa en el repositorio completo;
- `output-metadata.json` contiene un `versionCode` positivo y esperado;
- `apksigner verify` pasa cuando la herramienta está disponible;
- el commit se crea después del build;
- el push ocurre únicamente con remoto y firma confirmados.
