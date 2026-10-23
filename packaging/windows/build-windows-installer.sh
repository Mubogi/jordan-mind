#!/usr/bin/env bash
# Builds a Windows installer for Jordan Mind from a Linux machine.
#
# jpackage cannot cross-compile — it links a native launcher and a runtime for the target OS — so
# this assembles the distribution by hand: the desktop jars, a Windows Java runtime, and the
# Windows Skiko native library, wrapped in an NSIS installer.
#
#   ./packaging/windows/build-windows-installer.sh              # app only, ~100 MB
#   BUNDLE_MODEL=1 ./packaging/windows/build-windows-installer.sh  # app + Qwen + llama.cpp
#
# With BUNDLE_MODEL=1 the installer also carries a quantised Qwen2.5-0.5B GGUF and the llama.cpp
# runtime, so the result works offline out of the box with nothing else installed.
#
# Output: packaging/windows/dist/JordanMind-Setup-<version>-win64.exe
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
WORK="${WORK:-$ROOT/packaging/windows/.work}"
# Downloads live outside WORK so the staging wipe below does not re-download ~1 GB every build.
CACHE="${CACHE:-$ROOT/packaging/windows/.cache}"
DIST="$ROOT/packaging/windows/dist"
VERSION="${VERSION:-1.1.0}"
SKIKO_VERSION="${SKIKO_VERSION:-0.8.18}"
LLAMA_BUILD="${LLAMA_BUILD:-b11829}"
JRE_URL="https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jre/hotspot/normal/eclipse"
MODEL_URL="${MODEL_URL:-https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf}"

command -v makensis >/dev/null || { echo "NSIS is required: apt-get install nsis"; exit 1; }
command -v 7z >/dev/null || { echo "7z is required: apt-get install 7zip"; exit 1; }

echo "==> Building the desktop distributable"
(cd "$ROOT" && ./gradlew --no-daemon :desktop:createReleaseDistributable)
APP_SRC="$ROOT/desktop/build/compose/binaries/main-release/app/Jordan Mind/lib/app"
[ -d "$APP_SRC" ] || { echo "Desktop distributable not found at $APP_SRC"; exit 1; }

echo "==> Staging the Windows distribution"
rm -rf "$WORK" && mkdir -p "$WORK/stage/Jordan Mind/app" "$WORK/stage/Jordan Mind/runtime" "$CACHE" "$DIST"
STAGE="$WORK/stage/Jordan Mind"

# The jars are platform-independent, but the Skiko runtime jar is not: swap the Linux build for
# the Windows one, or the app fails to start with an UnsatisfiedLinkError.
cp "$APP_SRC"/*.jar "$STAGE/app/"
rm -f "$STAGE/app"/skiko-awt-runtime-linux-*.jar
[ -d "$APP_SRC/resources" ] && cp -r "$APP_SRC/resources" "$STAGE/app/"

echo "==> Fetching the Windows Skiko native library ($SKIKO_VERSION)"
SKIKO_JAR="$CACHE/skiko-awt-runtime-windows-x64-$SKIKO_VERSION.jar"
if [ ! -f "$SKIKO_JAR" ]; then
  curl -fsSL -o "$SKIKO_JAR" \
    "https://repo1.maven.org/maven2/org/jetbrains/skiko/skiko-awt-runtime-windows-x64/$SKIKO_VERSION/skiko-awt-runtime-windows-x64-$SKIKO_VERSION.jar"
fi
cp "$SKIKO_JAR" "$STAGE/app/"
python3 - "$SKIKO_JAR" "$STAGE/app" <<'PY'
import sys, zipfile
jar, out = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(jar) as z:
    for name in z.namelist():
        if name.endswith(".dll"):
            with open(f"{out}/{name.split('/')[-1]}", "wb") as fh:
                fh.write(z.read(name))
            print("   extracted", name.split("/")[-1])
PY

echo "==> Fetching a Windows Java runtime (Temurin 21)"
JRE_ZIP="$CACHE/jre-win64.zip"
if [ ! -f "$JRE_ZIP" ]; then
  curl -fsSL -o "$JRE_ZIP" "$JRE_URL"
fi
rm -rf "$WORK/jretmp" && mkdir -p "$WORK/jretmp"
python3 -c "import zipfile,sys; zipfile.ZipFile(sys.argv[1]).extractall(sys.argv[2])" "$JRE_ZIP" "$WORK/jretmp"
JRE_ROOT="$(find "$WORK/jretmp" -maxdepth 1 -mindepth 1 -type d | head -1)"
cp -r "$JRE_ROOT"/. "$STAGE/runtime/"
rm -rf "$WORK/jretmp"
[ -f "$STAGE/runtime/bin/javaw.exe" ] || { echo "Windows runtime is missing javaw.exe"; exit 1; }

if [ "${BUNDLE_MODEL:-0}" = "1" ]; then
  echo "==> Bundling the offline model"
  mkdir -p "$STAGE/app/models"
  MODEL_GGUF="$CACHE/qwen2.5-0.5b-instruct-q4_k_m.gguf"
  if [ ! -f "$MODEL_GGUF" ]; then
    echo "    downloading Qwen2.5-0.5B q4_k_m (~469 MB)"
    curl -fL --retry 3 -o "$MODEL_GGUF" "$MODEL_URL"
  fi
  cp "$MODEL_GGUF" "$STAGE/app/models/"

  echo "==> Bundling the llama.cpp runtime ($LLAMA_BUILD)"
  LLAMA_ZIP="$CACHE/llama-$LLAMA_BUILD-bin-win-cpu-x64.zip"
  if [ ! -f "$LLAMA_ZIP" ]; then
    curl -fsSL -o "$LLAMA_ZIP" \
      "https://github.com/ggml-org/llama.cpp/releases/download/$LLAMA_BUILD/llama-$LLAMA_BUILD-bin-win-cpu-x64.zip"
  fi
  rm -rf "$WORK/llama" && mkdir -p "$WORK/llama"
  7z x -o"$WORK/llama" "$LLAMA_ZIP" > /dev/null
  # llama-cli.exe needs its ggml/llama DLLs beside it; copy the whole runtime directory.
  cp "$WORK/llama"/*.exe "$WORK/llama"/*.dll "$STAGE/app/models/" 2>/dev/null || true
  [ -f "$STAGE/app/models/llama-cli.exe" ] || { echo "llama-cli.exe is missing from the runtime zip"; exit 1; }
  echo "    bundled $(ls "$STAGE/app/models" | wc -l) model/runtime files"
fi

echo "==> Building the launcher"
(cd "$ROOT/packaging/windows" && makensis -V2 -DOUTDIR="$WORK" launcher.nsi)
cp "$ROOT/packaging/windows/JordanMind.exe" "$WORK/" 2>/dev/null || true
[ -f "$WORK/JordanMind.exe" ] || { echo "Launcher was not produced"; exit 1; }

SUFFIX=""
[ "${BUNDLE_MODEL:-0}" = "1" ] && SUFFIX="-bundled"

echo "==> Building the installer"
(cd "$ROOT/packaging/windows" && makensis -V2 -DWORK="$WORK" -DVERSION="$VERSION" \
  -DSUFFIX="$SUFFIX" -DOUTDIR="$DIST" installer.nsi)

echo
echo "Done: $DIST/JordanMind-Setup-$VERSION-win64$SUFFIX.exe"
ls -la "$DIST"/JordanMind-Setup-*.exe
