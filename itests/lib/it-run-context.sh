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
# Run context and manifest assembly for archive-it-run.sh.
# Expects archive hooks: target_path, staging_path, log_staged_file, mark_included,
# and globals: SCRIPT_DIR, REPO_ROOT, TARGET_DIR, MANIFEST, OUTPUT, RUN_MESSAGE,
# RUN_ID, FULL_KARAF, CREATE_TAR, RUN_CONTEXT_REL, RUN_TRACE_FILE.
# Requires it-run-memory.sh to be sourced by the caller (archive-it-run.sh).

context_append_inferred_maven_properties() {
    local engine
    engine="$(it_infer_search_engine "$TARGET_DIR")"
    echo "inferred.search.engine=$engine"
    echo "# POM profile defaults (actual run may override via build.sh -D flags; see it-run-trace.properties when present)"
    echo "pom.default.elasticsearch.heap=$(it_read_pom_profile_property "$SCRIPT_DIR/pom.xml" elasticsearch elasticsearch.heap)"
    echo "pom.default.opensearch.heap=$(it_read_pom_profile_property "$SCRIPT_DIR/pom.xml" opensearch opensearch.heap)"
    echo "pom.default.karaf.heap.elasticsearch=$(it_read_pom_profile_property "$SCRIPT_DIR/pom.xml" elasticsearch karaf.heap)"
    echo "pom.default.karaf.heap.opensearch=$(it_read_pom_profile_property "$SCRIPT_DIR/pom.xml" opensearch karaf.heap)"
    echo "pom.default.elasticsearch.port=$(it_read_pom_profile_property "$SCRIPT_DIR/pom.xml" elasticsearch elasticsearch.port)"
    echo "pom.default.opensearch.port=$(it_read_pom_profile_property "$SCRIPT_DIR/pom.xml" opensearch opensearch.port)"
}

context_append_engine_port_files() {
    local port_file path

    for port_file in "${IT_ENGINE_PORT_FILES[@]}"; do
        path="$(target_path "$port_file")"
        if [ -f "$path" ]; then
            echo "# $port_file"
            cat "$path"
        fi
    done
}

context_append_system_snapshot() {
    echo "snapshot.time=$(it_utc_now)"
    echo "snapshot.host=$(it_hostname)"
    echo "snapshot.uname=$(uname -a 2>/dev/null || echo unknown)"
    if command -v uptime >/dev/null 2>&1; then
        echo "snapshot.uptime=$(uptime 2>/dev/null | sed 's/^[[:space:]]*//')"
    fi
    if command -v df >/dev/null 2>&1; then
        echo "snapshot.disk.target=$(df -h "$SCRIPT_DIR" 2>/dev/null | tail -1)"
        echo "snapshot.disk.output=$(df -h "$OUTPUT" 2>/dev/null | tail -1)"
    fi
    if [ -n "${MAVEN_OPTS:-}" ]; then
        echo "snapshot.maven.opts=$MAVEN_OPTS"
    fi
    if [ -n "${MAVEN_CMD_LINE_ARGS:-}" ]; then
        echo "snapshot.maven.cmdline.args=$MAVEN_CMD_LINE_ARGS"
    fi
    if [ "$(uname -s 2>/dev/null)" = Darwin ] && command -v vm_stat >/dev/null 2>&1; then
        echo "snapshot.vmstat.begin"
        vm_stat | grep -E 'page size|Pages free|Pages active|Pages inactive|Pages wired|Pages occupied by compressor|Swapins|Swapouts' \
            || vm_stat | head -12
        echo "snapshot.vmstat.end"
    fi
    if [ "$(uname -s 2>/dev/null)" = Darwin ] && command -v memory_pressure >/dev/null 2>&1; then
        echo "snapshot.memory_pressure=$(memory_pressure 2>/dev/null | head -1)"
    fi
    it_memory_append_system_snapshot
}

context_emit_manifest_git_lines() {
    if ! it_git_in_repo "$REPO_ROOT"; then
        return
    fi
    echo "Git branch: $(it_git_branch "$REPO_ROOT")"
    echo "Git commit: $(it_git_head "$REPO_ROOT")"
}

context_emit_manifest_java_line() {
    if command -v java >/dev/null 2>&1; then
        echo "Java: $(java -version 2>&1 | head -1)"
    fi
}

write_archive_manifest_header() {
    {
        echo "Apache Unomi integration-test run archive"
        echo "Created: $(it_utc_now)"
        echo "Host: $(it_hostname)"
        echo "itests directory: $SCRIPT_DIR"
        context_emit_manifest_git_lines
        context_emit_manifest_java_line
        echo "Karaf log mode: $([ "$FULL_KARAF" = true ] && echo full || echo tail+filtered)"
        echo
        echo "Included paths:"
    } > "$MANIFEST"
}

write_archive_manifest_footer() {
    {
        echo
        echo "Excluded (intentionally): exam/system bundles, test-classes, jacoco, snapshots_repository"
        if [ -n "$RUN_MESSAGE" ]; then
            echo
            echo "Operator note:"
            echo "$RUN_MESSAGE"
        fi
        if [ "$CREATE_TAR" = true ]; then
            echo "Output format: tar.gz -> $OUTPUT"
        else
            echo "Output format: directory -> $OUTPUT"
        fi
    } >> "$MANIFEST"
}

write_run_context() {
    local out trace
    out="$(staging_path "$RUN_CONTEXT_REL")"
    trace="$(target_path "$RUN_TRACE_FILE")"
    {
        echo "# Integration test run context"
        echo "# Read this first when triaging with an LLM or reviewing locally."
        echo "# For flaky vs systematic failures across runs: test-results.tsv + archives/$IT_RUNS_INDEX"
        echo "# Compare runs: $IT_CAPTURE_COMPARISON (auto) or ./compare-it-runs.sh --last $IT_AUTO_COMPARE_LAST"
        echo
        echo "## Operator notes"
        if [ -n "$RUN_MESSAGE" ]; then
            echo "$RUN_MESSAGE"
        else
            echo "$IT_RUN_CONTEXT_NO_OPERATOR_NOTE"
        fi
        echo
        echo "## Build / Maven trace"
        if [ -f "$trace" ]; then
            cat "$trace"
        else
            echo "# it-run-trace.properties not found in target/"
            echo "# Run integration tests via ./build.sh --integration-tests to capture build.sh and Maven options."
            echo
            context_append_inferred_maven_properties
        fi
        echo
        echo "## Docker port mappings (from target)"
        context_append_engine_port_files
        echo
        echo "## System snapshot (at archive time — not at IT start)"
        context_append_system_snapshot
        echo
        it_memory_append_context_section \
            "$(target_path "$IT_MEMORY_SUMMARY")" \
            "$(target_path "$IT_MEMORY_SAMPLES")"
    } > "$out"
    log_staged_file "$RUN_CONTEXT_REL" "Wrote $RUN_CONTEXT_REL"
    mark_included
}
