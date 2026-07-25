#!/usr/bin/env bash
# Patch a target APK with the PocketVoice Xposed hook module using LSPatch CLI.
# Usage:   ./scripts/patch-apk.sh <target.apk>  [more-modules.apk ...]
# Output:  <target>-lspatched.apk in current dir.
set -euo pipefail

if [ $# -lt 1 ]; then
    echo "usage: $0 <target.apk> [extra-module.apk ...]" >&2
    exit 1
fi

TARGET="$1"; shift
LSP_VERSION="0.7.0"
LSP_JAR="$HOME/.cache/lspatch-${LSP_VERSION}.jar"
LSP_URL="https://github.com/LSPosed/LSPatch/releases/download/v${LSP_VERSION}/lspatch-v${LSP_VERSION}-8014-release.jar"

HOOK_MODULE_APK="$(dirname "$0")/../xposed-hook/build/outputs/apk/release/xposed-hook-release-unsigned.apk"
HOOK_MODULE_APK="$(realpath "$HOOK_MODULE_APK" 2>/dev/null || echo "$HOOK_MODULE_APK")"

if [ ! -f "$LSP_JAR" ]; then
    mkdir -p "$(dirname "$LSP_JAR")"
    echo "→ downloading LSPatch ${LSP_VERSION}"
    curl -L --retry 6 --retry-delay 4 -o "$LSP_JAR" "$LSP_URL"
fi

if [ ! -f "$HOOK_MODULE_APK" ]; then
    echo "! hook module not built. run: ./gradlew :xposed-hook:assembleRelease" >&2
    exit 2
fi

echo "→ patching $TARGET with $HOOK_MODULE_APK"
java -jar "$LSP_JAR" \
    -m "$HOOK_MODULE_APK" \
    "$@" \
    "$TARGET" \
    --force

echo "✓ done. install the resulting *-lspatched.apk"
