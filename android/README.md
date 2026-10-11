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
