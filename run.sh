#!/usr/bin/env bash
#
# Build, test and launch the Property Search app.
#
#   ./run.sh                 Start the server if needed, start an emulator if none is connected,
#                            then install and launch the app
#   ./run.sh test            Run unit tests
#   ./run.sh all             Run unit tests, then do everything ./run.sh does
#   ./run.sh server          Only start the listings server (if it isn't already running)
#   ./run.sh stop-server     Stop a server that this script started
#
# Options:
#   --host <ip>    Server address as seen from the device. Defaults to 10.0.2.2 on an emulator
#                  and this machine's LAN IP on a physical device.
#   --port <port>  Server port (default 8000).
#   --avd <name>   Emulator to start when no device is connected (default: first AVD found).
#
# Set ANDROID_SERIAL to choose a device when several are connected.

set -euo pipefail

cd "$(dirname "$0")"

COMMAND="run"
HOST=""
PORT="8000"
AVD="${AVD:-}"
APP_ID="com.example.propertylisting"
SERVER_LOG="/tmp/property-search-server.log"
SERVER_PID_FILE="/tmp/property-search-server.pid"

while [[ $# -gt 0 ]]; do
    case "$1" in
        run|test|all|server|stop-server) COMMAND="$1" ;;
        --host) HOST="$2"; shift ;;
        --port) PORT="$2"; shift ;;
        --avd) AVD="$2"; shift ;;
        -h|--help) sed -n '3,19p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
        *) echo "Unknown argument: $1 (see ./run.sh --help)" >&2; exit 1 ;;
    esac
    shift
done

info() { printf '\033[1;34m==>\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33mwarning:\033[0m %s\n' "$*" >&2; }
fail() { printf '\033[1;31merror:\033[0m %s\n' "$*" >&2; exit 1; }

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

lan_ip() {
    ipconfig getifaddr en0 2>/dev/null \
        || ipconfig getifaddr en1 2>/dev/null \
        || hostname -I 2>/dev/null | awk '{print $1}'
}

server_is_up() {
    curl -sf --max-time 2 "http://localhost:$PORT/health" > /dev/null
}

ensure_server() {
    if server_is_up; then
        info "Listings server already running on port $PORT"
        return
    fi

    command -v python3 > /dev/null || fail "python3 is required to run the listings server."
    [[ -f server/data.json ]] || fail "server/data.json is missing; server.py needs it next to it."

    info "Starting listings server on port $PORT (log: $SERVER_LOG)"
    # server.py opens data.json relative to the working directory.
    (cd server && nohup python3 server.py "$PORT" > "$SERVER_LOG" 2>&1 & echo $! > "$SERVER_PID_FILE")

    for _ in $(seq 1 20); do
        server_is_up && { info "Server is up (stop it with ./run.sh stop-server)"; return; }
        sleep 0.5
    done
    fail "Server didn't start. See $SERVER_LOG"
}

stop_server() {
    if [[ -f "$SERVER_PID_FILE" ]] && kill "$(cat "$SERVER_PID_FILE")" 2>/dev/null; then
        rm -f "$SERVER_PID_FILE"
        for _ in $(seq 1 10); do server_is_up || break; sleep 0.3; done
        info "Server stopped"
    else
        rm -f "$SERVER_PID_FILE"
        warn "No server started by this script is running. Stop a manually started server with Ctrl+C."
    fi
}

run_tests() {
    info "Running unit tests"
    if ./gradlew testDebugUnitTest --console=plain; then
        info "All tests passed"
    else
        fail "Tests failed. Report: app/build/reports/tests/testDebugUnitTest/index.html"
    fi
}

connected_device() {
    "$ADB" devices | awk 'NR > 1 && $2 == "device" { print $1; exit }'
}

ensure_device() {
    "$ADB" start-server > /dev/null
    if [[ -n "${ANDROID_SERIAL:-}" || -n "$(connected_device)" ]]; then
        return
    fi

    [[ -x "$EMULATOR" ]] || fail "No device connected and the emulator isn't installed."
    if [[ -z "$AVD" ]]; then
        AVD="$("$EMULATOR" -list-avds | head -n 1)"
        [[ -n "$AVD" ]] || fail "No device connected and no emulator (AVD) exists. Create one in Android Studio."
    fi

    info "Starting emulator '$AVD' (log: /tmp/property-search-emulator.log)"
    nohup "$EMULATOR" -avd "$AVD" -no-snapshot-save -no-audio > /tmp/property-search-emulator.log 2>&1 &

    info "Waiting for the emulator to boot"
    "$ADB" wait-for-device
    until [[ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]; do
        sleep 2
    done
}

install_and_launch() {
    find_sdk
    ensure_server
    ensure_device

    local is_emulator
    is_emulator="$("$ADB" shell getprop ro.kernel.qemu 2>/dev/null | tr -d '\r')"
    if [[ -z "$HOST" ]]; then
        if [[ "$is_emulator" == "1" ]]; then
            HOST="10.0.2.2"
        else
            HOST="$(lan_ip)"
            [[ -n "$HOST" ]] || fail "Couldn't detect this machine's LAN IP. Pass it with --host <ip>."
            warn "Physical device: using $HOST. The phone must be on the same Wi-Fi network."
        fi
    fi
    local base_url="http://$HOST:$PORT"

    info "Building and installing with server $base_url"
    ./gradlew installDebug -PapiBaseUrl="$base_url" --console=plain -q

    info "Launching app"
    "$ADB" shell am force-stop "$APP_ID"
    "$ADB" shell am start -n "$APP_ID/.MainActivity" > /dev/null
    info "Done. Follow app logs with: $ADB logcat --pid=\$($ADB shell pidof $APP_ID)"
}

case "$COMMAND" in
    server) ensure_server ;;
    stop-server) stop_server ;;
    test) find_sdk; run_tests ;;
    run) install_and_launch ;;
    all) find_sdk; run_tests; install_and_launch ;;
esac
