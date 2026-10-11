# PipaCo para Android (APK propio, sin Play Store)

App nativa mínima (WebView) que incluye `index.html`, `manifest.webmanifest` e `icons/` de la raíz
del repo dentro del APK, así funciona 100 % sin internet. Se sirve desde
`https://appassets.androidplatform.net/assets/www/` (contexto seguro: cifrado de la contraseña,
localStorage e IndexedDB).

- Paquete: `com.pipaco.os` · minSdk 24 (Android 7) · targetSdk 34
- Fotos de facturas: cámara o galería (`<input type=file>`)
- Exportar Excel/CSV/JSON: se guarda en **Descargas/PipaCo** (puente `window.PipaCoAndroid.saveFile`)
- Enlaces externos (WhatsApp `wa.me`, Google Maps, etc.) se abren en las apps del sistema

## Compilar
```
export ANDROID_HOME=~/android-sdk JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
./gradlew assembleRelease        # tarea syncWeb copia la app web automáticamente
```
La firma se lee de `android/keystore.properties` (NO está en git):
```
storeFile=/ruta/pipaco-release.jks
storePassword=...
keyAlias=pipaco
keyPassword=...
```
**Importante:** cada actualización debe firmarse con el MISMO keystore y subir `versionCode`
en `app/build.gradle`. Si se pierde el keystore no se puede actualizar la app instalada.

## Bloqueo con huella / PIN (desde 2.2.0)
Al abrir la app (y al volver tras más de 1 minuto en segundo plano) se pide la huella o el PIN/patrón
del teléfono (BiometricPrompt). Si el teléfono no tiene bloqueo de pantalla, la app abre y recomienda
configurarlo. El acceso con correo + contraseña dentro de la app sigue igual.

## Actualizaciones dentro de la app (desde 2.2.0)
La app lee `https://conscaribe23-debug.github.io/pipaco-os/android/version.json`
(`{versionCode, versionName, apkUrl, sha256, notes}`) una vez al día (silencioso) o con
**Configuración → 🔄 Buscar actualización**. Si hay versión nueva: muestra novedades, descarga el APK
del Release de GitHub, verifica SHA-256 y paquete, y abre el instalador de Android
(pide “Permitir de esta fuente” la primera vez). Los datos se conservan.

### Publicar una versión nueva
1. Cambia lo que quieras en `index.html` (y sube `VERSION` en `sw.js` para la web).
2. En `android/app/build.gradle` sube `versionCode` (+1) y `versionName`.
3. `cd android && ./release.sh "Novedades de esta versión"`
   - compila y firma con el MISMO keystore (`keystore.properties`),
   - crea el Release `vX.Y.Z` en GitHub con el APK,
   - escribe `version.json` con la URL y el sha256, hace commit y push.
4. En 1–2 minutos GitHub Pages sirve el nuevo `version.json` y los teléfonos verán el aviso.
