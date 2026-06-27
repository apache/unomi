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
# JVM and system memory sampling helpers for integration test runs.
# Requires lib/it-run.sh to be sourced first (shared engine + POM helpers).

IT_MEMORY_SAMPLES="memory-samples.tsv"
IT_MEMORY_SUMMARY="memory-summary.txt"
IT_MEMORY_SAMPLER_PID="memory-sampler.pid"
IT_MEMORY_SAMPLER_STOP="memory-sampler.stop"
IT_MEMORY_SAMPLER_LOG="memory-sampler.log"
IT_MEMORY_SAMPLER_CACHE="memory-sampler.cache"
IT_MEMORY_SWAP_PRESSURE_MB=2048

IT_MEMORY_TSV_HEADER=$'timestamp_utc\tkaraf_pid\tkaraf_heap_used_mb\tkaraf_heap_max_mb\tkaraf_gct_s\tes_heap_used_mb\tes_heap_max_mb\tdocker_rss_mb\tsystem_mem_available_mb\tsystem_swap_used_mb\tsystem_load_1m'

_IT_MEMORY_OS=""

it_memory_os_name() {
    if [ -z "$_IT_MEMORY_OS" ]; then
        _IT_MEMORY_OS="$(uname -s 2>/dev/null || echo unknown)"
    fi
    echo "$_IT_MEMORY_OS"
}

it_memory_is_linux() {
    [ "$(it_memory_os_name)" = Linux ]
}

it_memory_is_darwin() {
    [ "$(it_memory_os_name)" = Darwin ]
}

it_memory_parse_heap_setting_mb() {
    local value="${1:-}"
    local number suffix

    value="$(echo "$value" | tr -d '[:space:]')"
    if [ -z "$value" ]; then
        echo "0"
        return
    fi

    suffix="${value: -1}"
    number="${value%?}"
    case "$suffix" in
        g | G) awk -v n="$number" 'BEGIN { printf "%d", int(n * 1024 + 0.5) }' ;;
        m | M) awk -v n="$number" 'BEGIN { printf "%d", int(n + 0.5) }' ;;
        [0-9]) awk -v n="$value" 'BEGIN { printf "%d", int(n / 1048576 + 0.5) }' ;;
        *) echo "0" ;;
    esac
}

it_memory_parse_jvm_flag_bytes() {
    local output="$1"
    local flag="$2"

    echo "$output" | sed -n "s/.*${flag}[[:space:]]*=[[:space:]]*\([0-9][0-9]*\).*/\1/p" | head -1
}

it_memory_read_trace_heap_mb() {
    local target_dir="$1"
    local trace_field="$2"
    local pom_profile="$3"
    local pom_field="$4"
    local script_dir trace_file configured

    script_dir="$(cd "$(dirname "$target_dir")" && pwd)"
    trace_file="$target_dir/it-run-trace.properties"
    if [ -z "${it_resolve_configured_heap:-}" ] || [ ! -f "$script_dir/pom.xml" ]; then
        echo "0"
        return
    fi
    configured="$(it_resolve_configured_heap "$trace_file" "$trace_field" "$script_dir/pom.xml" "$pom_profile" "$pom_field")"
    it_memory_parse_heap_setting_mb "$configured"
}

it_memory_detect_karaf_max_mb() {
    local target_dir="$1"
    local pid="$2"
    local cap_mb_from_jstat="${3:-0}"
    local max_bytes max_mb jinfo_out jcmd_out profile

    max_mb="0"

    if command -v jinfo >/dev/null 2>&1; then
        jinfo_out="$(jinfo -flag MaxHeapSize "$pid" 2>/dev/null || true)"
        max_bytes="$(it_memory_parse_jvm_flag_bytes "$jinfo_out" "MaxHeapSize")"
        if [ -n "$max_bytes" ]; then
            max_mb="$(it_memory_mb_from_bytes "$max_bytes")"
        fi
    fi

    if [ "$max_mb" = "0" ] && command -v jcmd >/dev/null 2>&1; then
        jcmd_out="$(jcmd "$pid" VM.flags 2>/dev/null || true)"
        max_bytes="$(it_memory_parse_jvm_flag_bytes "$jcmd_out" "MaxHeapSize")"
        if [ -n "$max_bytes" ]; then
            max_mb="$(it_memory_mb_from_bytes "$max_bytes")"
        fi
    fi

    if [ "$max_mb" = "0" ] && [ "$cap_mb_from_jstat" -gt 0 ] 2>/dev/null; then
        max_mb="$cap_mb_from_jstat"
    fi

    if [ "$max_mb" = "0" ]; then
        profile="$(it_search_engine_pom_profile "$(it_memory_resolve_search_engine "$target_dir")")"
        max_mb="$(it_memory_read_trace_heap_mb "$target_dir" "karaf.heap" "$profile" "karaf.heap")"
    fi

    echo "$max_mb"
}

it_memory_jstat_gc_metrics() {
    local pid="$1"

    if ! command -v jstat >/dev/null 2>&1; then
        echo -e "0\t0\t0"
        return
    fi

    jstat -gc "$pid" 2>/dev/null | awk '
        NR == 1 {
            for (i = 1; i <= NF; i++) {
                h[$i] = i
            }
            next
        }
        NR == 2 {
            if ("S0U" in h) {
                used_kb = $(h["S0U"]) + $(h["S1U"]) + $(h["EU"]) + $(h["OU"])
            } else {
                used_kb = $3 + $4 + $6 + $8
            }
            if ("S0C" in h) {
                cap_kb = $(h["S0C"]) + $(h["S1C"]) + $(h["EC"]) + $(h["OC"])
            } else {
                cap_kb = $1 + $2 + $5 + $7
            }
            if ("GCT" in h) {
                gct = $(h["GCT"]) + 0
            } else {
                gct = $NF + 0
            }
            printf "%d\t%d\t%.3f", int(used_kb * 1024 / 1048576 + 0.5), int(cap_kb * 1024 / 1048576 + 0.5), gct
        }'
}

it_memory_system_mem_available_mb() {
    local pagesize

    if it_memory_is_linux && command -v free >/dev/null 2>&1; then
        free -m 2>/dev/null | awk '/^Mem:/ {
            if ($7 != "") {
                print $7
            } else {
                print $4
            }
            exit
        }'
        return
    fi

    if it_memory_is_darwin && command -v vm_stat >/dev/null 2>&1; then
        pagesize="$(sysctl -n hw.pagesize 2>/dev/null || echo 4096)"
        vm_stat 2>/dev/null | awk -v ps="$pagesize" '
            /Pages free/ { gsub(/\./, "", $3); free = $3 + 0 }
            /Pages inactive/ { gsub(/\./, "", $3); inactive = $3 + 0 }
            END {
                printf "%d", int((free + inactive) * ps / 1048576 + 0.5)
            }'
        return
    fi

    echo "0"
}

it_memory_system_swap_used_mb() {
    if it_memory_is_linux && command -v free >/dev/null 2>&1; then
        free -m 2>/dev/null | awk '/^Swap:/ { print $3; exit }'
        return
    fi

    if it_memory_is_darwin; then
        sysctl -n vm.swapusage 2>/dev/null | sed -n 's/.* used = \([0-9.]*\)M.*/\1/p' | awk '{ printf "%d", int($1 + 0.5) }'
        return
    fi

    echo "0"
}

it_memory_system_load_1m() {
    if it_memory_is_linux && [ -r /proc/loadavg ]; then
        awk '{ print $1 + 0 }' /proc/loadavg
        return
    fi

    if it_memory_is_darwin && command -v sysctl >/dev/null 2>&1; then
        sysctl -n vm.loadavg 2>/dev/null | tr -d '{}\n' | awk '{ print $1 + 0 }'
        return
    fi

    if command -v uptime >/dev/null 2>&1; then
        uptime 2>/dev/null | sed -n 's/.*load average[s]*: //p' | cut -d, -f1 | tr -d ' '
        return
    fi

    echo "0"
}

it_memory_sampler_cache_file() {
    echo "$1/$IT_MEMORY_SAMPLER_CACHE"
}

it_memory_read_cache_field() {
    local cache_file="$1"
    local field="$2"
    if [ ! -f "$cache_file" ]; then
        return 1
    fi
    grep -m1 "^${field}=" "$cache_file" 2>/dev/null | cut -d= -f2-
}

it_memory_write_cache_field() {
    local cache_file="$1"
    local field="$2"
    local value="$3"
    local tmp

    mkdir -p "$(dirname "$cache_file")"
    touch "$cache_file"
    tmp="$(mktemp "${TMPDIR:-/tmp}/unomi-mem-cache.XXXXXX")"
    grep -v "^${field}=" "$cache_file" > "$tmp" 2>/dev/null || true
    printf '%s=%s\n' "$field" "$value" >> "$tmp"
    mv "$tmp" "$cache_file"
}

it_memory_clear_sampler_cache() {
    rm -f "$(it_memory_sampler_cache_file "$1")"
}

it_memory_resolve_search_engine() {
    local target_dir="$1"
    local engine

    if [ -n "${IT_SEARCH_ENGINE:-}" ]; then
        echo "$IT_SEARCH_ENGINE"
        return
    fi
    if [ -n "${it_infer_search_engine:-}" ]; then
        engine="$(it_infer_search_engine "$target_dir")"
        if [ -n "$engine" ] && [ "$engine" != "unknown" ]; then
            echo "$engine"
            return
        fi
    fi
    echo "elasticsearch"
}

it_memory_resolve_docker_container() {
    local target_dir="$1"
    local cache_file engine container default_container

    cache_file="$(it_memory_sampler_cache_file "$target_dir")"
    container="$(it_memory_read_cache_field "$cache_file" docker_container || true)"
    if [ -n "$container" ]; then
        echo "$container"
        return
    fi

    engine="$(it_memory_resolve_search_engine "$target_dir")"
    default_container="$(it_search_engine_docker_container "$engine")"

    if command -v docker >/dev/null 2>&1; then
        container="$(docker ps --filter "name=${default_container}" --format '{{.Names}}' 2>/dev/null | head -1)"
    fi

    if [ -z "$container" ]; then
        echo "$default_container"
        return
    fi

    it_memory_write_cache_field "$cache_file" docker_container "$container"
    echo "$container"
}

it_memory_mb_from_bytes() {
    local bytes="$1"
    if [ -z "$bytes" ] || [ "$bytes" = "0" ]; then
        echo "0"
        return
    fi
    echo $(( (bytes + 524288) / 1048576 ))
}

it_memory_json_number() {
    local json="$1"
    local field="$2"
    echo "$json" | sed -n "s/.*\"${field}\":\([0-9][0-9]*\).*/\1/p" | head -1
}

it_memory_resolve_search_port() {
    local target_dir="$1"
    local engine port_file port_line

    engine="$(it_memory_resolve_search_engine "$target_dir")"
    port_file="$target_dir/$(it_search_engine_port_properties_file "$engine")"

    if [ -f "$port_file" ]; then
        port_line="$(grep -m1 '\.port=' "$port_file" 2>/dev/null | cut -d= -f2- | tr -d '[:space:]')"
        if [ -n "$port_line" ]; then
            echo "$port_line"
            return
        fi
    fi

    if [ -n "${IT_SEARCH_PORT:-}" ]; then
        echo "$IT_SEARCH_PORT"
        return
    fi

    it_search_engine_default_port "$engine"
}

it_memory_parse_docker_mem_to_mb() {
    local raw="${1:-}"

    case "$raw" in
        *GiB) echo "${raw%GiB}" | awk '{printf "%d", $1 * 1024 + 0.5}' ;;
        *MiB) echo "${raw%MiB}" | awk '{printf "%d", $1 + 0.5}' ;;
        *KiB) echo "${raw%KiB}" | awk '{printf "%d", int($1 / 1024 + 0.5)}' ;;
        *G) echo "${raw%G}" | awk '{printf "%d", $1 * 1024 + 0.5}' ;;
        *M) echo "${raw%M}" | awk '{printf "%d", $1 + 0.5}' ;;
        *K) echo "${raw%K}" | awk '{printf "%d", int($1 / 1024 + 0.5)}' ;;
        *) echo "0" ;;
    esac
}

it_memory_find_karaf_pid() {
    pgrep -f 'org.apache.karaf.main.Main' 2>/dev/null | head -1
}

it_memory_karaf_max_mb_cached() {
    local target_dir="$1"
    local pid="$2"
    local cap_mb_from_jstat="${3:-0}"
    local cache_file cached_pid max_mb

    cache_file="$(it_memory_sampler_cache_file "$target_dir")"
    cached_pid="$(it_memory_read_cache_field "$cache_file" karaf_pid || true)"
    if [ "$cached_pid" = "$pid" ]; then
        max_mb="$(it_memory_read_cache_field "$cache_file" karaf_max_mb || true)"
        if [ -n "$max_mb" ] && [ "$max_mb" -gt 0 ] 2>/dev/null; then
            echo "$max_mb"
            return
        fi
    fi

    max_mb="$(it_memory_detect_karaf_max_mb "$target_dir" "$pid" "$cap_mb_from_jstat")"
    if [ -z "$max_mb" ]; then
        max_mb="0"
    fi

    it_memory_write_cache_field "$cache_file" karaf_pid "$pid"
    if [ "$max_mb" -gt 0 ] 2>/dev/null; then
        it_memory_write_cache_field "$cache_file" karaf_max_mb "$max_mb"
    else
        it_memory_write_cache_field "$cache_file" karaf_max_mb ""
    fi
    echo "$max_mb"
}

it_memory_karaf_stats() {
    local target_dir="$1"
    local pid="$2"
    local used_mb max_mb cap_mb gct_s jstat_line

    if [ -z "$pid" ]; then
        echo -e "0\t0\t0"
        return
    fi

    if ! kill -0 "$pid" 2>/dev/null; then
        echo -e "0\t0\t0"
        return
    fi

    used_mb="0"
    cap_mb="0"
    gct_s="0"
    jstat_line="$(it_memory_jstat_gc_metrics "$pid")"
    if [ -n "$jstat_line" ]; then
        read -r used_mb cap_mb gct_s <<EOF
$jstat_line
EOF
    fi

    max_mb="$(it_memory_karaf_max_mb_cached "$target_dir" "$pid" "$cap_mb")"

    if [ -z "${used_mb:-}" ] || [ "$used_mb" = "0" ]; then
        if command -v ps >/dev/null 2>&1; then
            used_mb="$(ps -o rss= -p "$pid" 2>/dev/null | awk '{print int($1/1024+0.5)}')"
        fi
    fi

    if [ -z "${max_mb:-}" ] || [ "$max_mb" = "0" ]; then
        max_mb="0"
    fi
    if [ -z "${used_mb:-}" ]; then
        used_mb="0"
    fi
    if [ -z "${gct_s:-}" ]; then
        gct_s="0"
    fi

    echo -e "${used_mb}\t${max_mb}\t${gct_s}"
}

it_memory_search_engine_stats() {
    local port="$1"
    local json used_bytes max_bytes

    json="$(curl -sf --max-time 5 \
        "http://localhost:${port}/_nodes/stats/jvm?filter_path=nodes.*.jvm.mem.heap_used_in_bytes,nodes.*.jvm.mem.heap_max_in_bytes" \
        2>/dev/null || true)"
    if [ -z "$json" ]; then
        echo -e "0\t0"
        return
    fi

    used_bytes="$(it_memory_json_number "$json" "heap_used_in_bytes")"
    max_bytes="$(it_memory_json_number "$json" "heap_max_in_bytes")"
    echo -e "$(it_memory_mb_from_bytes "${used_bytes:-0}")\t$(it_memory_mb_from_bytes "${max_bytes:-0}")"
}

it_memory_docker_rss_mb() {
    local target_dir="$1"
    local container rss

    if ! command -v docker >/dev/null 2>&1; then
        echo "0"
        return
    fi

    container="$(it_memory_resolve_docker_container "$target_dir")"
    rss="$(docker stats --no-stream --format '{{.MemUsage}}' "$container" 2>/dev/null | head -1 | cut -d/ -f1 | tr -d ' ')"

    it_memory_parse_docker_mem_to_mb "$rss"
}

it_memory_system_stats() {
    local mem_available swap_used load_1m

    mem_available="$(it_memory_system_mem_available_mb)"
    swap_used="$(it_memory_system_swap_used_mb)"
    load_1m="$(it_memory_system_load_1m)"

    echo -e "${mem_available:-0}\t${swap_used:-0}\t${load_1m:-0}"
}

it_memory_sample_once() {
    local target_dir="$1"
    local port="${2:-}"
    local karaf_pid karaf_line es_line sys_line

    port="${port:-$(it_memory_resolve_search_port "$target_dir")}"
    karaf_pid="$(it_memory_find_karaf_pid)"
    karaf_line="$(it_memory_karaf_stats "$target_dir" "$karaf_pid")"
    es_line="$(it_memory_search_engine_stats "$port")"
    sys_line="$(it_memory_system_stats)"

    printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
        "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
        "${karaf_pid:-0}" \
        "$karaf_line" \
        "$es_line" \
        "$(it_memory_docker_rss_mb "$target_dir")" \
        "$sys_line"
}

it_memory_write_samples_header() {
    local samples_file="$1"
    if [ ! -f "$samples_file" ] || [ ! -s "$samples_file" ]; then
        printf '%s\n' "$IT_MEMORY_TSV_HEADER" > "$samples_file"
    fi
}

it_memory_summarize_samples() {
    local samples_file="$1"
    local summary_file="$2"

    if [ ! -f "$samples_file" ] || [ ! -s "$samples_file" ]; then
        return 1
    fi

    awk -F'\t' -v summary="$summary_file" -v swap_pressure_mb="$IT_MEMORY_SWAP_PRESSURE_MB" '
        NR == 1 { next }
        NF < 11 { next }
        {
            samples++
            if ($2+0 > 0) {
                if ($3+0 > peak_karaf_used) peak_karaf_used = $3+0
                if ($4+0 > peak_karaf_max) peak_karaf_max = $4+0
                if ($5+0 > peak_karaf_gct_s) peak_karaf_gct_s = $5+0
            }
            if ($6+0 > peak_es_used) peak_es_used = $6+0
            if ($7+0 > peak_es_max) peak_es_max = $7+0
            if ($8+0 > peak_docker_rss) peak_docker_rss = $8+0
            if ($9+0 > 0 && (min_mem_avail == 0 || $9+0 < min_mem_avail)) min_mem_avail = $9+0
            if ($10+0 > peak_swap) peak_swap = $10+0
            if ($11+0 > peak_load) peak_load = $11+0
            if (samples == 1) first_swap = $10+0
            last_swap = $10+0
        }
        END {
            if (samples == 0) exit 1
            karaf_headroom = (peak_karaf_max > 0 ? (peak_karaf_max - peak_karaf_used) * 100 / peak_karaf_max : 0)
            es_headroom = (peak_es_max > 0 ? (peak_es_max - peak_es_used) * 100 / peak_es_max : 0)
            swap_pressure = (peak_swap+0 > 0 && min_mem_avail+0 > 0 && min_mem_avail+0 < swap_pressure_mb+0) || (last_swap+0 > first_swap+0 + 256)
            printf("# Integration test memory summary (generated from %s)\n", FILENAME) > summary
            printf("memory.samples.count=%d\n", samples) >> summary
            printf("memory.peak.karaf.heap.used.mb=%d\n", peak_karaf_used+0) >> summary
            printf("memory.peak.karaf.heap.max.mb=%d\n", peak_karaf_max+0) >> summary
            printf("memory.peak.karaf.gct.s=%.2f\n", peak_karaf_gct_s+0) >> summary
            printf("memory.peak.search.heap.used.mb=%d\n", peak_es_used+0) >> summary
            printf("memory.peak.search.heap.max.mb=%d\n", peak_es_max+0) >> summary
            printf("memory.peak.docker.rss.mb=%d\n", peak_docker_rss+0) >> summary
            printf("memory.min.system.mem.available.mb=%d\n", min_mem_avail+0) >> summary
            printf("memory.peak.system.swap.used.mb=%d\n", peak_swap+0) >> summary
            printf("memory.peak.system.load.1m=%.2f\n", peak_load+0) >> summary
            printf("memory.karaf.headroom.pct=%d\n", karaf_headroom+0) >> summary
            printf("memory.search.headroom.pct=%d\n", es_headroom+0) >> summary
            if (swap_pressure) {
                printf("memory.warning.swap.detected=true\n") >> summary
            } else {
                printf("memory.warning.swap.detected=false\n") >> summary
            }
            if (peak_karaf_max > 0 && peak_karaf_used * 100 / peak_karaf_max >= 85) {
                printf("memory.warning.karaf.heap.high=true\n") >> summary
            } else {
                printf("memory.warning.karaf.heap.high=false\n") >> summary
            }
            if (peak_es_max > 0 && peak_es_used * 100 / peak_es_max >= 85) {
                printf("memory.warning.search.heap.high=true\n") >> summary
            } else {
                printf("memory.warning.search.heap.high=false\n") >> summary
            }
        }
    ' "$samples_file"
}

it_memory_append_context_section() {
    local summary_file="$1"
    local samples_file="$2"

    if [ -f "$summary_file" ]; then
        echo "## Memory summary (observed during IT run)"
        cat "$summary_file"
        echo
    elif [ -f "$samples_file" ]; then
        echo "## Memory samples"
        echo "# memory-summary.txt missing; raw samples available at $(basename "$samples_file")"
        echo "memory.samples.lines=$(($(wc -l < "$samples_file" 2>/dev/null | tr -d ' ') - 1))"
        echo
    fi
}

it_memory_append_system_snapshot() {
    if it_memory_is_linux && command -v free >/dev/null 2>&1; then
        echo "snapshot.mem.linux=$(free -h 2>/dev/null | awk '/^Mem:/ {print $0}')"
        echo "snapshot.swap.linux=$(free -h 2>/dev/null | awk '/^Swap:/ {print $0}')"
    fi
    echo "snapshot.mem.available.mb=$(it_memory_system_mem_available_mb)"
    echo "snapshot.swap.used.mb=$(it_memory_system_swap_used_mb)"
    echo "snapshot.load.1m=$(it_memory_system_load_1m)"
}

it_memory_verify() {
    local target_dir="$1"
    local script_dir="${2:-$(cd "$(dirname "$target_dir")" && pwd)}"
    local pom="$script_dir/pom.xml"
    local failures=0 karaf_pid sample_line

    _it_memory_verify_eq() {
        if [ "$2" = "$3" ]; then
            echo "OK   $1"
        else
            echo "FAIL $1 (expected '$2', got '$3')"
            failures=$((failures + 1))
        fi
    }

    _it_memory_verify_gt_zero() {
        if awk -v v="${2:-0}" 'BEGIN { exit !(v + 0 > 0) }'; then
            echo "OK   $1 ($2)"
        else
            echo "FAIL $1 (expected > 0, got '${2:-0}')"
            failures=$((failures + 1))
        fi
    }

    echo "Memory sampling verification on $(it_memory_os_name)"
    _it_memory_verify_eq "parse 2g" "2048" "$(it_memory_parse_heap_setting_mb 2g)"
    _it_memory_verify_eq "parse 1536m" "1536" "$(it_memory_parse_heap_setting_mb 1536m)"
    _it_memory_verify_eq "parse docker GiB" "2048" "$(it_memory_parse_docker_mem_to_mb 2GiB)"
    _it_memory_verify_eq "parse docker MiB" "512" "$(it_memory_parse_docker_mem_to_mb 512MiB)"
    _it_memory_verify_eq "parse docker KiB" "1" "$(it_memory_parse_docker_mem_to_mb 1024KiB)"
    _it_memory_verify_eq "parse jinfo flag" "2147483648" \
        "$(it_memory_parse_jvm_flag_bytes '-XX:MaxHeapSize=2147483648' 'MaxHeapSize')"
    _it_memory_verify_eq "search engine default port" "9400" "$(it_search_engine_default_port elasticsearch)"
    _it_memory_verify_eq "search engine container" "itests-elasticsearch" "$(it_search_engine_docker_container elasticsearch)"
    _it_memory_verify_eq "pom elasticsearch.heap" "4g" \
        "$(it_read_pom_profile_property "$pom" elasticsearch elasticsearch.heap 2>/dev/null || true)"
    _it_memory_verify_eq "pom karaf.heap" "2g" \
        "$(it_read_pom_profile_property "$pom" elasticsearch karaf.heap 2>/dev/null || true)"
    _it_memory_verify_gt_zero "system load 1m" "$(it_memory_system_load_1m)"
    _it_memory_verify_gt_zero "system mem available mb" "$(it_memory_system_mem_available_mb)"
    echo "OK   system swap used mb ($(it_memory_system_swap_used_mb))"

    karaf_pid="$(it_memory_find_karaf_pid || true)"
    if [ -n "$karaf_pid" ]; then
        echo "Karaf PID $karaf_pid — live checks"
        sample_line="$(it_memory_sample_once "$target_dir")"
        _it_memory_verify_gt_zero "sample karaf_heap_max_mb" "$(echo "$sample_line" | awk -F'\t' '{print $4}')"
        _it_memory_verify_gt_zero "sample system_load_1m parseable" \
            "$(echo "$sample_line" | awk -F'\t' '{print ($11+0 > 0)}')"
        echo "Sample: $sample_line"
    else
        echo "SKIP live Karaf checks (no Karaf process)"
    fi

    if [ "$failures" -eq 0 ]; then
        echo "All checks passed."
        return 0
    fi
    echo "$failures check(s) failed."
    return 1
}
