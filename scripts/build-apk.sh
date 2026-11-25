#!/bin/sh
# Builds DroidNix with the local toolchain.
#
#   scripts/build-apk.sh            -> debug APK
#   scripts/build-apk.sh release    -> optimised release APK (debug-signed)
#
# Environment:
#   DROIDNIX_JDK   path to a JDK 21 (overrides JAVA_HOME)
#   ANDROID_HOME   Android SDK location (Gradle also reads local.properties)
#   GRADLE_BIN     alternate Gradle binary when ./gradlew is absent
set -eu
cd "$(dirname "$0")/.."

JDK="${DROIDNIX_JDK:-${JAVA_HOME:-}}"
if [ -z "$JDK" ] || [ ! -x "$JDK/bin/java" ]; then
  echo "A JDK 21 is required. Set JAVA_HOME or DROIDNIX_JDK." >&2
  exit 1
fi
"$JDK/bin/java" -version 2>&1 | head -1 | grep -qE '"21(\.|")' || {
  echo "warning: JDK 21 is recommended; found $("$JDK/bin/java" -version 2>&1 | head -1)" >&2
}

export JAVA_HOME="$JDK"
export PATH="$JDK/bin:$PATH"

if [ -x ./gradlew ]; then
  GRADLE=./gradlew
else
  GRADLE="${GRADLE_BIN:-gradle}"
  command -v "$GRADLE" >/dev/null 2>&1 || {
    echo "Gradle not found: provide ./gradlew or set GRADLE_BIN." >&2
    exit 1
  }
fi

variant=debug
task=assembleDebug
if [ "${1:-}" = "release" ]; then
  variant=release
  task=assembleRelease
  shift
fi

"$GRADLE" -Dorg.gradle.java.home="$JDK" ":app:$task" --console=plain --no-daemon "$@"

out="app/build/outputs/apk/$variant/app-$variant.apk"
[ -f "$out" ] || { echo "APK not produced at $out" >&2; exit 1; }
echo "APK: $out"
