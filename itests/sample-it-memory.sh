#!/bin/bash
################################################################################
#
#    Licensed to the Apache Software Foundation (ASF) under one or more
#    contributor license agreements.  See the NOTICE file distributed with
#    this work for additional information regarding copyright ownership.
#    The ASF licenses this file to You under the Apache License, Version 2.0
#    (the "License"); you may not use this file except in compliance with
#    the License.  You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#    Unless required by applicable law or agreed to in writing, software
#    distributed under the License is distributed on an "AS IS" BASIS,
#    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#    See the License for the specific language governing permissions and
#    limitations under the License.
#
################################################################################
#
# Sample JVM and system memory while integration tests run.
#
# Usage:
#   ./itests/sample-it-memory.sh start [--target-dir itests/target] [--interval 30]
#   ./itests/sample-it-memory.sh stop  [--target-dir itests/target]
#   ./itests/sample-it-memory.sh sample-once [--target-dir itests/target]
#   ./itests/sample-it-memory.sh summarize [--target-dir itests/target]
#   ./itests/sample-it-memory.sh operator-note [--target-dir itests/target] [--print-only]
#   ./itests/sample-it-memory.sh verify [--target-dir itests/target]
# Started automatically by ./build.sh --integration-tests (disable with --no-memory-sampler).
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/it-run.sh disable=SC1091
source "$SCRIPT_DIR/lib/it-run.sh"
# shellcheck source=lib/it-run-memory.sh disable=SC1091
source "$SCRIPT_DIR/lib/it-run-memory.sh"

TARGET_DIR="$SCRIPT_DIR/target"
INTERVAL=30
SEARCH_PORT=""
PRINT_ONLY=false
COMMAND=""

usage() {
    cat <<EOF
Usage: $0 <command> [options]

Commands:
  start         Run the sampler in the background until "stop" is called
  stop          Stop the background sampler and write memory-summary.txt
  sample-once   Print one TSV sample line to stdout
  summarize     Build memory-summary.txt from memory-samples.tsv
  operator-note Write it-run-operator-note.txt from trace + test + memory data
  verify        Cross-platform sanity checks (macOS + Linux)

Options:
  --target-dir DIR   IT target directory (default: itests/target)
  --interval SEC     Sample interval in seconds for start (default: 30)
  --port PORT        Search engine HTTP port override
  --print-only       With operator-note: print to stdout instead of writing file
  -h, --help         Show this help
EOF
    exit 1
}

parse_args() {
    COMMAND="${1:-}"
    shift || true

    while [ $# -gt 0 ]; do
        case "$1" in
            --target-dir)
                shift
                TARGET_DIR="${1:-}"
                ;;
            --interval)
                shift
                INTERVAL="${1:-30}"
                ;;
            --port)
                shift
                SEARCH_PORT="${1:-}"
                ;;
            --print-only)
                PRINT_ONLY=true
                ;;
            -h | --help)
                usage
                ;;
            *)
                echo "Unknown option: $1" >&2
                usage
                ;;
        esac
        shift
    done

    if [ -z "$COMMAND" ]; then
        usage
    fi

    mkdir -p "$TARGET_DIR"
    if [ -n "$SEARCH_PORT" ]; then
        IT_SEARCH_PORT="$SEARCH_PORT"
        export IT_SEARCH_PORT
    fi
}

sampler_paths() {
    SAMPLES_FILE="$TARGET_DIR/$IT_MEMORY_SAMPLES"
    SUMMARY_FILE="$TARGET_DIR/$IT_MEMORY_SUMMARY"
    PID_FILE="$TARGET_DIR/$IT_MEMORY_SAMPLER_PID"
    STOP_FILE="$TARGET_DIR/$IT_MEMORY_SAMPLER_STOP"
    LOG_FILE="$TARGET_DIR/$IT_MEMORY_SAMPLER_LOG"
}

sampler_loop() {
    local interval="$1"
    sampler_paths
    it_memory_write_samples_header "$SAMPLES_FILE"

    while [ ! -f "$STOP_FILE" ]; do
        it_memory_sample_once "$TARGET_DIR" >> "$SAMPLES_FILE" || true
        sleep "$interval"
    done
}

cmd_start() {
    sampler_paths

    if [ -f "$PID_FILE" ]; then
        existing_pid="$(cat "$PID_FILE" 2>/dev/null || true)"
        if [ -n "$existing_pid" ] && kill -0 "$existing_pid" 2>/dev/null; then
            echo "Memory sampler already running (pid $existing_pid)" >&2
            exit 0
        fi
        rm -f "$PID_FILE"
    fi

    rm -f "$STOP_FILE"
    it_memory_clear_sampler_cache "$TARGET_DIR"
    it_memory_write_samples_header "$SAMPLES_FILE"
    echo "# started $(date -u +%Y-%m-%dT%H:%M:%SZ) interval=${INTERVAL}s target=$TARGET_DIR" >> "$LOG_FILE"

    (
        sampler_loop "$INTERVAL"
    ) >> "$LOG_FILE" 2>&1 &

    echo $! > "$PID_FILE"
    echo "Memory sampler started (pid $(cat "$PID_FILE"), interval ${INTERVAL}s)"
    echo "Samples: $SAMPLES_FILE"
}

cmd_stop() {
    sampler_paths

    if [ ! -f "$PID_FILE" ]; then
        echo "Memory sampler is not running" >&2
        cmd_summarize || true
        return 0
    fi

    pid="$(cat "$PID_FILE" 2>/dev/null || true)"
    touch "$STOP_FILE"

    if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then
        kill -TERM "$pid" 2>/dev/null || true
        wait "$pid" 2>/dev/null || true
    fi

    rm -f "$PID_FILE" "$STOP_FILE"
    echo "# stopped $(date -u +%Y-%m-%dT%H:%M:%SZ)" >> "$LOG_FILE"
    cmd_summarize || true
    echo "Memory sampler stopped"
}

cmd_sample_once() {
    sampler_paths
    it_memory_write_samples_header "$SAMPLES_FILE"
    it_memory_sample_once "$TARGET_DIR"
}

cmd_summarize() {
    sampler_paths
    if it_memory_summarize_samples "$SAMPLES_FILE" "$SUMMARY_FILE"; then
        echo "Wrote $SUMMARY_FILE"
        return 0
    fi
    echo "No memory samples to summarize in $SAMPLES_FILE" >&2
    return 1
}

cmd_operator_note() {
    if [ "$PRINT_ONLY" = true ]; then
        it_generate_operator_note "$TARGET_DIR" "$SCRIPT_DIR"
        return 0
    fi
    if it_write_operator_note_file "$TARGET_DIR" "$SCRIPT_DIR"; then
        echo "Wrote $TARGET_DIR/$IT_OPERATOR_NOTE_FILE"
        return 0
    fi
    echo "Could not generate operator note (missing $IT_RUN_TRACE in $TARGET_DIR)" >&2
    return 1
}

cmd_verify() {
    it_memory_verify "$TARGET_DIR" "$SCRIPT_DIR"
}

main() {
    parse_args "$@"
    case "$COMMAND" in
        start) cmd_start ;;
        stop) cmd_stop ;;
        sample-once) cmd_sample_once ;;
        summarize) cmd_summarize ;;
        operator-note) cmd_operator_note ;;
        verify) cmd_verify ;;
        *)
            echo "Unknown command: $COMMAND" >&2
            usage
            ;;
    esac
}

main "$@"
