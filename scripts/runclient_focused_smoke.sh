#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_DIR="$ROOT_DIR/build/smoke-tests"
mkdir -p "$OUT_DIR"
LOG_FILE="$OUT_DIR/runclient-focused-smoke-$(date +%Y%m%d-%H%M%S).log"
TIMEOUT_SECONDS="${1:-300}"
POLL_INTERVAL_SECONDS="${SMOKE_POLL_INTERVAL_SECONDS:-1}"
SHUTDOWN_GRACE_SECONDS="${SMOKE_SHUTDOWN_GRACE_SECONDS:-15}"
WORLD_FOLDER="${FOCUSED_SMOKE_WORLD_FOLDER:-tacz_focused_smoke_auto}"
WORLD_NAME="${FOCUSED_SMOKE_WORLD_NAME:-tacz_focused_smoke_auto}"
EXPLOSIVE_GUN="${FOCUSED_SMOKE_EXPLOSIVE_GUN:-tacz:rpg7}"
AUDIO_BACKEND="${TACZ_AUDIO_BACKEND:-diagnostic}"
AUDIO_PREFLIGHT="${TACZ_AUDIO_PREFLIGHT:-true}"
AUDIO_PREFLIGHT_STRICT="${TACZ_AUDIO_PREFLIGHT_STRICT:-false}"

SUCCESS_PATTERN="\\[FocusedSmoke] PASS"
FAIL_PATTERN="\\[FocusedSmoke] FAIL"

RUN_PID=""
RUN_STATUS=0
RUN_STATUS_CAPTURED=0
RESULT="failed"

process_alive() {
  [[ -n "$RUN_PID" ]] && kill -0 "$RUN_PID" 2>/dev/null
}

collect_run_status() {
  if [[ -z "$RUN_PID" || $RUN_STATUS_CAPTURED -eq 1 ]]; then
    return
  fi
  set +e
  wait "$RUN_PID"
  RUN_STATUS=$?
  set -e
  RUN_STATUS_CAPTURED=1
}

terminate_process_group() {
  local signal="$1"
  if [[ -z "$RUN_PID" ]]; then
    return
  fi
  kill "-$signal" -- "-$RUN_PID" 2>/dev/null || true
}

stop_process_group() {
  if ! process_alive; then
    collect_run_status
    return
  fi

  terminate_process_group TERM

  local deadline=$((SECONDS + SHUTDOWN_GRACE_SECONDS))
  while process_alive && (( SECONDS < deadline )); do
    sleep 1
  done

  if process_alive; then
    terminate_process_group KILL
  fi

  collect_run_status
}

cleanup() {
  local exit_code=$?
  if [[ "$RESULT" != "pass" ]]; then
    stop_process_group
  fi
  exit "$exit_code"
}

trap cleanup EXIT INT TERM

cd "$ROOT_DIR"

GRADLE_CMD=(
  ./gradlew
  --no-daemon
  -Dtacz.focusedSmoke=true
  -Dtacz.focusedSmoke.autoWorld=true
  "-Dtacz.focusedSmoke.worldFolder=${WORLD_FOLDER}"
  "-Dtacz.focusedSmoke.worldName=${WORLD_NAME}"
  "-Dtacz.focusedSmoke.explosiveGun=${EXPLOSIVE_GUN}"
  "-Dtacz.audio.backend=${AUDIO_BACKEND}"
  "-Dtacz.audio.preflight=${AUDIO_PREFLIGHT}"
  "-Dtacz.audio.preflight.strict=${AUDIO_PREFLIGHT_STRICT}"
  runClient
  --console=plain
)
if command -v xvfb-run >/dev/null 2>&1; then
  RUN_CMD=(xvfb-run -a "${GRADLE_CMD[@]}")
else
  RUN_CMD=("${GRADLE_CMD[@]}")
fi

: > "$LOG_FILE"

set +e
setsid stdbuf -oL -eL "${RUN_CMD[@]}" > >(tee -a "$LOG_FILE") 2> >(tee -a "$LOG_FILE" >&2) &
RUN_PID=$!
set -e

echo "[TACZ-Legacy] focused smoke launched (pid=$RUN_PID, audioBackend=$AUDIO_BACKEND, preflight=$AUDIO_PREFLIGHT, strict=$AUDIO_PREFLIGHT_STRICT); waiting for in-world markers..."

deadline=$((SECONDS + TIMEOUT_SECONDS))
while true; do
  if grep -q "$SUCCESS_PATTERN" "$LOG_FILE"; then
    RESULT="pass"
    echo "[TACZ-Legacy] focused smoke pass marker reached; shutting down client automatically..."
    stop_process_group
    break
  fi

  if grep -q "$FAIL_PATTERN" "$LOG_FILE"; then
    RESULT="failed"
    echo "[TACZ-Legacy] focused smoke reported failure. Log: $LOG_FILE" >&2
    stop_process_group
    break
  fi

  if ! process_alive; then
    collect_run_status
    break
  fi

  if (( SECONDS >= deadline )); then
    RESULT="timeout"
    echo "[TACZ-Legacy] focused smoke timed out before PASS/FAIL marker appeared. Log: $LOG_FILE" >&2
    stop_process_group
    break
  fi

  sleep "$POLL_INTERVAL_SECONDS"
done

if [[ "$RESULT" == "pass" ]]; then
  echo "[TACZ-Legacy] focused smoke pass: animation/projectile/explosion diagnostic chain completed. Log: $LOG_FILE"
  trap - EXIT INT TERM
  exit 0
fi

echo "[TACZ-Legacy] focused smoke failed with status $RUN_STATUS. Log: $LOG_FILE" >&2
exit 1
