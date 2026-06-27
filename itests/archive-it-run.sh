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
# Archive integration-test run artifacts for later analysis (e.g. LLM review).
#
# Invoke from anywhere:
#   ./itests/archive-it-run.sh
#   ./itests/archive-it-run.sh -m "Heavy macOS swap during run"
#   ./itests/archive-it-run.sh --tar -o /tmp/my-it-run.tar.gz
#   ./itests/compare-it-runs.sh --last 3
#   ./itests/archive-it-run.sh --full-karaf   # include full karaf.log (can be large)
#
# Default output: unexploded directory under archives/it-run-YYYYMMDD-HHMMSS/
# Use --tar or -o path ending in .tar.gz to produce a tarball instead.
#
# Shared modules: bootstrap (ui · lib · compare-lib) + karaf-lib + context-lib
# (auto-disabled when NO_COLOR, CI, or non-TTY).
#
# Included (high signal):
#   - failsafe-reports/     IT results (XML, txt, dumpstream, summary)
#   - surefire-reports/     module unit tests run before ITs (if present)
#   - exam/.../karaf.log[.N]   full segment(s) only with --full-karaf (rollover: karaf.log.1, .2, …)
#   - exam/.../karaf-log-segments.txt   rollover segment order (oldest → newest)
#   - exam/.../karaf-triage-summary.txt   LLM entry point for Karaf log analysis
#   - exam/.../karaf-failure-correlation.log   excerpts anchored on failing tests
#   - exam/.../karaf-exception-index.tsv   top recurring exceptions/errors
#   - exam/.../karaf-recent.log   full merged log (if ≤20k lines) or tail
#   - exam/.../karaf-errors-warnings.log   merged ERROR/WARN blocks + full stack traces
#   - elasticsearch-port.properties / opensearch-port.properties (engine + ports)
#   - elasticsearch0/logs, opensearch0/logs (Docker engine logs, if present)
#   - llm-it-run-analysis-guide.md, expected-karaf-log-patterns.txt
#   - exam/.../karaf-unexpected-candidates.log (errors not matching expected patterns)
#   - test-results.tsv, run-summary.properties, failed-tests.txt (LLM-friendly per-run test manifest)
#   - it-run-operator-note.txt (auto-generated operator note from build.sh)
#   - memory-samples.tsv, memory-summary.txt (JVM/system memory observed during IT run)
#   - run-context.txt, run-config/it-run-trace.properties (build/Maven/options trace)
#   - archives/runs-index.tsv (updated each capture — cross-run comparison index)
#   - comparison-last-3.txt, archives/latest-comparison.txt (auto when 2+ captures exist)
#   - manifest.txt
#
# Excluded (noise / huge): exam/system bundles, test-classes, jacoco, snapshots_repository
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
# shellcheck source=lib/it-run-bootstrap.sh disable=SC1091
source "$SCRIPT_DIR/lib/it-run-bootstrap.sh"
# shellcheck source=lib/it-run-karaf.sh disable=SC1091
source "$SCRIPT_DIR/lib/it-run-karaf.sh"
# shellcheck source=lib/it-run-memory.sh disable=SC1091
source "$SCRIPT_DIR/lib/it-run-memory.sh"
# shellcheck source=lib/it-run-context.sh disable=SC1091
source "$SCRIPT_DIR/lib/it-run-context.sh"

# --- configuration -----------------------------------------------------------

TARGET_DIR="target"
LLM_GUIDE="llm-it-run-analysis-guide.md"
MANIFEST_REL="manifest.txt"
RUN_TRACE_FILE="it-run-trace.properties"
RUN_CONTEXT_REL="run-context.txt"
FAILED_TESTS_REL="failed-tests.txt"

REPORT_TREES=(
    failsafe-reports
    surefire-reports
)

RUN_CONFIG_FILES=(
    elasticsearch-port.properties
    opensearch-port.properties
    it-run-trace.properties
    it-run-operator-note.txt
)

ENGINE_LOG_TREES=(
    elasticsearch0/logs
    opensearch0/logs
)

OUTPUT=""
CREATE_TAR=false
RUN_MESSAGE=""
FULL_KARAF=false
AUTO_COMPARE=true
FORCE_ARCHIVE=false
STAGING=""
STAGING_IS_TEMP=false
MANIFEST=""
PATTERNS_FILE=""
RUN_ID=""
RUN_FINGERPRINT=""
included=0

# --- CLI ---------------------------------------------------------------------

usage() {
    echo "Usage: $0 [-o DIR|ARCHIVE.tar.gz] [--tar] [-m MSG] [--message-file FILE] [--full-karaf] [--no-compare] [--force]"
    echo "  (default)     Unexploded directory: archives/it-run-YYYYMMDD-HHMMSS/"
    echo "  --tar         Write a .tar.gz instead (default name under archives/)"
    echo "  -o PATH       Output directory, or .tar.gz / .tgz archive path"
    echo "  -m, --message Operator note (default: it-run-operator-note.txt from build.sh)"
    echo "  --message-file  Read operator note from a file"
    echo "  --full-karaf  Include complete karaf.log and rollover segments (default: tail + filtered errors)"
    echo "  --no-compare  Skip auto compare of last 3 captures (default: on when 2+ runs exist)"
    echo "  --force       Archive even if this target/ run was already captured"
    exit 1
}


load_default_operator_note() {
    if [ -n "$RUN_MESSAGE" ]; then
        return 0
    fi
    if RUN_MESSAGE="$(it_load_default_operator_note "$TARGET_DIR" "$SCRIPT_DIR" 2>/dev/null)"; then
        ui_detail "Using auto-generated operator note from $IT_OPERATOR_NOTE_FILE"
        return 0
    fi
}

parse_args() {
    while [ $# -gt 0 ]; do
        case "$1" in
            -h) usage ;;
            -o)
                shift
                [ $# -gt 0 ] || usage
                OUTPUT="$1"
                ;;
            --tar) CREATE_TAR=true ;;
            -m | --message)
                shift
                [ $# -gt 0 ] || usage
                RUN_MESSAGE="$1"
                ;;
            --message-file)
                shift
                [ $# -gt 0 ] || usage
                [ -f "$1" ] || { ui_error "Message file not found: $1"; exit 1; }
                RUN_MESSAGE="$(cat "$1")"
                ;;
            --full-karaf) FULL_KARAF=true ;;
            --no-compare) AUTO_COMPARE=false ;;
            --force) FORCE_ARCHIVE=true ;;
            *) usage ;;
        esac
        shift
    done
}

# --- path helpers ------------------------------------------------------------

target_path() {
    echo "$TARGET_DIR/$1"
}

staging_path() {
    echo "$STAGING/$1"
}

# --- staging helpers ---------------------------------------------------------

mark_included() {
    included=1
}

note_file() {
    local rel="$1"
    local full size
    full="$(staging_path "$rel")"
    size="$(wc -c < "$full" 2>/dev/null | tr -d ' ')"
    echo "  $rel ($size bytes)" >> "$MANIFEST"
}

log_staged_file() {
    local rel="$1"
    local message="$2"
    note_file "$rel"
    ui_detail "$message"
}

register_staged_file() {
    local rel="$1"
    local message="$2"
    local full
    full="$(staging_path "$rel")"
    if [ -s "$full" ]; then
        log_staged_file "$rel" "$message"
        return 0
    fi
    rm -f "$full"
    return 1
}

copy_tree() {
    local src="$1"
    local dest_name="$2"
    local dest file_count
    if ! it_dir_has_files "$src"; then
        return
    fi
    dest="$(staging_path "$dest_name")"
    mkdir -p "$dest"
    cp -R "$src"/. "$dest/"
    file_count="$(find "$dest" -type f | wc -l | tr -d ' ')"
    echo "  $dest_name/ ($file_count files)" >> "$MANIFEST"
    mark_included
    ui_detail "Added $src"
}

copy_file() {
    local src="$1"
    local dest_rel="$2"
    local dest
    if [ ! -f "$src" ]; then
        return
    fi
    dest="$(staging_path "$dest_rel")"
    mkdir -p "$(dirname "$dest")"
    cp "$src" "$dest"
    log_staged_file "$dest_rel" "Added $src"
    mark_included
}

configure_output() {
    local timestamp
    timestamp="$(date +%Y%m%d-%H%M%S)"

    if [ -z "$OUTPUT" ]; then
        mkdir -p "$IT_ARCHIVES_DIR"
        if [ "$CREATE_TAR" = true ]; then
            OUTPUT="$IT_ARCHIVES_DIR/it-run-$timestamp.tar.gz"
        else
            OUTPUT="$IT_ARCHIVES_DIR/it-run-$timestamp"
        fi
    fi

    if [[ "$OUTPUT" == *.tar.gz ]] || [[ "$OUTPUT" == *.tgz ]]; then
        CREATE_TAR=true
    fi

    if [ "$CREATE_TAR" = true ]; then
        STAGING="$(mktemp -d "${TMPDIR:-/tmp}/unomi-it-archive.XXXXXX")"
        STAGING_IS_TEMP=true
    else
        mkdir -p "$OUTPUT"
        STAGING="$OUTPUT"
        STAGING_IS_TEMP=false
    fi

    RUN_ID="$(basename "$OUTPUT")"
    RUN_ID="${RUN_ID%.tar.gz}"
    RUN_ID="${RUN_ID%.tgz}"
}

setup_staging() {
    if [ "$STAGING_IS_TEMP" = true ]; then
        trap 'ui_spinner_cleanup; rm -rf "$STAGING"' EXIT
    else
        trap 'ui_spinner_cleanup' EXIT
    fi
    MANIFEST="$(staging_path "$MANIFEST_REL")"
    PATTERNS_FILE="$(staging_path "$KARAF_PATTERNS_REL")"
}

require_target_dir() {
    if [ -d "$TARGET_DIR" ]; then
        return
    fi
    ui_error "$SCRIPT_DIR/$TARGET_DIR not found. Run integration tests first."
    exit 1
}

reject_duplicate_archive() {
    local xml duplicate

    [ "$FORCE_ARCHIVE" = true ] && return 0

    if ! xml="$(it_target_failsafe_xml "$TARGET_DIR")"; then
        ui_detail "Duplicate check skipped (no failsafe XML in $TARGET_DIR)"
        return 0
    fi

    RUN_FINGERPRINT="$(it_compute_run_fingerprint_from_target \
        "$xml" "$REPO_ROOT" "$(target_path "$RUN_TRACE_FILE")")"

    if duplicate="$(it_find_duplicate_archive "$RUN_FINGERPRINT")"; then
        ui_warn "This IT run is already archived (same test outcomes, git commit, and build options)"
        ui_detail "Existing capture: $duplicate"
        ui_detail "Use --force to archive again anyway"
        exit 0
    fi
}

require_archived_content() {
    if [ "$included" -eq 0 ]; then
        ui_error "Nothing to archive under $TARGET_DIR."
        exit 1
    fi
}

# --- test results manifest (for cross-run / flaky analysis) ------------------

find_failsafe_xml() {
    local candidate
    if candidate="$(it_target_failsafe_xml "$TARGET_DIR")"; then
        echo "$candidate"
        return 0
    fi
    candidate="$(staging_path "failsafe-reports/$IT_FAILSAFE_XML")"
    if [ -f "$candidate" ]; then
        echo "$candidate"
        return 0
    fi
    return 1
}

extract_test_results() {
    local xml="$1"
    local tsv failed
    tsv="$(staging_path "$IT_TEST_RESULTS")"
    failed="$(staging_path "$FAILED_TESTS_REL")"
    : > "$failed"

    it_write_test_results_from_xml "$xml" "$tsv" "$failed"

    log_staged_file "$IT_TEST_RESULTS" "Wrote $IT_TEST_RESULTS"
    if [ -s "$failed" ]; then
        log_staged_file "$FAILED_TESTS_REL" "Wrote $FAILED_TESTS_REL"
    else
        rm -f "$failed"
    fi
    mark_included
}

write_run_summary() {
    local xml="$1"
    local summary failed_count trace summary_xml
    summary="$(staging_path "$IT_RUN_SUMMARY")"
    trace="$(target_path "$RUN_TRACE_FILE")"
    failed_count=0
    if [ -f "$(staging_path "$FAILED_TESTS_REL")" ]; then
        failed_count="$(wc -l < "$(staging_path "$FAILED_TESTS_REL")" | tr -d ' ')"
    fi

    {
        echo "# Per-run summary for cross-run comparison (see archives/$IT_RUNS_INDEX)"
        echo "run.id=$RUN_ID"
        echo "run.captured=$(it_utc_now)"
        echo "run.path=$OUTPUT"
        if [ -n "$RUN_MESSAGE" ]; then
            echo "operator.note=$RUN_MESSAGE"
        fi
        if it_git_in_repo "$REPO_ROOT"; then
            echo "git.commit=$(it_git_head "$REPO_ROOT")"
            echo "git.branch=$(it_git_branch "$REPO_ROOT")"
        fi
        if [ -f "$trace" ]; then
            it_grep_property_lines "$trace" "$IT_TRACE_FINGERPRINT_FIELDS"
        fi
        echo "search.engine=$(it_extract_xml_property "$xml" unomi.search.engine)"
        echo "elasticsearch.heap=$(it_extract_xml_property "$xml" elasticsearch.heap)"
        echo "opensearch.heap=$(it_extract_xml_property "$xml" opensearch.heap)"
        echo "it.karaf.heap=$(it_extract_xml_property "$xml" it.karaf.heap)"
        summary_xml="$(target_path "failsafe-reports/failsafe-summary.xml")"
        if [ -f "$summary_xml" ]; then
            echo "tests.completed=$(it_failsafe_summary_count "$summary_xml" completed)"
            echo "tests.failures=$(it_failsafe_summary_count "$summary_xml" failures)"
            echo "tests.errors=$(it_failsafe_summary_count "$summary_xml" errors)"
            echo "tests.skipped=$(it_failsafe_summary_count "$summary_xml" skipped)"
        fi
        echo "failed.tests.count=$failed_count"
        if [ -f "$(target_path "$IT_MEMORY_SUMMARY")" ]; then
            grep -E '^memory\.(peak|min|warning|samples|karaf|search)\.' \
                "$(target_path "$IT_MEMORY_SUMMARY")" 2>/dev/null || true
        fi
        if [ -n "$RUN_FINGERPRINT" ]; then
            echo "${IT_RUN_FINGERPRINT_FIELD}=$RUN_FINGERPRINT"
        fi
    } > "$summary"
    log_staged_file "$IT_RUN_SUMMARY" "Wrote $IT_RUN_SUMMARY"
    mark_included
}

capture_test_manifest() {
    local xml
    if ! xml="$(find_failsafe_xml)"; then
        ui_warn "No $IT_FAILSAFE_XML found — skipping test-results.tsv"
        return
    fi
    RUN_FINGERPRINT="$(it_ensure_run_fingerprint "$xml" "$REPO_ROOT" \
        "$(target_path "$RUN_TRACE_FILE")" "$RUN_FINGERPRINT")"
    extract_test_results "$xml"
    write_run_summary "$xml"
}

# --- archive assembly --------------------------------------------------------

archive_report_trees() {
    local name
    for name in "${REPORT_TREES[@]}"; do
        copy_tree "$(target_path "$name")" "$name"
    done
}

archive_run_config_files() {
    local name
    for name in "${RUN_CONFIG_FILES[@]}"; do
        copy_file "$(target_path "$name")" "run-config/$name"
    done
}

archive_engine_log_trees() {
    local name
    for name in "${ENGINE_LOG_TREES[@]}"; do
        copy_tree "$(target_path "$name")" "$name"
    done
}

archive_memory_artifacts() {
    local name samples summary
    for name in "$IT_MEMORY_SAMPLES" "$IT_MEMORY_SUMMARY" "$IT_MEMORY_SAMPLER_LOG"; do
        copy_file "$(target_path "$name")" "$name"
    done
    samples="$(target_path "$IT_MEMORY_SAMPLES")"
    summary="$(target_path "$IT_MEMORY_SUMMARY")"
    if [ -f "$samples" ] && [ ! -f "$summary" ]; then
        it_memory_summarize_samples "$samples" "$summary" && copy_file "$summary" "$IT_MEMORY_SUMMARY"
    fi
}

archive_test_artifacts() {
    archive_report_trees
    capture_test_manifest
    archive_run_config_files
    archive_engine_log_trees
    archive_memory_artifacts
    write_run_context
}

prepare_llm_context() {
    copy_file "$SCRIPT_DIR/$LLM_GUIDE" "$LLM_GUIDE"
    generate_expected_patterns
}

maybe_run_auto_compare() {
    local count tmp latest bundled

    [ "$AUTO_COMPARE" = true ] || return 0
    if [ ! -f "$(staging_path "$IT_TEST_RESULTS")" ]; then
        ui_detail "Cross-run compare skipped (no test-results.tsv in this capture)"
        return 0
    fi

    count="$(it_count_comparable_runs)"
    if [ "$count" -lt 2 ]; then
        ui_detail "Cross-run compare skipped (need 2+ captures with test-results.tsv)"
        return 0
    fi

    tmp="$(mktemp "${TMPDIR:-/tmp}/unomi-it-auto-compare.XXXXXX")"
    latest="$IT_ARCHIVES_DIR/$IT_LATEST_COMPARISON"
    bundled="$(staging_path "$IT_CAPTURE_COMPARISON")"

    if it_compare_last_to_file "$IT_AUTO_COMPARE_LAST" "$tmp"; then
        mkdir -p "$IT_ARCHIVES_DIR"
        cp "$tmp" "$latest"
        cp "$tmp" "$bundled"
        log_staged_file "$IT_CAPTURE_COMPARISON" \
            "Auto-compared last ${IT_AUTO_COMPARE_LAST} captures (or fewer) → archives/$IT_LATEST_COMPARISON"
        ui_detail "Cross-run compare: archives/$IT_LATEST_COMPARISON + $IT_CAPTURE_COMPARISON"
        it_compare_show_metrics "$tmp"
        mark_included
    else
        ui_detail "Cross-run compare skipped (comparison failed)"
    fi
    rm -f "$tmp"
}

count_staged_bytes() {
    local total
    total="$(find "$STAGING" -type f -exec wc -c {} + 2>/dev/null | tail -1 | awk '{print $1}')"
    if [ -z "$total" ]; then
        echo 0
    else
        echo "$total"
    fi
}

finalize_output() {
    if [ "$STAGING_IS_TEMP" = false ]; then
        it_update_runs_index
    fi
    maybe_run_auto_compare

    if [ "$CREATE_TAR" = true ]; then
        mkdir -p "$(dirname "$OUTPUT")"
        ui_spinner_run "Compressing archive..." tar -czf "$OUTPUT" -C "$STAGING" .
        ui_finish "$OUTPUT" "$(wc -c < "$OUTPUT" | tr -d ' ')"
        return
    fi
    ui_finish_dir "$OUTPUT" "$(count_staged_bytes)" "$(find "$STAGING" -type f | wc -l | tr -d ' ')"
}

# --- main --------------------------------------------------------------------

main() {
    it_run_entry_init "$SCRIPT_DIR"
    parse_args "$@"
    load_default_operator_note

    require_target_dir
    reject_duplicate_archive
    configure_output
    ui_banner
    setup_staging

    ui_phase "1/4" "Test reports, run context, and engine config"
    write_archive_manifest_header
    archive_test_artifacts

    ui_phase "2/4" "LLM triage guide and ignore patterns"
    prepare_llm_context

    ui_phase "3/4" "Karaf log mining"
    archive_karaf_logs

    require_archived_content

    ui_phase "4/4" "Finalize capture"
    write_archive_manifest_footer
    finalize_output
}

main "$@"
