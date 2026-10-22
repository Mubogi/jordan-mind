#!/bin/bash
# Build helper for Jordan Mind.
#   ./build.sh                       -> APK with no bundled model
#   ./build.sh /path/to/model.task   -> APK with the model baked into assets/models/
set -o pipefail
export JAVA_HOME=${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}
export ANDROID_HOME=${ANDROID_HOME:-/workspace/android-sdk}
export PATH="$JAVA_HOME/bin:$PATH"
GRADLE=${GRADLE:-/workspace/tools/gradle-8.14.6/bin/gradle}

ARGS=("assembleDebug" "--no-daemon")
if [ -n "$1" ]; then
  ARGS+=("-PbundleModel=$1")
fi
exec "$GRADLE" "${ARGS[@]}"
