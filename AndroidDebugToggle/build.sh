#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SDK_DIR="${ANDROID_SDK_ROOT:-/opt/homebrew/share/android-commandlinetools}"
BUILD_TOOLS_DIR="${ANDROID_BUILD_TOOLS_DIR:-${SDK_DIR}/build-tools/36.0.0}"
PLATFORM_DIR="${ANDROID_PLATFORM_DIR:-${SDK_DIR}/platforms/android-36}"
BUILD_DIR="${ROOT_DIR}/build"

AAPT2="${BUILD_TOOLS_DIR}/aapt2"
D8="${BUILD_TOOLS_DIR}/d8"
APKSIGNER="${BUILD_TOOLS_DIR}/apksigner"
ZIPALIGN="${BUILD_TOOLS_DIR}/zipalign"
ANDROID_JAR="${PLATFORM_DIR}/android.jar"

for tool in "${AAPT2}" "${D8}" "${APKSIGNER}" "${ZIPALIGN}"; do
  if [[ ! -x "${tool}" ]]; then
    echo "Missing Android build tool: ${tool}" >&2
    exit 1
  fi
done
if [[ ! -f "${ANDROID_JAR}" ]]; then
  echo "Missing Android platform: ${ANDROID_JAR}" >&2
  exit 1
fi
if ! command -v javac >/dev/null; then
  echo "javac not found in PATH" >&2
  exit 1
fi

rm -rf "${BUILD_DIR}"
mkdir -p "${BUILD_DIR}/generated" "${BUILD_DIR}/classes" "${BUILD_DIR}/dex" "${BUILD_DIR}/apk"

"${AAPT2}" compile --dir "${ROOT_DIR}/app/src/main/res" -o "${BUILD_DIR}/generated/res.zip"
"${AAPT2}" link \
  -o "${BUILD_DIR}/apk/base.apk" \
  -I "${ANDROID_JAR}" \
  --manifest "${ROOT_DIR}/app/src/main/AndroidManifest.xml" \
  --java "${BUILD_DIR}/generated" \
  "${BUILD_DIR}/generated/res.zip"

find "${ROOT_DIR}/app/src/main/java" "${BUILD_DIR}/generated" -name '*.java' -type f \
  -exec javac --release 11 \
    -classpath "${ANDROID_JAR}" \
    -d "${BUILD_DIR}/classes" \
    {} +

find "${BUILD_DIR}/classes" -name '*.class' -type f \
  -exec "${D8}" --min-api 30 --output "${BUILD_DIR}/dex" {} +

cp "${BUILD_DIR}/apk/base.apk" "${BUILD_DIR}/apk/unsigned.apk"
zip -q -j "${BUILD_DIR}/apk/unsigned.apk" "${BUILD_DIR}/dex/classes.dex"
"${ZIPALIGN}" -f 4 "${BUILD_DIR}/apk/unsigned.apk" "${BUILD_DIR}/apk/aligned.apk"

KEYSTORE="${ANDROID_DEBUG_KEYSTORE:-${ROOT_DIR}/debug.keystore}"
if [[ ! -f "${KEYSTORE}" ]]; then
  keytool -genkeypair \
    -alias androiddebugkey \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -storepass android \
    -keypass android \
    -dname "CN=Android Debug,O=Android,C=US" \
    -keystore "${KEYSTORE}"
fi

"${APKSIGNER}" sign \
  --ks "${KEYSTORE}" \
  --ks-pass pass:android \
  --key-pass pass:android \
  --out "${BUILD_DIR}/MirrorDebug.apk" \
  "${BUILD_DIR}/apk/aligned.apk"

"${APKSIGNER}" verify --verbose --print-certs "${BUILD_DIR}/MirrorDebug.apk"
echo "Built ${BUILD_DIR}/MirrorDebug.apk"
