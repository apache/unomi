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
# Compare two or more IT run captures to classify systematic vs flaky failures.
#
# Usage:
#   ./compare-it-runs.sh archives/it-run-20260101-120000 archives/it-run-20260102-120000
#   ./compare-it-runs.sh --last 3
#   ./compare-it-runs.sh --last 5 -o /tmp/comparison.txt
#
# Reads test-results.tsv from each run directory (produced by archive-it-run.sh).
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/it-run-bootstrap.sh disable=SC1091
source "$SCRIPT_DIR/lib/it-run-bootstrap.sh"

OUTPUT=""
LAST_N=0
QUIET=false
RUN_DIRS=()

usage() {
    echo "Usage: $0 RUN_DIR [RUN_DIR ...]"
    echo "       $0 --last N"
    echo "  -o FILE     Write report to FILE (default: stdout; required with --quiet)"
    echo "  --quiet      No banner or progress (for use from archive-it-run.sh)"
    exit 1
}

parse_args() {
    local runs=()
    while [ $# -gt 0 ]; do
        case "$1" in
            -h) usage ;;
            -o)
                shift
                [ $# -gt 0 ] || usage
                OUTPUT="$1"
                ;;
            --last)
                shift
                [ $# -gt 0 ] || usage
                [[ "$1" =~ ^[0-9]+$ ]] || { echo "ERROR: --last requires a positive integer" >&2; usage; }
                LAST_N="$1"
                ;;
            --quiet) QUIET=true ;;
            *)
                runs+=("$1")
                ;;
        esac
        shift
    done

    if [ "$LAST_N" -gt 0 ]; then
        while IFS= read -r dir; do
            [ -n "$dir" ] && runs+=("$dir")
        done < <(it_find_comparable_run_dirs "$LAST_N")
    fi

    if [ "${#runs[@]}" -lt 2 ]; then
        if [ "$QUIET" = true ]; then
            exit 1
        fi
        ui_error "Need at least two run directories to compare."
        usage
    fi

    if [ "$QUIET" = true ] && [ -z "$OUTPUT" ]; then
        echo "ERROR: --quiet requires -o FILE" >&2
        exit 1
    fi

    RUN_DIRS=("${runs[@]}")
}

main() {
    it_run_entry_init "$SCRIPT_DIR"
    parse_args "$@"
    it_compare_cli_run "$LAST_N" "$QUIET" "$OUTPUT" "${RUN_DIRS[@]}"
}

main "$@"
