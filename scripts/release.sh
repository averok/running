#!/usr/bin/env bash
# Prepara un release firmado: pasa los tests, compila, comprueba la firma y deja el APK en build/release/.
# NO publica nada. Para publicar (opcional, con `gh` instalado y autenticado): scripts/release.sh --publish
#
# Antes: subir `appVersion` en app/build.gradle.kts, commitear y tener la clave en ~/.config/running-signing/.
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME=${JAVA_HOME:-$HOME/dev/jdk}
export ANDROID_HOME=${ANDROID_HOME:-$HOME/dev/android-sdk}
export PATH="$JAVA_HOME/bin:$PATH"   # apksigner necesita java

VERSION=$(sed -n 's/^val appVersion = "\(.*\)"/\1/p' app/build.gradle.kts)
[ -n "$VERSION" ] || { echo "No encuentro appVersion en app/build.gradle.kts"; exit 1; }
TAG="v$VERSION"

if [ -n "$(git status --porcelain)" ]; then
  echo "Hay cambios sin commitear. Commitea antes de publicar un release."; exit 1
fi
if git rev-parse -q --verify "refs/tags/$TAG" >/dev/null; then
  echo "El tag $TAG ya existe. Sube appVersion en app/build.gradle.kts."; exit 1
fi

./gradlew testDebugUnitTest assembleRelease --console=plain -q
APK=app/build/outputs/apk/release/app-release.apk
[ -f "$APK" ] || { echo "No se generó $APK (¿falta la clave en ~/.config/running-signing/? Sin ella el release sale sin firmar)."; exit 1; }

APKSIGNER=$(ls -d "$ANDROID_HOME"/build-tools/*/apksigner | sort -V | tail -1)
"$APKSIGNER" verify "$APK"
GOT=$("$APKSIGNER" verify --print-certs "$APK" | sed -n 's/.*certificate SHA-256 digest: //p' | head -1 | tr 'a-f' 'A-F' | sed 's/../&:/g; s/:$//')
WANT=$(grep -v '^#' app/release-cert.sha256 | head -1 | tr -d '[:space:]')
if [ "$GOT" != "$WANT" ]; then
  echo "LA FIRMA NO COINCIDE con app/release-cert.sha256."; echo "  APK:      $GOT"; echo "  Esperada: $WANT"; exit 1
fi

mkdir -p build/release
OUT="build/release/running-$VERSION.apk"
cp "$APK" "$OUT"
SHA=$(sha256sum "$OUT" | cut -d' ' -f1)
echo
echo "Listo: $OUT ($(du -h "$OUT" | cut -f1))"
echo "SHA-256: $SHA"
echo "Firma verificada (coincide con app/release-cert.sha256)."

if [ "${1:-}" = "--publish" ]; then
  command -v gh >/dev/null || { echo "Falta gh (GitHub CLI) para publicar."; exit 1; }
  git tag "$TAG" && git push origin "$TAG"
  gh release create "$TAG" "$OUT" --title "Running $VERSION" --generate-notes
else
  echo
  echo "Para publicarlo:"
  echo "  git tag $TAG && git push origin $TAG"
  echo "  gh release create $TAG $OUT --title \"Running $VERSION\" --generate-notes"
  echo "  (o súbelo a mano en github.com/$(git remote get-url origin | sed 's#.*github.com[:/]##; s#\.git$##')/releases/new)"
fi
