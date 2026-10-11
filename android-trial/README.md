# PipaCo Prueba (APK de prueba, 14 días)

App **completa** de PipaCo (mismo `index.html` 2.3.0) con límites de prueba. Paquete `com.pipaco.trial`, firmado con un keystore propio (no se sube).

- **Configuración (único lugar):** `web/trial-config.json` → días, `maxVentas` 30, `maxClientes` 5, `maxComprobantes` 10, `maxProductos` 10, marca del PDF, correo y WhatsApp.
- **Cómo funciona:** `web/build_trial.py` genera `app/src/main/assets/www/index.html` desde `../index.html` + `web/trial.js` (la app real no cambia). Gradle lo ejecuta antes de compilar.
- **Acceso:** cada usuario crea su correo/contraseña (como la web). Sin huella ni actualizador. Datos de inicio neutros.
- **Bloqueado:** Excel/CSV, respaldo/importación JSON, contabilidad avanzada (libro diario, estado de resultados, balance, ITBMS, Excel contador). PDFs con marca "VERSIÓN DE PRUEBA".
- **Periodo:** 14 días desde la primera apertura (SharedPreferences + archivo privado, reloj atrasado no devuelve días, borrar datos usa la fecha de instalación). Al vencer: pantalla de contacto y modo solo lectura ("Ver mis datos").
- **Límite honesto:** desinstalar y volver a instalar reinicia la prueba (y borra los datos).

Compilar: `./gradlew testReleaseUnitTest assembleRelease` (necesita `keystore.properties`, no incluido). Prueba web de límites: `node web/test/limites.test.js` (puppeteer, sirviendo el HTML generado en :8771).
Íconos: `python3 tools/make_icon.py`.
