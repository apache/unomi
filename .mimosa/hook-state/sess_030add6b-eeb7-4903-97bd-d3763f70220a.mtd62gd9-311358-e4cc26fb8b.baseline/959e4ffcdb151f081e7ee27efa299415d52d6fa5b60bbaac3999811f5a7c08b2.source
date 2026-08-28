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
# Source all IT run tooling modules (sourced, not executed).

it_run_source_modules() {
    local script_dir="$1"
    # shellcheck source=lib/it-run-ui.sh disable=SC1091
    source "$script_dir/lib/it-run-ui.sh"
    # shellcheck source=lib/it-run.sh disable=SC1091
    source "$script_dir/lib/it-run.sh"
    # shellcheck source=lib/it-run-compare.sh disable=SC1091
    source "$script_dir/lib/it-run-compare.sh"
}

it_run_entry_init() {
    local script_dir="$1"
    it_run_source_modules "$script_dir"
    it_run_tools_init "$script_dir"
    ui_init
}
