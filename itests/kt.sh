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

# ASCII art and version
print_header() {
    cat << "EOF"
 _  __                __   _____           _
| |/ /               / _| |_   _|         | |
| ' / __ _ _ __ __ _| |_    | | ___   ___ | |___
|  < / _` | '__/ _` |  _|   | |/ _ \ / _ \| / __|
| . \ (_| | | | (_| | |     | | (_) | (_) | \__ \
|_|\_\__,_|_|  \__,_|_|     \_/\___/ \___/|_|___/

EOF
    echo "--------------------------------------------------"
    echo "Karaf Test Tools (kt.sh) - Version 1.0.0"
    echo "A utility for managing Apache Karaf test instances"
    echo "--------------------------------------------------"
}

# Function to get file modification time in a cross-platform way
get_file_mtime() {
    if [[ "$OSTYPE" == "darwin"* ]]; then
        # macOS
        stat -f "%m %N" "$1"
    else
        # Linux and others
        stat -c "%Y %n" "$1"
    fi
}

# Function to find the latest Karaf directory
find_karaf_dir() {
    if [ ! -d "target/exam" ]; then
        echo "Error: Directory target/exam does not exist" >&2
        return 1
    fi

    local latest_time=0
    local latest_dir=""

    while IFS= read -r dir; do
        local stat_output=$(get_file_mtime "$dir")
        local mtime=$(echo "$stat_output" | cut -d' ' -f1)

        if (( mtime > latest_time )); then
            latest_time=$mtime
            latest_dir=$dir
        fi
    done < <(find target/exam -maxdepth 1 -type d -name "*-*-*-*-*" 2>/dev/null)

    if [ -z "$latest_dir" ]; then
        echo "Error: No Karaf test directory found in target/exam" >&2
        return 1
    fi
    echo "$latest_dir"
}

# Function to find the latest Karaf log file
find_karaf_log() {
    local karaf_dir=$(find_karaf_dir)
    if [ $? -ne 0 ]; then
        return 1
    fi
    local log_file="$karaf_dir/data/log/karaf.log"
    if [ ! -f "$log_file" ]; then
        echo "Error: Karaf log file not found at $log_file" >&2
        return 1
    fi
    echo "$log_file"
}

# Function to find the Karaf executable
find_karaf_exec() {
    local karaf_dir=$(find_karaf_dir)
    if [ $? -ne 0 ]; then
        return 1
    fi
    local karaf_exec="$karaf_dir/bin/karaf"
    if [ ! -f "$karaf_exec" ]; then
        echo "Error: Karaf executable not found at $karaf_exec" >&2
        return 1
    fi
    echo "$karaf_exec"
}

# Function to check if Karaf is running
is_karaf_running() {
    local karaf_dir=$(find_karaf_dir)
    if [ $? -ne 0 ]; then
        return 1
    fi
    local pid_file="$karaf_dir/data/karaf.pid"
    if [ -f "$pid_file" ]; then
        local pid=$(cat "$pid_file")
        if ps -p "$pid" > /dev/null; then
            return 0
        fi
    fi
    return 1
}

usage() {
    print_header
    cat << EOF

DESCRIPTION
    This script helps manage and inspect Karaf test instances during integration
    testing. It automatically finds the most recent test instance and provides
    convenient commands for viewing logs and navigating directories.

USAGE
    $(basename $0) COMMAND [ARGS]

COMMANDS
    start, s    Start the Karaf instance
                Launches Karaf in the background

    debug, d    Start Karaf in debug mode
                Launches Karaf with JPDA debugging enabled on port 5005

    console, c  Start Karaf in console mode
                Launches Karaf in foreground with direct console access

    stop, x     Stop the running Karaf instance
                Gracefully shuts down the Karaf container

    log, l      View the latest Karaf log file using less
                Useful for scrolling through the entire log file

    tail, t     Tail the current Karaf log file
                Follows the log in real-time, great for watching test execution

    grep, g     Grep the latest Karaf log file
                Searches for patterns in the log file
                Example: kt.sh grep "ERROR" finds all error messages

    dir, i      Print the latest Karaf directory path
                Shows the full path to the most recent test instance

    pushd, p    Change to the latest Karaf directory using pushd
                Quickly navigate to the test instance directory
                Use 'popd' to return to the previous directory

    help, h     Show this help message

EXAMPLES
    $(basename $0) start        # Start Karaf instance
    $(basename $0) debug        # Start Karaf in debug mode
    $(basename $0) console      # Start Karaf with direct console access
    $(basename $0) stop         # Stop Karaf instance
    $(basename $0) log          # View complete log with less
    $(basename $0) tail         # Watch log updates in real-time
    $(basename $0) grep ERROR   # Find all ERROR messages in log
    $(basename $0) dir          # Show path to latest test instance
    $(basename $0) pushd        # Jump to test instance directory

TIPS
    - The script automatically finds the most recent test instance
    - All commands have short aliases (single letter) for quick access
    - Use 'tail' during test execution to monitor progress
    - Use 'grep' to search for specific test failures or errors
    - Debug mode allows remote debugging on port 5005
EOF
}

# Main command processing
case "${1:-help}" in
    start|s)
        if is_karaf_running; then
            echo "Error: Karaf is already running" >&2
            exit 1
        fi
        karaf_exec=$(find_karaf_exec)
        if [ $? -eq 0 ]; then
            echo "Starting Karaf..."
            "$karaf_exec" start
            echo "Karaf started. Use 'tail' command to follow the logs."
        fi
        ;;
    console|c)
        if is_karaf_running; then
            echo "Error: Karaf is already running" >&2
            exit 1
        fi
        karaf_exec=$(find_karaf_exec)
        if [ $? -eq 0 ]; then
            echo "Starting Karaf in console mode..."
            exec "$karaf_exec"
        fi
        ;;
    debug|d)
        if is_karaf_running; then
            echo "Error: Karaf is already running" >&2
            exit 1
        fi
        karaf_exec=$(find_karaf_exec)
        if [ $? -eq 0 ]; then
            echo "Starting Karaf in debug mode (port 5005)..."
            KARAF_DEBUG=true "$karaf_exec" debug
            echo "Karaf started in debug mode. Connect debugger to port 5005."
        fi
        ;;
    stop|x)
        if ! is_karaf_running; then
            echo "Error: Karaf is not running" >&2
            exit 1
        fi
        karaf_exec=$(find_karaf_exec)
        if [ $? -eq 0 ]; then
            echo "Stopping Karaf..."
            "$karaf_exec" stop
            echo "Karaf stopped."
        fi
        ;;
    log|l)
        log_file=$(find_karaf_log)
        [ $? -eq 0 ] && less "$log_file"
        ;;
    tail|t)
        log_file=$(find_karaf_log)
        [ $? -eq 0 ] && tail -f "$log_file"
        ;;
    grep|g)
        if [ -z "$2" ]; then
            echo "Error: grep pattern required" >&2
            exit 1
        fi
        log_file=$(find_karaf_log)
        [ $? -eq 0 ] && grep "$2" "$log_file"
        ;;
    dir|i)
        find_karaf_dir
        ;;
    pushd|p)
        karaf_dir=$(find_karaf_dir)
        [ $? -eq 0 ] && pushd "$karaf_dir" > /dev/null && echo "Changed to: $PWD"
        ;;
    help|h|*)
        usage
        ;;
esac
