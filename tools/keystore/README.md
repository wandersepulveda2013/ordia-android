# Keystore de firma estable — guía

Para que las actualizaciones automáticas funcionen, **todas** las builds del canal
OpenHands Preview Advanced deben compartir la **misma firma**. El auto-updater
rechaza APKs cuya firma no sea compatible con la instalación actual
(`signaturesAreCompatible`).

## Secretos de GitHub necesarios

El workflow `.github/workflows/openhands-delivery.yml` firma con estos secretos:

| Secret | Descripción |
|---|---|
| `ORDIA_UPDATE_KEYSTORE_BASE64` | keystore `.jks`/`.keystore` codificado en base64 |
| `ORDIA_UPDATE_KEYSTORE_PASSWORD` | contraseña del keystore |
| `ORDIA_UPDATE_KEY_ALIAS` | alias de la clave |
| `ORDIA_UPDATE_KEY_PASSWORD` | contraseña de la clave |

Y el watchdog/supervisor usan:

| Secret | Descripción |
|---|---|
| `OPENHANDS_API_KEY` | clave de OpenHands Cloud (dispatch) |
| `ORDIA_SUPERVISOR_GIST_ID` | id del gist de estado del supervisor (opcional) |

## Generar un keystore local (única intervención humana)

En tu máquina, una sola vez:

```bash
keytool -genkeypair -v \
  -keystore ordia-update.keystore \
  -alias ordia-update \
  -keyalg RSA -keysize 2048 -validity 36500 \
  -storepass 'ELIGE_PASSWORD_DE_KEYSTORE' \
  -keypass 'ELIGE_PASSWORD_DE_CLAVE'
```

Cargar los 4 secretos en GitHub (repo → Settings → Secrets → Actions):

```bash
GH_REPO=wandersepulveda2013/ordia-android
base64 -w0 ordia-update.keystore > ks.b64
gh secret set ORDIA_UPDATE_KEYSTORE_BASE64 --repo "$GH_REPO" < ks.b64
gh secret set ORDIA_UPDATE_KEYSTORE_PASSWORD --repo "$GH_REPO" --body 'ELIGE_PASSWORD_DE_KEYSTORE'
gh secret set ORDIA_UPDATE_KEY_ALIAS --repo "$GH_REPO" --body 'ordia-update'
gh secret set ORDIA_UPDATE_KEY_PASSWORD --repo "$GH_REPO" --body 'ELIGE_PASSWORD_DE_CLAVE'
rm -f ks.b64 ordia-update.keystore
```

**NUNCA** subas el keystore ni las contraseñas al repo. `.gitignore` ya ignora
`*.keystore`, `*.jks`, `tools/.env`.

## Primera instalación en el teléfono

Si tu Ordía actual tiene firma distinta, Android no permite actualizar encima.
Hace falta **una última instalación manual limpia** de una APK firmada con el
keystore estable. A partir de esa build, todas las futuras (del canal OpenHands)
comparten firma y se actualizan automáticamente.
