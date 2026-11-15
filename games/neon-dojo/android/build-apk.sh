#!/usr/bin/env bash
# Compila Neon Dojo como APK firmado usando solo las herramientas del SDK (sin Gradle).
# Requiere: JDK 17+, ANDROID_HOME con platforms;android-34 y build-tools;34.0.0
set -euo pipefail
cd "$(dirname "$0")"
SDK="${ANDROID_HOME:-/opt/android-sdk}"
BT="$SDK/build-tools/34.0.0"
JAR="$SDK/platforms/android-34/android.jar"
VERSION_CODE="${VERSION_CODE:-1}"
VERSION_NAME="${VERSION_NAME:-1.0.0}"
KS="${KEYSTORE:-keystore/neondojo.jks}"
KS_PASS="${KEYSTORE_PASS:-neondojo}"
OUT=build
rm -rf "$OUT" && mkdir -p "$OUT"/{assets/www/fonts,gen,classes,dex}

echo "▸ assets web"
cp fonts/PressStart2P-Regular.ttf "$OUT/assets/www/fonts/"
python3 - "$OUT/assets/www/index.html" <<'PY'
import re,sys
s=open('../index.html',encoding='utf-8').read()
s=re.sub(r'<!--PWA-->.*?<!--/PWA-->\n','',s,flags=re.S)
s=re.sub(r'/\*SW\*/.*?/\*/SW\*/','',s,flags=re.S)
s=re.sub(r'<link rel="(preconnect|stylesheet)" href="https://fonts\.[^>]*>\n','',s)
face="@font-face{font-family:'Press Start 2P';src:url(fonts/PressStart2P-Regular.ttf) format('truetype');font-display:block}\n"
s=s.replace('<style>\n','<style>\n'+face,1)
assert 'fonts.googleapis' not in s
open(sys.argv[1],'w',encoding='utf-8').write(s)
PY

echo "▸ recursos"
"$BT/aapt2" compile --dir res -o "$OUT/res.zip"
"$BT/aapt2" link -I "$JAR" --manifest AndroidManifest.xml -A "$OUT/assets" \
  --min-sdk-version 24 --target-sdk-version 34 \
  --version-code "$VERSION_CODE" --version-name "$VERSION_NAME" \
  --java "$OUT/gen" -o "$OUT/unsigned.apk" "$OUT/res.zip"

echo "▸ java → dex"
javac --release 11 -nowarn -classpath "$JAR" -d "$OUT/classes" $(find src "$OUT/gen" -name '*.java')
"$BT/d8" --release --min-api 24 --lib "$JAR" --output "$OUT/dex" $(find "$OUT/classes" -name '*.class')
(cd "$OUT/dex" && zip -q -u ../unsigned.apk classes.dex)

echo "▸ alinear y firmar"
"$BT/zipalign" -p -f 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"
if [ ! -f "$KS" ]; then
  mkdir -p "$(dirname "$KS")"
  keytool -genkeypair -keystore "$KS" -storepass "$KS_PASS" -keypass "$KS_PASS" -alias neondojo \
    -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Neon Dojo, O=Neon Dojo, C=CL" >/dev/null 2>&1
fi
"$BT/apksigner" sign --ks "$KS" --ks-pass "pass:$KS_PASS" --ks-key-alias neondojo \
  --out "NeonDojo-$VERSION_NAME.apk" "$OUT/aligned.apk"
"$BT/apksigner" verify --print-certs "NeonDojo-$VERSION_NAME.apk" | head -3
ls -lh "NeonDojo-$VERSION_NAME.apk"
