# Actualizaciones gestionadas de Ordia 2.0

## Canales

- **Debug de distribución directa:** incluye comprobación y descarga gestionada de APK.
- **Release/tienda:** `SELF_UPDATE_ENABLED=false` y su manifiesto no incluye `INTERNET`, `REQUEST_INSTALL_PACKAGES`, FileProvider, receptor ni actividad del instalador propio.

## Flujo seguro

1. WorkManager consulta la Release oficial cada 12 horas si la opción está activa.
2. El tag debe tener el formato `v2.0.X-code-N` y `N` debe superar el `versionCode` instalado.
3. La Release debe contener exactamente `Ordia-2.0-code-N.apk` y `Ordia-2.0-code-N.apk.sha256`.
4. Se exige una sola línea de checksum en formato `hash␠␠archivo`.
5. La descarga periódica evita redes medidas; una descarga manual explícita puede permitirlas.
6. DownloadManager guarda el archivo en el espacio externo privado sin ofrecer una acción de instalación directa.
7. Ordia compara tamaño anunciado, reportado y copiado mientras calcula SHA-256.
8. Los bytes se copian a `files/verified-updates`.
9. Sobre esa copia privada se verifican paquete, `versionCode` y certificado.
10. Se vuelve a calcular el hash de la copia privada.
11. Solo entonces un FileProvider no exportado entrega acceso temporal al instalador de Android.
12. Android conserva la confirmación final obligatoria.

## Limpieza

- APK inválidas se eliminan;
- descargas sustituidas se cancelan;
- metadatos y APK de versiones instaladas u obsoletas se borran al iniciar;
- una descarga solo se considera gestionada cuando sus metadatos se guardaron sin error.

## Firma estable

El aplicador crea o reutiliza exclusivamente la clave dedicada `ordia-update`; nunca acepta `androiddebugkey`. Las credenciales locales se guardan con DPAPI mediante `Export-Clixml` y se validan con `keytool -list`.

El push automático requiere:

- remoto oficial;
- GitHub CLI autenticado;
- cuatro secretos de firma confirmados;
- compilación local válida.

## GitHub Actions

- todas las Actions están fijadas a SHA;
- checkout no conserva credenciales;
- `build` tiene permisos de lectura;
- `publish` obtiene escritura solo después del build exitoso en `main`;
- el artefacto se comprueba mediante `sha256sum --check`;
- una Release existente no se sobrescribe.

## Restricción de Android

Una aplicación normal no puede instalar silenciosamente una APK. El usuario debe autorizar la fuente cuando corresponda y confirmar la instalación.
