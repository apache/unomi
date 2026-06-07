# Shared helpers for IT run scripts (sourced, not executed).

IT_SCRIPT_DIR=""
IT_ARCHIVES_DIR=""

IT_ARCHIVES_DIR_NAME="archives"
IT_RUN_DIR_GLOB="it-run-*"
IT_TEST_RESULTS="test-results.tsv"
IT_RUN_SUMMARY="run-summary.properties"
IT_RUNS_INDEX="runs-index.tsv"
IT_FAILSAFE_XML="TEST-org.apache.unomi.itests.AllITs.xml"
IT_LATEST_COMPARISON="latest-comparison.txt"
IT_CAPTURE_COMPARISON="comparison-last-3.txt"
IT_AUTO_COMPARE_LAST=3
IT_RUN_FINGERPRINT_FIELD="run.fingerprint"
IT_TRACE_FINGERPRINT_FIELDS='^(search\.engine|search\.heap|karaf\.heap|single\.test|use\.opensearch|maven\.exit\.code)='
IT_RUN_SUMMARY_EXCERPT_FIELDS='^(run\.captured|search\.engine|it\.karaf\.heap|elasticsearch\.heap|opensearch\.heap|tests\.failures|tests\.errors|operator\.note)='
IT_ENGINE_PORT_FILES=(
    elasticsearch-port.properties
    opensearch-port.properties
)
IT_RUN_CONTEXT_NO_OPERATOR_NOTE='(none — pass --message "..." to describe run conditions, e.g. heavy swap, CI runner, single-test rerun)'

it_run_lib_init() {
    IT_SCRIPT_DIR="$1"
    IT_ARCHIVES_DIR="$IT_SCRIPT_DIR/$IT_ARCHIVES_DIR_NAME"
}

it_run_tools_init() {
    local script_dir="$1"
    cd "$script_dir"
    it_run_lib_init "$script_dir"
}

it_utc_now() {
    date -u +%Y-%m-%dT%H:%M:%SZ
}

it_hostname() {
    hostname 2>/dev/null || echo unknown
}

it_dir_has_files() {
    [ -d "$1" ] && [ -n "$(ls -A "$1" 2>/dev/null)" ]
}

it_extract_xml_property() {
    local file="$1"
    local property="$2"

    grep -m1 "name=\"$property\"" "$file" 2>/dev/null \
        | sed -n 's/.*value="\([^"]*\)".*/\1/p'
}

it_read_pom_profile_property() {
    local pom="$1"
    local profile="$2"
    local property="$3"

    awk -v profile="$profile" -v prop="$property" '
        $0 ~ "<id>" profile "</id>" { in_profile=1 }
        in_profile && $0 ~ "<" prop ">" {
            gsub(/.*<[^>]+>/, "")
            gsub(/<.*/, "")
            print
            exit
        }
        in_profile && $0 ~ "</profile>" { exit }
    ' "$pom"
}

it_infer_search_engine() {
    local target_dir="$1"

    if it_dir_has_files "$target_dir/opensearch0/logs"; then
        echo "opensearch"
    elif it_dir_has_files "$target_dir/elasticsearch0/logs"; then
        echo "elasticsearch"
    elif [ -f "$target_dir/opensearch-port.properties" ]; then
        echo "opensearch"
    elif [ -f "$target_dir/elasticsearch-port.properties" ]; then
        echo "elasticsearch"
    else
        echo "unknown"
    fi
}

it_run_label() {
    basename "$1"
}

it_read_properties_field() {
    local file="$1"
    local field="$2"

    if [ ! -f "$file" ]; then
        echo ""
        return
    fi
    grep -m1 "^${field}=" "$file" 2>/dev/null | cut -d= -f2-
}

it_read_run_summary_field() {
    it_read_properties_field "$1/$IT_RUN_SUMMARY" "$2"
}

it_validate_run_dir() {
    local dir="$1"

    if [ ! -d "$dir" ]; then
        ui_error "Not a directory: $dir"
        exit 1
    fi
    if [ ! -f "$dir/$IT_TEST_RESULTS" ]; then
        ui_error "Missing $IT_TEST_RESULTS in $dir (run archive-it-run.sh first)"
        exit 1
    fi
}

it_find_archive_run_dirs() {
    find "$IT_ARCHIVES_DIR" -maxdepth 1 -type d -name "$IT_RUN_DIR_GLOB" 2>/dev/null | sort -r
}

it_collect_comparable_run_dirs() {
    local max_count="$1"
    local -a dirs=()
    local dir

    while IFS= read -r dir; do
        [ -n "$dir" ] || continue
        [ -f "$dir/$IT_TEST_RESULTS" ] || continue
        dirs+=("$dir")
        if [ "$max_count" -gt 0 ] && [ "${#dirs[@]}" -ge "$max_count" ]; then
            break
        fi
    done < <(it_find_archive_run_dirs)

    printf '%s\n' "${dirs[@]}"
}

# Newest N captures that have test-results.tsv, returned oldest → newest (for compare).
it_find_comparable_run_dirs() {
    local count="$1"
    local -a dirs=()
    local i

    while IFS= read -r dir; do
        [ -n "$dir" ] && dirs+=("$dir")
    done < <(it_collect_comparable_run_dirs "$count")

    for ((i = ${#dirs[@]} - 1; i >= 0; i--)); do
        printf '%s\n' "${dirs[i]}"
    done
}

it_count_comparable_runs() {
    it_collect_comparable_run_dirs 0 | wc -l | tr -d ' '
}

it_read_comparison_total() {
    local value
    value="$(it_read_properties_field "$1" "$2")"
    if [ -z "$value" ]; then
        echo "0"
    else
        echo "$value"
    fi
}

it_append_run_summary_excerpt() {
    local run_dir="$1"

    if [ ! -f "$run_dir/$IT_RUN_SUMMARY" ]; then
        return
    fi
    grep -E "$IT_RUN_SUMMARY_EXCERPT_FIELDS" "$run_dir/$IT_RUN_SUMMARY" 2>/dev/null || true
}

it_failsafe_testcase_awk() {
    cat <<'AWK'
function attr(line, key,    pos, rest) {
    pos = index(line, key "=\"")
    if (pos == 0) return ""
    rest = substr(line, pos + length(key) + 2)
    sub(/".*/, "", rest)
    return rest
}
/<testcase / {
    current = attr($0, "classname") "\t" attr($0, "name")
    if (current == "\t") current = ""
    if (current != "") {
        status[current] = "PASS"
        elapsed[current] = attr($0, "time")
    }
}
current != "" && /<failure/ { status[current] = "FAIL" }
current != "" && /<error/   { status[current] = "ERROR" }
current != "" && /<skipped/ { status[current] = "SKIP" }
AWK
}

it_write_test_results_from_xml() {
    local xml="$1"
    local tsv="$2"
    local failed="${3:-}"

    awk "$(it_failsafe_testcase_awk)
    END {
        print \"test_class\ttest_method\tstatus\telapsed_s\"
        for (t in status) {
            split(t, parts, \"\\t\")
            print parts[1] \"\\t\" parts[2] \"\\t\" status[t] \"\\t\" elapsed[t]
        }
    }" "$xml" | sort -t $'\t' -k1,1 -k2,2 > "$tsv"

    if [ -n "$failed" ]; then
        awk -F '\t' '$3 == "FAIL" || $3 == "ERROR" { print $1 "." $2 }' "$tsv" >> "$failed"
    fi
}

it_test_outcomes_fingerprint_from_xml() {
    local xml="$1"

    awk "$(it_failsafe_testcase_awk)
    END {
        for (t in status) {
            split(t, parts, \"\\t\")
            print parts[1] \"\\t\" parts[2] \"\\t\" status[t]
        }
    }" "$xml" | sort -t $'\t' -k1,1 -k2,2 | it_hash_lines
}

it_summarize_run_dir() {
    local run_dir="$1"
    local label failures errors

    label="$(it_run_label "$run_dir")"
    failures="$(it_read_run_summary_field "$run_dir" tests.failures)"
    errors="$(it_read_run_summary_field "$run_dir" tests.errors)"
    failures="${failures:-?}"
    errors="${errors:-?}"
    printf '%s (failures=%s errors=%s)' "$label" "$failures" "$errors"
}

it_hash_lines() {
    shasum -a 256 2>/dev/null | awk '{print $1}'
}

it_git_head() {
    local repo_root="$1"

    if command -v git >/dev/null 2>&1 \
        && git -C "$repo_root" rev-parse HEAD >/dev/null 2>&1; then
        git -C "$repo_root" rev-parse HEAD
    else
        echo "unknown"
    fi
}

it_git_branch() {
    local repo_root="$1"

    if command -v git >/dev/null 2>&1 \
        && git -C "$repo_root" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
        git -C "$repo_root" rev-parse --abbrev-ref HEAD 2>/dev/null || echo unknown
    else
        echo "unknown"
    fi
}

it_git_in_repo() {
    local repo_root="$1"
    command -v git >/dev/null 2>&1 \
        && git -C "$repo_root" rev-parse --is-inside-work-tree >/dev/null 2>&1
}

it_target_failsafe_xml() {
    local target_dir="$1"
    local path="$target_dir/failsafe-reports/$IT_FAILSAFE_XML"

    if [ -f "$path" ]; then
        echo "$path"
        return 0
    fi
    return 1
}

it_grep_property_lines() {
    local file="$1"
    local pattern="$2"

    if [ ! -f "$file" ]; then
        return 0
    fi
    grep -E "$pattern" "$file" 2>/dev/null || true
}

it_failsafe_summary_count() {
    local summary_xml="$1"
    local tag="$2"

    if [ ! -f "$summary_xml" ]; then
        echo ""
        return
    fi
    grep -m1 "<${tag}>" "$summary_xml" 2>/dev/null | sed 's/[^0-9]//g'
}

it_ensure_run_fingerprint() {
    local xml="$1"
    local repo_root="$2"
    local trace_file="$3"
    local current_fp="$4"

    if [ -n "$current_fp" ]; then
        echo "$current_fp"
        return
    fi
    it_compute_run_fingerprint_from_target "$xml" "$repo_root" "$trace_file"
}


it_test_outcomes_fingerprint_from_tsv() {
    local tsv="$1"

    awk -F '\t' 'NR > 1 { print $1 "\t" $2 "\t" $3 }' "$tsv" \
        | sort -t $'\t' -k1,1 -k2,2 \
        | it_hash_lines
}

it_trace_fingerprint_from_file() {
    local trace_file="$1"

    if [ ! -f "$trace_file" ]; then
        echo "none"
        return
    fi
    grep -E "$IT_TRACE_FINGERPRINT_FIELDS" "$trace_file" 2>/dev/null | sort | it_hash_lines
}

it_trace_fingerprint_from_summary() {
    it_trace_fingerprint_from_file "$1/$IT_RUN_SUMMARY"
}

it_build_run_fingerprint() {
    local git_commit="$1"
    local outcomes_fp="$2"
    local trace_fp="$3"

    printf '%s\n%s\n%s\n' "$git_commit" "$outcomes_fp" "$trace_fp" | it_hash_lines
}

it_compute_run_fingerprint_from_target() {
    local xml="$1"
    local repo_root="$2"
    local trace_file="$3"

    it_build_run_fingerprint \
        "$(it_git_head "$repo_root")" \
        "$(it_test_outcomes_fingerprint_from_xml "$xml")" \
        "$(it_trace_fingerprint_from_file "$trace_file")"
}

it_compute_run_fingerprint_from_archive() {
    local run_dir="$1"
    local stored git_commit

    stored="$(it_read_run_summary_field "$run_dir" "$IT_RUN_FINGERPRINT_FIELD")"
    if [ -n "$stored" ]; then
        echo "$stored"
        return 0
    fi

    if [ ! -f "$run_dir/$IT_TEST_RESULTS" ]; then
        return 1
    fi

    git_commit="$(it_read_run_summary_field "$run_dir" git.commit)"
    if [ -z "$git_commit" ]; then
        git_commit="unknown"
    fi

    it_build_run_fingerprint \
        "$git_commit" \
        "$(it_test_outcomes_fingerprint_from_tsv "$run_dir/$IT_TEST_RESULTS")" \
        "$(it_trace_fingerprint_from_summary "$run_dir")"
}

it_find_duplicate_archive() {
    local fingerprint="$1"
    local run_dir existing_fp

    [ -n "$fingerprint" ] || return 1

    while IFS= read -r run_dir; do
        [ -n "$run_dir" ] || continue
        existing_fp="$(it_compute_run_fingerprint_from_archive "$run_dir")" || continue
        if [ "$existing_fp" = "$fingerprint" ]; then
            echo "$run_dir"
            return 0
        fi
    done < <(it_find_archive_run_dirs)

    return 1
}

it_sanitize_index_field() {
    local value="$1"
    value="${value//$'\t'/ }"
    value="${value//$'\n'/ }"
    printf '%s' "$value"
}

it_update_runs_index() {
    local index run_dir captured git_commit engine failures errors failed_count note
    local karaf_heap search_heap run_id

    mkdir -p "$IT_ARCHIVES_DIR"
    index="$IT_ARCHIVES_DIR/$IT_RUNS_INDEX"
    {
        echo -e "run_id\tcaptured_utc\tgit_commit\tsearch_engine\tkaraf_heap\tsearch_heap\ttests_failures\ttests_errors\tfailed_tests_count\toperator_note\trun_path"
        for run_dir in "$IT_ARCHIVES_DIR"/$IT_RUN_DIR_GLOB; do
            [ -d "$run_dir" ] || continue
            [ -f "$run_dir/$IT_RUN_SUMMARY" ] || continue
            run_id="$(it_run_label "$run_dir")"
            captured="$(it_read_run_summary_field "$run_dir" run.captured)"
            git_commit="$(it_read_run_summary_field "$run_dir" git.commit)"
            engine="$(it_read_run_summary_field "$run_dir" search.engine)"
            karaf_heap="$(it_read_run_summary_field "$run_dir" it.karaf.heap)"
            search_heap="$(it_read_run_summary_field "$run_dir" elasticsearch.heap)"
            if [ -z "$search_heap" ]; then
                search_heap="$(it_read_run_summary_field "$run_dir" opensearch.heap)"
            fi
            failures="$(it_read_run_summary_field "$run_dir" tests.failures)"
            errors="$(it_read_run_summary_field "$run_dir" tests.errors)"
            failed_count="$(it_read_run_summary_field "$run_dir" failed.tests.count)"
            note="$(it_sanitize_index_field "$(it_read_run_summary_field "$run_dir" operator.note)")"
            echo -e "${run_id}\t${captured}\t${git_commit}\t${engine}\t${karaf_heap}\t${search_heap}\t${failures}\t${errors}\t${failed_count}\t${note}\t${run_dir}"
        done
    } > "$index"
    ui_detail "Updated $index"
}
