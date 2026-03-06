#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_DIR="$ROOT_DIR/build/smoke-tests"
mkdir -p "$OUT_DIR"
LOG_FILE="$OUT_DIR/runclient-smoke-$(date +%Y%m%d-%H%M%S).log"
TIMEOUT_SECONDS="${1:-180}"
POLL_INTERVAL_SECONDS="${SMOKE_POLL_INTERVAL_SECONDS:-1}"
SHUTDOWN_GRACE_SECONDS="${SMOKE_SHUTDOWN_GRACE_SECONDS:-15}"

SUCCESS_PATTERNS=(
  "\\[FoundationSmoke] PREINIT complete\\."
  "\\[FoundationSmoke] CLIENT runtime hooks ready\\."
  "\\[FoundationSmoke] INIT complete\\."
  "\\[FoundationSmoke] POSTINIT complete\\."
  "Forge Mod Loader has successfully loaded"
)

RUN_PID=""
RUN_STATUS=0
RUN_STATUS_CAPTURED=0
RESULT="failed"

have_all_success_markers() {
  local pattern
  for pattern in "${SUCCESS_PATTERNS[@]}"; do
    if ! grep -q "$pattern" "$LOG_FILE"; then
      return 1
    fi
  done
  return 0
}

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

GRADLE_CMD=(./gradlew --no-daemon runClient --console=plain)
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

echo "[TACZ-Legacy] smoke launched (pid=$RUN_PID); waiting for startup markers..."

deadline=$((SECONDS + TIMEOUT_SECONDS))
while true; do
  if have_all_success_markers; then
    RESULT="pass"
    echo "[TACZ-Legacy] startup markers reached; shutting down client automatically..."
    stop_process_group
    break
  fi

  if ! process_alive; then
    collect_run_status
    break
  fi

  if (( SECONDS >= deadline )); then
    RESULT="timeout"
    echo "[TACZ-Legacy] smoke timed out before startup markers appeared. Log: $LOG_FILE" >&2
    stop_process_group
    break
  fi

  sleep "$POLL_INTERVAL_SECONDS"
done

if [[ "$RESULT" != "pass" ]] && have_all_success_markers; then
  RESULT="pass"
fi

if [[ "$RESULT" == "pass" ]]; then
  echo "[TACZ-Legacy] smoke pass: startup completed and client was stopped automatically. Log: $LOG_FILE"
  trap - EXIT INT TERM
  exit 0
fi

echo "[TACZ-Legacy] smoke failed with status $RUN_STATUS. Log: $LOG_FILE" >&2
exit 1
