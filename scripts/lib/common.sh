# Shared helpers for the scripts in scripts/. Source this file; don't run it.

set -euo pipefail

SCRIPTS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPTS_DIR")"
cd "$PROJECT_ROOT"

info() { printf '\033[1;34m==>\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33mwarning:\033[0m %s\n' "$*" >&2; }
fail() { printf '\033[1;31merror:\033[0m %s\n' "$*" >&2; exit 1; }

# Sets SDK, ADB and EMULATOR, and writes local.properties if it doesn't exist yet.
find_sdk() {
    local sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
    if [[ -z "$sdk" && -f local.properties ]]; then
        sdk="$(sed -n 's/^sdk\.dir=//p' local.properties)"
    fi
    if [[ -z "$sdk" ]]; then
        for candidate in "$HOME/Library/Android/sdk" "$HOME/Android/Sdk"; do
            [[ -d "$candidate" ]] && sdk="$candidate" && break
        done
    fi
    [[ -d "$sdk" ]] || fail "Android SDK not found. Set ANDROID_HOME or install Android Studio."
    [[ -f local.properties ]] || echo "sdk.dir=$sdk" > local.properties
    SDK="$sdk"
    ADB="$SDK/platform-tools/adb"
    EMULATOR="$SDK/emulator/emulator"
}
