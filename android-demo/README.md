# PipaCo Demo (APK de prueba, 7 días)

App Android **separada** de la real: paquete `com.pipaco.demo`, nombre "PipaCo Demo", ícono con insignia DEMO.
Se instala **al lado** de PipaCo (com.pipaco.os) sin tocarla. Empaqueta `../demo/index.html` + `../demo/img`
(funciona sin internet). Sin login, sin huella, sin actualizador. Usa el mismo puente de archivos que la app real
(`saveFile` → Descargas/PipaCo Demo, `shareFile` → WhatsApp / correo / compartir) y además
`demoDiasRestantes()` para el banner "Demo: quedan X días".

## Prueba de 7 días (`Trial.java`, `TrialStore.java`)
- Empieza en la **primera apertura**. Se guarda en SharedPreferences **y** en un archivo privado (`files/demo_trial.dat`);
  si falta uno se repara con el otro (se usa el inicio más antiguo y la última vista más reciente).
- **Reloj atrasado:** el tiempo efectivo es `max(ahora, última vista)`; atrasar la fecha no devuelve días.
- **"Borrar datos" de la app** no reinicia la prueba: si no hay nada guardado y la app se instaló hace más de 1 día,
  se usa la fecha de instalación.
- Al vencer: pantalla "La demo de Pip & Co terminó" con WhatsApp, correo (conscaribe23@gmail.com), enlace a la demo web y Salir.
  El número de WhatsApp es la constante `DemoActivity.TELEFONO_WHATSAPP` (vacía: WhatsApp deja elegir el contacto).
- **Límite honesto:** sin servidor, **desinstalar y volver a instalar reinicia la prueba** (Android borra los datos de la app;
  solo si el teléfono tiene activada la copia de seguridad de Google podría restaurarse el inicio).

## Compilar y publicar
```
# keystore DE LA DEMO (distinto del real): /workspace/pipaco-android/keystore/pipaco-demo.jks
# contraseña: pipaco-demo-password.txt (junto al keystore). keystore.properties NO se sube.
./gradlew testReleaseUnitTest lintRelease assembleRelease
cp app/build/outputs/apk/release/app-release.apk dist/PipaCo-Demo.apk
gh release create demo-vX.Y.Z dist/PipaCo-Demo.apk --prerelease --latest=false -t "PipaCo Demo X.Y.Z" -n "..."
```
Siempre `--prerelease --latest=false`: `/releases/latest` debe seguir apuntando a la app real (el actualizador de la app real usa `version.json`, no esta release).
Si cambias la demo web, corre antes `python3 demo/build_demo.py`.
