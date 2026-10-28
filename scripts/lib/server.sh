# Listings server management, shared by run.sh and server.sh. Source after common.sh.
# Expects PORT to be set.

SERVER_LOG="/tmp/property-search-server.log"
SERVER_PID_FILE="/tmp/property-search-server.pid"

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
        server_is_up && { info "Server is up (stop it with scripts/server.sh stop)"; return; }
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
        warn "No server started by these scripts is running. Stop a manually started server with Ctrl+C."
    fi
}

server_status() {
    if server_is_up; then
        info "Listings server is running on port $PORT"
    else
        info "No listings server is responding on port $PORT"
        return 1
    fi
}
