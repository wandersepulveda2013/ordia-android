# Compilación y lanzamiento

## Entorno

- JDK 17
- Android SDK Platform 36
- Build Tools 36.0.0
- Gradle 8.13
- Android Gradle Plugin 8.13.2

## Preparación

El paquete fuente no contiene `gradle-wrapper.jar`. Genéralo una vez desde Android Studio o con una instalación local de Gradle:

```bash
gradle wrapper --gradle-version 8.13
```

Después:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

En Windows:

```bat
gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

## Build de lanzamiento

1. Crea un keystore fuera del repositorio.
2. Configura la firma mediante propiedades locales o secretos de CI.
3. No escribas contraseñas en `build.gradle.kts`.
4. Ejecuta:
   ```bash
   ./gradlew clean testReleaseUnitTest lintRelease bundleRelease
   ```
5. Prueba el AAB en un canal interno de Google Play.
6. Revisa el informe de pre-lanzamiento antes de promoverlo.

## CI

`.github/workflows/android-ci.yml` ejecuta:

- verificación estática;
- pruebas unitarias;
- lint debug;
- ensamblado debug;
- publicación temporal del APK y reportes.

## Versionado

- `versionCode` siempre aumenta.
- `versionName` sigue `mayor.menor.parche`.
- Las migraciones Room deben acompañar cualquier cambio de esquema.

## Development APK signing

Debug builds use `app/ordia-dev.keystore`, a public development-only key so test APK updates can be installed over earlier Ordia debug builds. This key must never be used for a Play Store or production release. Production signing must use a private key stored outside the repository.
