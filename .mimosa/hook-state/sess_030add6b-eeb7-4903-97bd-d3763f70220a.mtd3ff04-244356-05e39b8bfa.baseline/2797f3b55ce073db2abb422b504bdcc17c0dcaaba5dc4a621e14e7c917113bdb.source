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
# Cross-run comparison engine (sourced by compare-it-runs.sh and archive-it-run.sh).

IT_COMPARE_LEGEND=(
    "systematic: FAIL/ERROR in every run where the test appears"
    "flaky: mix of PASS and FAIL/ERROR across runs"
    "regression: PASS in an earlier run, FAIL/ERROR in the latest run"
    "fixed: FAIL/ERROR in an earlier run, PASS in the latest run"
)

it_compare_sort_run_dirs() {
    local run_dir
    local -a unsorted=("$@")
    local -a lines=()
    local captured

    for run_dir in "${unsorted[@]}"; do
        captured="$(it_read_run_summary_field "$run_dir" run.captured)"
        if [ -z "$captured" ]; then
            captured="$(it_run_label "$run_dir")"
        fi
        lines+=("${captured}"$'\t'"${run_dir}")
    done

    printf '%s\n' "${lines[@]}" | sort -t $'\t' -k1,1 | cut -f2-
}

it_compare_write_header() {
    local report_file="$1"
    local run_count="$2"
    local labels_line="$3"
    local item

    {
        echo "# IT run comparison"
        echo "# Runs (${run_count}): ${labels_line}"
        echo "# Generated: $(it_utc_now)"
        echo
        echo "## Legend"
        for item in "${IT_COMPARE_LEGEND[@]}"; do
            echo "- ${item}"
        done
        echo
    } > "$report_file"
}

it_compare_write_metadata() {
    local report_file="$1"
    local run_dir label

    for run_dir in "${@:2}"; do
        label="$(it_run_label "$run_dir")"
        {
            echo "## Run: $label"
            it_append_run_summary_excerpt "$run_dir"
            echo
        } >> "$report_file"
    done
}

it_compare_write_analysis() {
    local report_file="$1"
    local labels_line="$2"
    shift 2
    local -a tsv_files=("$@")

    awk -v runs="$labels_line" '
    function test_id(key, parts) {
        split(key, parts, "\t")
        return parts[1] "." parts[2]
    }
    function get_status(r, id) {
        return ((r SUBSEP id) in status) ? status[r, id] : "MISSING"
    }
    function is_bad(st) {
        return st == "FAIL" || st == "ERROR"
    }
    function print_section(title, count, lines,    i) {
        print title
        if (count == 0) {
            print "(none)"
        } else {
            for (i = 1; i <= count; i++) {
                print lines[i]
            }
        }
        print ""
    }
    BEGIN {
        n = split(runs, run_labels, " ")
        latest = n
    }
    FNR == 1 { run_idx++; next }
    NF >= 3 {
        key = $1 "\t" $2
        if (!(key in seen)) {
            seen[key] = 1
            order[++order_count] = key
        }
        id = test_id(key, parts)
        status[run_idx, id] = $3
    }
    END {
        for (i = 1; i <= order_count; i++) {
            key = order[i]
            id = test_id(key, parts)
            bad = 0
            good = 0
            seen_runs = 0
            detail = ""
            for (r = 1; r <= n; r++) {
                st = get_status(r, id)
                if (st != "MISSING") {
                    seen_runs++
                }
                if (is_bad(st)) {
                    bad++
                } else if (st == "PASS") {
                    good++
                }
                if (r > 1) {
                    detail = detail ", "
                }
                detail = detail run_labels[r] "=" st
            }
            latest_st = get_status(latest, id)

            if (seen_runs == n && bad == n) {
                systematic[++systematic_count] = "- " id " (" n "/" n " runs)"
            }
            if (bad > 0 && good > 0) {
                flaky[++flaky_count] = "- " id " (" detail ")"
            }
            if (is_bad(latest_st)) {
                for (r = 1; r < latest; r++) {
                    prev = get_status(r, id)
                    if (prev == "PASS") {
                        regression[++regression_count] = "- " id " (was PASS in " run_labels[r] ", now " latest_st " in " run_labels[latest] ")"
                        break
                    }
                }
            }
            if (latest_st == "PASS") {
                for (r = 1; r < latest; r++) {
                    prev = get_status(r, id)
                    if (is_bad(prev)) {
                        fixed[++fixed_count] = "- " id " (was " prev " in " run_labels[r] ", now PASS)"
                        break
                    }
                }
            }
        }

        print_section("## Systematic failures (fail/error in every run)", systematic_count, systematic)
        print_section("## Flaky tests (mixed pass and fail/error)", flaky_count, flaky)
        print_section("## Regressions (pass -> fail/error vs latest run: " run_labels[latest] ")", regression_count, regression)
        print_section("## Fixed since earlier runs (fail/error -> pass in " run_labels[latest] ")", fixed_count, fixed)

        print "## Totals"
        print "systematic=" systematic_count + 0
        print "flaky=" flaky_count + 0
        print "regression=" regression_count + 0
        print "fixed=" fixed_count + 0
    }' "${tsv_files[@]}" >> "$report_file"
}

it_compare_write_report() {
    local report_file="$1"
    shift
    local -a run_dirs=("$@")
    local -a labels=()
    local -a tsv_files=()
    local run_dir

    for run_dir in "${run_dirs[@]}"; do
        labels+=("$(it_run_label "$run_dir")")
        tsv_files+=("$run_dir/$IT_TEST_RESULTS")
    done

    it_compare_write_header "$report_file" "${#run_dirs[@]}" "${labels[*]}"
    it_compare_write_metadata "$report_file" "${run_dirs[@]}"
    it_compare_write_analysis "$report_file" "${labels[*]}" "${tsv_files[@]}"
}

it_compare_show_metrics() {
    local report_file="$1"

    ui_metric "systematic" "$(it_read_comparison_total "$report_file" systematic)"
    ui_metric "flaky" "$(it_read_comparison_total "$report_file" flaky)"
    ui_metric "regression" "$(it_read_comparison_total "$report_file" regression)"
    ui_metric "fixed" "$(it_read_comparison_total "$report_file" fixed)"
}

it_compare_validate_run_dirs() {
    local show_details="$1"
    shift
    local run_dir

    for run_dir in "$@"; do
        it_validate_run_dir "$run_dir"
        if [ "$show_details" = true ]; then
            ui_detail "Loaded $(it_summarize_run_dir "$run_dir")"
        fi
    done
}

it_compare_last_to_file() {
    local count="$1"
    local report_file="$2"
    local -a run_dirs=()
    local dir

    while IFS= read -r dir; do
        [ -n "$dir" ] && run_dirs+=("$dir")
    done < <(it_find_comparable_run_dirs "$count")

    if [ "${#run_dirs[@]}" -lt 2 ]; then
        return 1
    fi

    it_compare_write_report "$report_file" "${run_dirs[@]}"
}

it_compare_runs_to_file() {
    local report_file="$1"
    shift
    local -a run_dirs=()

    if [ "$#" -lt 2 ]; then
        return 1
    fi

    while IFS= read -r dir; do
        [ -n "$dir" ] && run_dirs+=("$dir")
    done < <(it_compare_sort_run_dirs "$@")

    it_compare_write_report "$report_file" "${run_dirs[@]}"
}

it_compare_publish_report() {
    local tmp="$1"
    local output="${2:-}"

    if [ -n "$output" ]; then
        cp "$tmp" "$output"
        ui_finish_report "$output" "$(wc -c < "$output" | tr -d ' ')"
        return
    fi

    if ui__color_enabled; then
        ui_detail "Report follows:"
        echo
    fi
    cat "$tmp"
}

it_compare_write_to_temp() {
    local tmp="$1"
    local last_n="$2"
    shift 2
    local -a run_dirs=("$@")

    if [ "$last_n" -gt 0 ]; then
        it_compare_last_to_file "$last_n" "$tmp"
    else
        it_compare_runs_to_file "$tmp" "${run_dirs[@]}"
    fi
}

it_compare_cli_run() {
    local last_n="$1"
    local quiet="$2"
    local output="$3"
    shift 3
    local -a run_dirs=("$@")
    local compare_tmp=""
    local show_details=false

    if [ "$quiet" != true ]; then
        show_details=true
        ui_banner_compare
        ui_phase "1/2" "Load run captures"
    fi
    it_compare_validate_run_dirs "$show_details" "${run_dirs[@]}"

    compare_tmp="$(mktemp "${TMPDIR:-/tmp}/unomi-it-compare.XXXXXX")"
    trap 'ui_spinner_cleanup; rm -f "$compare_tmp"' EXIT

    if [ "$quiet" = true ]; then
        it_compare_write_to_temp "$compare_tmp" "$last_n" "${run_dirs[@]}"
        cp "$compare_tmp" "$output"
    else
        ui_phase "2/2" "Classify failures"
        ui_spinner_run "Comparing test results across ${#run_dirs[@]} runs..." \
            it_compare_write_to_temp "$compare_tmp" "$last_n" "${run_dirs[@]}"
        it_compare_show_metrics "$compare_tmp"
        it_compare_publish_report "$compare_tmp" "$output"
    fi

    rm -f "$compare_tmp"
    trap - EXIT
}
