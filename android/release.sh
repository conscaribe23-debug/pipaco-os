#!/usr/bin/env bash
# Publica una versión nueva del APK de PipaCo.
# Uso:  ./release.sh "Novedades de esta versión"
# Antes: sube versionCode y versionName en app/build.gradle (y VERSION en ../sw.js si cambió index.html).
set -euo pipefail
cd "$(dirname "$0")"
REPO=conscaribe23-debug/pipaco-os
NOTES="${1:-Mejoras y correcciones.}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}"
CODE=$(grep -oP 'versionCode\s+\K\d+' app/build.gradle)
NAME=$(grep -oP "versionName\s+'\K[^']+" app/build.gradle)
[ -f keystore.properties ] || { echo "Falta keystore.properties (firma). Ver README.md"; exit 1; }
PREV=$(python3 -c "import json;print(json.load(open('version.json'))['versionCode'])" 2>/dev/null || echo 0)
[ "$CODE" -gt "$PREV" ] || { echo "versionCode ($CODE) debe ser mayor que el publicado ($PREV)"; exit 1; }

./gradlew --no-daemon -q assembleRelease
mkdir -p dist
APK="dist/PipaCo-v$NAME.apk"
cp app/build/outputs/apk/release/app-release.apk "$APK"
"$ANDROID_HOME"/build-tools/35.0.0/apksigner verify "$APK"
SHA=$(sha256sum "$APK" | cut -d' ' -f1)

cp "$APK" dist/PipaCo.apk
gh release create "v$NAME" "$APK" dist/PipaCo.apk --repo "$REPO" --title "PipaCo $NAME" --notes "$NOTES"
URL="https://github.com/$REPO/releases/download/v$NAME/PipaCo-v$NAME.apk"

python3 - "$CODE" "$NAME" "$URL" "$SHA" "$NOTES" <<'PY'
import json,sys
c,n,u,s,no=sys.argv[1:]
json.dump({"versionCode":int(c),"versionName":n,"apkUrl":u,"sha256":s,"notes":no},open('version.json','w'),ensure_ascii=False,indent=2)
PY
cd .. && git add android/version.json android/app/build.gradle sw.js index.html && git commit -m "Release Android v$NAME" && git push
echo "Listo: v$NAME  sha256=$SHA"
echo "Verifica en ~1-2 min: https://conscaribe23-debug.github.io/pipaco-os/android/version.json"
