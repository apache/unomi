# Source all IT run tooling modules (sourced, not executed).

it_run_source_modules() {
    local script_dir="$1"
    # shellcheck source=it-run-ui.sh disable=SC1091
    source "$script_dir/it-run-ui.sh"
    # shellcheck source=it-run-lib.sh disable=SC1091
    source "$script_dir/it-run-lib.sh"
    # shellcheck source=it-run-compare-lib.sh disable=SC1091
    source "$script_dir/it-run-compare-lib.sh"
}

it_run_entry_init() {
    local script_dir="$1"
    it_run_source_modules "$script_dir"
    it_run_tools_init "$script_dir"
    ui_init
}
