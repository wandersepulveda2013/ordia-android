# Ordia Android

Ordia es una aplicación Android local para organización personal, tareas, planificación, notas, hábitos, rutinas y enfoque.

## APK automático

Cada cambio enviado a `main` ejecuta `.github/workflows/android-ci.yml`. El flujo realiza verificación estática, pruebas unitarias, lint, compilación del APK de depuración y publica una versión de prueba en **Releases**.

El APK de prueba utiliza `com.ordia.app.debug` y la clave de desarrollo incluida únicamente para mantener compatibilidad entre actualizaciones de prueba. Esa clave no debe usarse para una publicación final en Google Play.

## Requisitos del proyecto

- JDK 17
- Android SDK 36
- Gradle 8.13
- Android Gradle Plugin 8.13.2
