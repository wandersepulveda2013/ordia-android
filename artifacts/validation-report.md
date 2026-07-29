# Informe de validación — Ordia 1.0 RC1

Fecha: 29 de julio de 2026

## Inventario

- Archivos totales del proyecto: 111
- Archivos Kotlin principales: 52
- Líneas Kotlin principales: 6604
- Archivos de pruebas unitarias: 9
- Archivos de pruebas instrumentales: 2

## Comprobaciones ejecutadas en este entorno

- Verificación estática del proyecto: **aprobada**.
- Pruebas puras de dominio con Kotlin/JVM: **25 afirmaciones aprobadas**.
- XML: **válido**.
- JSON: **válido**.
- YAML de CI: **válido**.
- Scripts Python: **compilan**.
- Scripts shell: **sintaxis válida**.
- Codificación UTF-8 y detección de mojibake: **aprobada**.
- Manifiesto local-first: sin permiso Internet, sin permiso de grabación directa y con backup automático desactivado.

## Mejoras finales incluidas

- Planificación automática local del día.
- Recomendación local del guardián.
- Búsqueda que ignora tildes.
- Captura natural de fechas como «viernes» y duraciones como «en 45 minutos».
- Permiso de notificaciones solicitado desde Ajustes con contexto.
- Manejo de bloques de planificación que cruzan medianoche.
- Documentación de producto, arquitectura, privacidad, pruebas y lanzamiento.

## Límite de validación

Este entorno no contiene Android SDK ni una instalación completa de Gradle. Por tanto, aquí no se ejecutaron `assembleDebug`, lint Android ni las pruebas instrumentales en emulador. El repositorio incluye CI para ejecutar esas puertas en un entorno Android real. La ausencia de `gradle-wrapper.jar` está documentada y no afecta el flujo de CI que instala Gradle directamente.

## Estado

**Candidato de código fuente 1.0 (RC1).** No debe publicarse hasta que la CI compile el APK, lint termine sin bloqueos y se completen pruebas en dispositivos reales.
