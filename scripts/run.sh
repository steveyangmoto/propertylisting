#!/usr/bin/env bash
# Build, test and launch the Property Search app.

source "$(dirname "$0")/lib/common.sh"

usage() {
    cat <<'EOF'
Build, test and launch the Property Search app.

  scripts/run.sh           Start the server if needed, start an emulator if none is connected,
                           then install and launch the app
  scripts/run.sh test      Run unit tests
  scripts/run.sh all       Run unit tests, then do everything scripts/run.sh does

Options:
  --host <ip>    Server address as seen from the device. Defaults to 10.0.2.2 on an emulator
                 and this machine's LAN IP on a physical device.
  --port <port>  Server port (default 8000).
  --avd <name>   Emulator to start when no device is connected (default: first AVD found).

Set ANDROID_SERIAL to choose a device when several are connected.
EOF
}

COMMAND="run"
HOST=""
PORT="8000"
AVD="${AVD:-}"
APP_ID="com.example.propertylisting"
EMULATOR_LOG="/tmp/property-search-emulator.log"

while [[ $# -gt 0 ]]; do
    case "$1" in
        run|test|all) COMMAND="$1" ;;
        --host) HOST="$2"; shift ;;
        --port) PORT="$2"; shift ;;
        --avd) AVD="$2"; shift ;;
        -h|--help) usage; exit 0 ;;
        *) echo "Unknown argument: $1 (see scripts/run.sh --help)" >&2; exit 1 ;;
    esac
    shift
done

source "$SCRIPTS_DIR/lib/server.sh"

lan_ip() {
    ipconfig getifaddr en0 2>/dev/null \
        || ipconfig getifaddr en1 2>/dev/null \
        || hostname -I 2>/dev/null | awk '{print $1}'
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

    info "Starting emulator '$AVD' (log: $EMULATOR_LOG)"
    nohup "$EMULATOR" -avd "$AVD" -no-snapshot-save -no-audio > "$EMULATOR_LOG" 2>&1 &

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
    test) find_sdk; run_tests ;;
    run) install_and_launch ;;
    all) find_sdk; run_tests; install_and_launch ;;
esac
