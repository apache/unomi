# Presentation layer for IT run tooling (sourced, not executed directly).

UI_USE_COLOR=0
UI_SPINNER_PID=""

ui_init() {
    UI_USE_COLOR=0
    if [ -t 1 ] && [ -z "${NO_COLOR:-}" ] && [ -z "${CI:-}" ]; then
        UI_USE_COLOR=1
    fi
}

ui__color_enabled() {
    [ "$UI_USE_COLOR" -eq 1 ]
}

ui__fg() {
    local code="$1"
    ui__color_enabled && printf '\033[%sm' "$code"
}

ui__reset() { ui__fg 0; }
ui__bold()  { ui__fg 1; }
ui__dim()   { ui__fg 2; }
ui__red()   { ui__fg 31; }
ui__grn()   { ui__fg 32; }
ui__ylw()   { ui__fg 33; }
ui__blu()   { ui__fg 34; }
ui__mag()   { ui__fg 35; }
ui__cyn()   { ui__fg 36; }

ui__line() {
    local glyph="$1"
    local color_fn="$2"
    local indent="$3"
    local message="$4"
    local stream="${5:-1}"
    local plain_label="${6:-}"

    if ui__color_enabled; then
        $color_fn
        printf '%s%s ' "$indent" "$glyph"
        ui__reset
        printf '%s\n' "$message" >&"$stream"
    elif [ "$stream" -eq 2 ]; then
        printf '%s: %s\n' "$plain_label" "$message" >&2
    else
        printf '%s%s\n' "$indent" "$message"
    fi
}

ui__glyph_status() {
    local ok="$1"
    local message="$2"
    local stream="${3:-2}"

    if ui__color_enabled; then
        if [ "$ok" -eq 0 ]; then
            ui__grn; printf '  ✔ '; ui__reset
        else
            ui__red; printf '  ✖ '; ui__reset
            message="$message (failed)"
        fi
        echo "$message" >&"$stream"
    elif [ "$ok" -ne 0 ]; then
        echo "ERROR: $message" >&"$stream"
    fi
}

ui__banner_box() {
    local title="$1"

    if ui__color_enabled; then
        ui__cyn; ui__bold
        cat <<'EOF'
  ╔═══════════════════════════════════════════════════════════╗
  ║                                                           ║
  ║     ██╗   ██╗███╗   ██╗ ██████╗ ███╗   ███╗██╗            ║
  ║     ██║   ██║████╗  ██║██╔═══██╗████╗ ████║██║            ║
  ║     ██║   ██║██╔██╗ ██║██║   ██║██╔████╔██║██║            ║
  ║     ██║   ██║██║╚██╗██║██║   ██║██║╚██╔╝██║██║            ║
  ║     ╚██████╔╝██║ ╚████║╚██████╔╝██║ ╚═╝ ██║██║            ║
  ║      ╚═════╝ ╚═╝  ╚═══╝ ╚═════╝ ╚═╝     ╚═╝╚═╝            ║
  ║                                                           ║
EOF
        printf '  ║  %-57s║\n' "$title"
        echo '  ╚═══════════════════════════════════════════════════════════╝'
        ui__reset
    else
        echo "$title"
    fi
}

ui__banner_with_tagline() {
    local title="$1"
    local tagline="$2"

    ui__banner_box "$title"
    if [ -z "$tagline" ]; then
        return
    fi
    if ui__color_enabled; then
        ui__dim; printf '  '; ui__reset
        ui_typewriter "$tagline"
        echo
    else
        echo "$tagline"
    fi
}

ui_banner() {
    ui__banner_with_tagline "IT RUN ARCHIVER  ·  pack · triage · ship" \
        'Collecting artifacts for humans and LLMs alike...'
}

ui_banner_compare() {
    ui__banner_with_tagline "IT RUN COMPARATOR  ·  systematic · flaky · drift" \
        'Diffing captures to separate real bugs from noise...'
}

ui_typewriter() {
    local text="$1"
    local delay="${2:-0.012}"
    local i

    if ! ui__color_enabled; then
        echo "$text"
        return
    fi
    for ((i = 0; i < ${#text}; i++)); do
        printf '%s' "${text:i:1}"
        sleep "$delay"
    done
    echo
}

ui_phase() {
    local step="$1"
    local label="$2"
    if ui__color_enabled; then
        ui__blu; ui__bold
        printf '▸ [%s] %s\n' "$step" "$label"
        ui__reset
    else
        printf '[%s] %s\n' "$step" "$label"
    fi
}

ui_detail() { ui__line '→' ui__dim '    ' "$1"; }
ui_warn()   { ui__line '⚠' ui__ylw '  ' "$1" 2 WARN; }
ui_error()  { ui__line '✖' ui__red '  ' "$1" 2 ERROR; }

ui_metric() {
    local label="$1"
    local value="$2"
    if ui__color_enabled; then
        ui__dim; printf '    %-14s' "$label"; ui__reset
        ui__bold; printf '%s\n' "$value"; ui__reset
    else
        printf '    %s %s\n' "$label" "$value"
    fi
}

ui_spinner_cleanup() {
    if [ -z "$UI_SPINNER_PID" ]; then
        return
    fi
    kill "$UI_SPINNER_PID" 2>/dev/null || true
    wait "$UI_SPINNER_PID" 2>/dev/null || true
    UI_SPINNER_PID=""
    printf '\r\033[K' >&2
}

ui_spinner_start() {
    local message="$1"
    if ! ui__color_enabled; then
        echo "$message"
        return
    fi
    ui_spinner_cleanup
    (
        local spin='|/-\'
        local i=0
        while true; do
            ui__cyn
            printf '\r  %s %s' "${spin:i%4:1}" "$message"
            ui__reset
            i=$((i + 1))
            sleep 0.12
        done
    ) >&2 &
    UI_SPINNER_PID=$!
}

ui_spinner_stop() {
    local message="$1"
    local status="${2:-0}"

    if ! ui__color_enabled; then
        return "$status"
    fi
    ui_spinner_cleanup
    ui__glyph_status "$status" "$message"
    return "$status"
}

ui_spinner_run() {
    local message="$1"
    shift
    ui_spinner_start "$message"
    "$@"
    local status=$?
    ui_spinner_stop "$message" "$status"
    return "$status"
}

ui__finish_box() {
    local title="$1"
    local emoji="$2"
    local path="$3"
    local detail="$4"

    if ui__color_enabled; then
        ui__mag; ui__bold
        printf '\n  ╭───────────────────────────────────────────────────────────╮\n'
        printf '  │  %-57s│\n' "$title"
        printf '  ╰───────────────────────────────────────────────────────────╯\n'
        ui__reset
        ui__grn; printf '  %s ' "$emoji"; ui__reset
        echo "$path"
        ui__dim; printf '     %s\n' "$detail"; ui__reset
    else
        echo "$title: $path"
        echo "$detail"
    fi
}

ui_finish() {
    local path="$1"
    local size="$2"
    ui__finish_box "ARCHIVE READY" '📦' "$path" "$size bytes · tar -tzf $(printf '%q' "$path")"
}

ui_finish_dir() {
    local path="$1"
    local size="$2"
    local files="$3"
    ui__finish_box "RUN CAPTURE READY" '📂' "$path" "$size bytes · $files files · start with run-context.txt"
}

ui_finish_report() {
    local path="$1"
    local size="$2"
    ui__finish_box "COMPARISON READY" '📊' "$path" "$size bytes · share with an LLM or open in your editor"
}
