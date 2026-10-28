#!/usr/bin/env bash
# Start, stop or check the listings server in server/.

source "$(dirname "$0")/lib/common.sh"

usage() {
    cat <<'EOF'
Start, stop or check the listings server.

  scripts/server.sh start      Start the server in the background (if it isn't already running)
  scripts/server.sh stop       Stop a server that these scripts started
  scripts/server.sh status     Report whether a server is responding

Options:
  --port <port>  Server port (default 8000).

The server logs to /tmp/property-search-server.log.
EOF
}

COMMAND=""
PORT="8000"

while [[ $# -gt 0 ]]; do
    case "$1" in
        start|stop|status) COMMAND="$1" ;;
        --port) PORT="$2"; shift ;;
        -h|--help) usage; exit 0 ;;
        *) echo "Unknown argument: $1 (see scripts/server.sh --help)" >&2; exit 1 ;;
    esac
    shift
done

source "$SCRIPTS_DIR/lib/server.sh"

case "$COMMAND" in
    start) ensure_server ;;
    stop) stop_server ;;
    status) server_status ;;
    *) usage; exit 1 ;;
esac
