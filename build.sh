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

set -e  # Exit on error
trap 'handle_error $? $LINENO $BASH_LINENO "$BASH_COMMAND" $(printf "::%s" ${FUNCNAME[@]:-})' ERR

# Error handling function
handle_error() {
    local exit_code=$1
    local line_no=$2
    local bash_lineno=$3
    local last_command=$4
    local func_trace=$5

    cat << "EOF"
     _____ ____  ____   ___  ____
    | ____|  _ \|  _ \ / _ \|  _ \
    |  _| | |_) | |_) | | | | |_) |
    | |___|  _ <|  _ <| |_| |  _ <
    |_____|_| \_\_| \_\\___/|_| \_\

EOF
    echo "Error occurred in:"
    echo "  Command: $last_command"
    echo "  Line: $line_no"
    echo "  Exit code: $exit_code"
    if [ ! -z "$func_trace" ]; then
        echo "  Function trace: $func_trace"
    fi
    exit $exit_code
}

# Function to detect color support
setup_colors() {
    # Only use colors if connected to a terminal
    if [ -t 1 ]; then
        # Check if NO_COLOR is set (https://no-color.org/)
        if [ -z "${NO_COLOR+x}" ]; then
            # Check if terminal supports colors
            if [ -n "$TERM" ] && [ "$TERM" != "dumb" ]; then
                # Check if tput is available
                if command -v tput > /dev/null && tput setaf 1 >&/dev/null; then
                    RED='\033[0;31m'
                    GREEN='\033[0;32m'
                    YELLOW='\033[1;33m'
                    BLUE='\033[0;34m'
                    MAGENTA='\033[0;35m'
                    CYAN='\033[0;36m'
                    GRAY='\033[0;90m'
                    BOLD='\033[1m'
                    NC='\033[0m' # No Color

                    # Unicode symbols (only if terminal likely supports UTF-8)
                    if [ "$TERM" != "linux" ] && [ -n "$LANG" ] && [ "$LANG" != "C" ]; then
                        CHECK_MARK="✔"
                        CROSS_MARK="✘"
                        ARROW="➜"
                        WARNING="⚠"
                        INFO="ℹ"
                    else
                        CHECK_MARK="(+)"
                        CROSS_MARK="(x)"
                        ARROW="(>)"
                        WARNING="(!)"
                        INFO="(i)"
                    fi

                    HAS_COLORS=1
                fi
            fi
        fi
    fi

    # If colors are not supported or disabled, use plain text without escape sequences
    if [ -z "${HAS_COLORS+x}" ]; then
        RED=''
        GREEN=''
        YELLOW=''
        BLUE=''
        MAGENTA=''
        CYAN=''
        GRAY=''
        BOLD=''
        NC=''

        # Use ASCII alternatives without escape sequences
        CHECK_MARK="(+)"
        CROSS_MARK="(x)"
        ARROW="(>)"
        WARNING="(!)"
        INFO="(i)"

        HAS_COLORS=0
    fi
}

# Initialize colors early
setup_colors

# Function to print section headers
print_section() {
    local text="$1"
    local text_length=${#text}
    local total_width=80  # Standard terminal width
    local padding_length=$(( (total_width - text_length - 4) / 2 ))  # -4 for the borders and spaces
    local left_padding=""
    local right_padding=""

    # Create padding strings of spaces for the text line
    for ((i=0; i<padding_length; i++)); do
        left_padding+=" "
        right_padding+=" "
    done

    # Adjust right padding for odd lengths
    if [ $(( (total_width - text_length) % 2 )) -eq 1 ]; then
        right_padding+=" "
    fi

    if [ "$HAS_COLORS" -eq 1 ]; then
        echo -e "\n${BLUE}╔════════════════════════════════════════════════════════════════════════════╗${NC}"
        echo -e "${BLUE}║${NC}${left_padding}${BOLD}${text}${NC}${right_padding}${BLUE}║${NC}"
        echo -e "${BLUE}╚════════════════════════════════════════════════════════════════════════════╝${NC}"
    else
        echo -e "\n+------------------------------------------------------------------------------+"
        echo -e "| ${left_padding}${text}${right_padding} |"
        echo "+------------------------------------------------------------------------------+"
    fi
}

# Function to print status messages
print_status() {
    local status=$1
    local message=$2
    
    if [ "$HAS_COLORS" -eq 1 ]; then
        case $status in
            "success")
                echo -e " ${GREEN}${CHECK_MARK}${NC} ${GREEN}${message}${NC}"
                ;;
            "error")
                echo -e " ${RED}${CROSS_MARK}${NC} ${RED}${message}${NC}"
                ;;
            "warning")
                echo -e " ${YELLOW}${WARNING}${NC} ${YELLOW}${message}${NC}"
                ;;
            "info")
                echo -e " ${CYAN}${INFO}${NC} ${CYAN}${message}${NC}"
                ;;
            *)
                echo -e " ${BLUE}${ARROW}${NC} ${message}"
                ;;
        esac
    else
        # No colors - use plain text without escape sequences
        local symbol
        case $status in
            "success")
                symbol="${CHECK_MARK}"
                ;;
            "error")
                symbol="${CROSS_MARK}"
                ;;
            "warning")
                symbol="${WARNING}"
                ;;
            "info")
                symbol="${INFO}"
                ;;
            *)
                symbol="${ARROW}"
                ;;
        esac
        echo " ${symbol} ${message}"
    fi
}

# Enhanced progress tracking
print_progress() {
    local current=$1
    local total=$2
    local message=$3
    local percentage=$((current * 100 / total))

    if [ "$HAS_COLORS" -eq 1 ]; then
        local filled=$((percentage / 2))
        local empty=$((50 - filled))

        local progress="["
        for ((i=0; i<filled; i++)); do progress+="█"; done
        for ((i=0; i<empty; i++)); do progress+="░"; done
        progress+="]"

        echo -e "\r${CYAN}${progress}${NC} ${percentage}% ${GRAY}${message}${NC}"
    else
        local filled=$((percentage / 4))
        local empty=$((25 - filled))

        local progress="["
        for ((i=0; i<filled; i++)); do progress+="#"; done
        for ((i=0; i<empty; i++)); do progress+="-"; done
        progress+="]"

        echo -e "\r${progress} ${percentage}% ${message}"
    fi
}

# Function to prompt for continuation
prompt_continue() {
    local prompt_text="$1"
    if [ -z "$prompt_text" ]; then
        prompt_text="Continue?"
    fi
    
    read -p "$prompt_text (y/N) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        exit 1
    fi
}

# Add the new section header
print_section "Apache Unomi Build Script"

# Default values
SKIP_TESTS=false
RUN_INTEGRATION_TESTS=false
DEPLOY=false
DEBUG=false
USE_MAVEN_CACHE=true
PURGE_MAVEN_CACHE=false
MAVEN_DEBUG=false
MAVEN_OFFLINE=false
KARAF_DEBUG_PORT=5005
KARAF_DEBUG_SUSPEND=n
USE_OPENSEARCH=false
NO_KARAF=false
AUTO_START=""
SINGLE_TEST=""
IT_DEBUG=false
IT_DEBUG_PORT=5006
IT_DEBUG_SUSPEND=false
SKIP_MIGRATION_TESTS=false

# Enhanced usage function with color support
usage() {
    if [ "$HAS_COLORS" -eq 1 ]; then
        echo -e "${CYAN}"
        cat << "EOF"
     _    _ _____ _      ____
    | |  | |  ___| |    |  _ \
    | |__| | |__ | |    | |_) |
    |  __  |  __|| |    |  __/
    | |  | | |___| |____| |
    |_|  |_\_____|______|_|
EOF
        echo -e "${NC}"

        echo -e "${BOLD}Usage:${NC} $0 [options]"
        echo
        echo -e "${BOLD}Options:${NC}"
        echo -e "  ${CYAN}-h, --help${NC}                 Show this help message"
        echo -e "  ${CYAN}-s, --skip-tests${NC}           Skip all tests"
        echo -e "  ${CYAN}-i, --integration-tests${NC}    Run integration tests"
        echo -e "  ${CYAN}-d, --deploy${NC}               Deploy after build"
        echo -e "  ${CYAN}-X, --maven-debug${NC}          Enable Maven debug output"
        echo -e "  ${CYAN}-o, --offline${NC}              Run Maven in offline mode"
        echo -e "  ${CYAN}--debug${NC}                    Run Karaf in debug mode"
        echo -e "  ${CYAN}--debug-port PORT${NC}          Set debug port (default: 5005)"
        echo -e "  ${CYAN}--debug-suspend${NC}            Suspend JVM until debugger connects"
        echo -e "  ${CYAN}--no-maven-cache${NC}           Disable Maven build cache"
        echo -e "  ${CYAN}--purge-maven-cache${NC}        Purge local Maven cache before building"
        echo -e "  ${CYAN}--karaf-home PATH${NC}          Set Karaf home directory for deployment"
        echo -e "  ${CYAN}--use-opensearch${NC}           Use OpenSearch instead of ElasticSearch"
        echo -e "  ${CYAN}--no-karaf${NC}                 Build without starting Karaf"
        echo -e "  ${CYAN}--auto-start ENGINE${NC}        Auto-start with specified engine"
        echo -e "  ${CYAN}--single-test TEST${NC}         Run a single integration test"
        echo -e "  ${CYAN}--it-debug${NC}                 Enable integration test debug mode"
        echo -e "  ${CYAN}--it-debug-port PORT${NC}       Set integration test debug port"
        echo -e "  ${CYAN}--it-debug-suspend${NC}         Suspend integration test until debugger connects"
        echo -e "  ${CYAN}--skip-migration-tests${NC}     Skip migration-related tests"
    else
        cat << "EOF"
     _    _ _____ _      ____
    | |  | |  ___| |    |  _ \
    | |__| | |__ | |    | |_) |
    |  __  |  __|| |    |  __/
    | |  | | |___| |____| |
    |_|  |_\_____|______|_|
EOF
        echo
        echo "Usage: $0 [options]"
        echo
        echo "Options:"
        echo "  -h, --help                 Show this help message"
        echo "  -s, --skip-tests           Skip all tests"
        echo "  -i, --integration-tests    Run integration tests"
        echo "  -d, --deploy               Deploy after build"
        echo "  -X, --maven-debug         Enable Maven debug output"
        echo "  -o, --offline             Run Maven in offline mode"
        echo "  --debug                    Run Karaf in debug mode"
        echo "  --debug-port PORT          Set debug port (default: 5005)"
        echo "  --debug-suspend           Suspend JVM until debugger connects"
        echo "  --no-maven-cache           Disable Maven build cache"
        echo "  --purge-maven-cache        Purge local Maven cache before building"
        echo "  --karaf-home PATH          Set Karaf home directory for deployment"
        echo "  --use-opensearch          Use OpenSearch instead of ElasticSearch"
        echo "  --no-karaf               Build without starting Karaf"
        echo "  --auto-start ENGINE      Auto-start with specified engine"
        echo "  --single-test TEST         Run a single integration test"
        echo "  --it-debug                Enable integration test debug mode"
        echo "  --it-debug-port PORT      Set integration test debug port"
        echo "  --it-debug-suspend        Suspend integration test until debugger connects"
        echo "  --skip-migration-tests    Skip migration-related tests"
    fi

    echo
    echo "Examples:"
    if [ "$HAS_COLORS" -eq 1 ]; then
        # Reset to default terminal color for examples (better readability)
        echo -e "${NC}"
        echo "  # Build with integration tests using OpenSearch"
        echo "  $0 --integration-tests --use-opensearch"
        echo
        echo "  # Build in debug mode"
        echo "  $0 --debug --debug-port 5006 --debug-suspend"
        echo
        echo "  # Deploy to specific Karaf instance"
        echo "  $0 --deploy --karaf-home ~/apache-karaf"
        echo
        echo "  # Build without Karaf and auto-start OpenSearch"
        echo "  $0 --no-karaf --auto-start opensearch"
        echo
        echo "  # Run a single integration test"
        echo "  $0 --integration-tests --single-test org.apache.unomi.itests.graphql.GraphQLEventIT"
        echo
        echo "  # Debug a single integration test"
        echo "  $0 --integration-tests --single-test org.apache.unomi.itests.graphql.GraphQLEventIT --it-debug --it-debug-suspend"
        echo
        echo "  # Run without colored output"
        echo "  NO_COLOR=1 $0"
        echo "  # or"
        echo "  export NO_COLOR=1"
        echo "  $0"
    else
        echo "  # Build with integration tests using OpenSearch"
        echo "  $0 --integration-tests --use-opensearch"
        echo
        echo "  # Build in debug mode"
        echo "  $0 --debug --debug-port 5006 --debug-suspend"
        echo
        echo "  # Deploy to specific Karaf instance"
        echo "  $0 --deploy --karaf-home ~/apache-karaf"
        echo
        echo "  # Build without Karaf and auto-start OpenSearch"
        echo "  $0 --no-karaf --auto-start opensearch"
        echo
        echo "  # Run a single integration test"
        echo "  $0 --integration-tests --single-test org.apache.unomi.itests.graphql.GraphQLEventIT"
        echo
        echo "  # Debug a single integration test"
        echo "  $0 --integration-tests --single-test org.apache.unomi.itests.graphql.GraphQLEventIT --it-debug --it-debug-suspend"
        echo
        echo "  # Run without colored output"
        echo "  NO_COLOR=1 $0"
        echo "  # or"
        echo "  export NO_COLOR=1"
        echo "  $0"
    fi
    exit 1
}

# Parse command line arguments
while [ "$1" != "" ]; do
    case $1 in
        -h | --help)
            usage
            ;;
        -X | --maven-debug)
            MAVEN_DEBUG=true
            ;;
        -o | --offline)
            MAVEN_OFFLINE=true
            ;;
        -s | --skip-tests)
            SKIP_TESTS=true
            ;;
        -i | --integration-tests)
            RUN_INTEGRATION_TESTS=true
            ;;
        -d | --deploy)
            DEPLOY=true
            ;;
        --debug)
            DEBUG=true
            ;;
        --debug-port)
            shift
            KARAF_DEBUG_PORT=$1
            ;;
        --debug-suspend)
            KARAF_DEBUG_SUSPEND=y
            ;;
        --no-maven-cache)
            USE_MAVEN_CACHE=false
            ;;
        --purge-maven-cache)
            PURGE_MAVEN_CACHE=true
            ;;
        --karaf-home)
            shift
            CONTEXT_SERVER_KARAF_HOME=$1
            ;;
        --use-opensearch)
            USE_OPENSEARCH=true
            ;;
        --no-karaf)
            NO_KARAF=true
            ;;
        --auto-start)
            shift
            if [[ "$1" != "elasticsearch" && "$1" != "opensearch" ]]; then
                echo "Error: --auto-start must be either 'elasticsearch' or 'opensearch'"
                exit 1
            fi
            AUTO_START="$1"
            ;;
        --single-test)
            shift
            SINGLE_TEST="$1"
            ;;
        --it-debug)
            IT_DEBUG=true
            ;;
        --it-debug-port)
            shift
            IT_DEBUG_PORT=$1
            ;;
        --it-debug-suspend)
            IT_DEBUG_SUSPEND=true
            ;;
        --skip-migration-tests)
            SKIP_MIGRATION_TESTS=true
            ;;
        *)
            echo "Unknown option: $1"
            usage
            ;;
    esac
    shift
done

# Set environment
DIRNAME=`dirname "$0"`
PROGNAME=`basename "$0"`
if [ -f "$DIRNAME/setenv.sh" ]; then
    . "$DIRNAME/setenv.sh"
fi

# Purge Maven cache if requested
if [ "$PURGE_MAVEN_CACHE" = true ]; then
    echo "Purging Maven cache..."
    rm -rf ~/.m2/build-cache ~/.m2/dependency-cache ~/.m2/dependency-cache_v2
    echo "Maven cache purged."
fi

# Function to check if command exists
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

# Comprehensive function to check all requirements
check_requirements() {
    print_section "System Requirements Check"
    local has_warnings=false
    local has_errors=false

    # 1. Required Tools Check
    print_status "info" "Checking required tools..."
    local required_tools=("mvn" "java" "tar" "gzip" "dot")
    local missing_tools=()
    
    echo "Required tools:"
    for tool in "${required_tools[@]}"; do
        if command_exists "$tool"; then
            case "$tool" in
                java)
                    java_version=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}')
                    if [[ "$java_version" =~ ^1[1-9]\. || "$java_version" =~ ^[2-9][0-9]\. ]]; then
                        print_status "success" "✓ Java ${java_version}"
                    else
                        print_status "error" "✗ Java ${java_version} (version 11 or higher required)"
                        echo "Please install Java 11 or higher:"
                        if [[ "$(uname -sm)" == "Darwin arm64" ]]; then
                            echo "Apple Silicon (M1/M2) detected, recommended options:"
                            echo "  - Download native arm64 build from: https://adoptium.net/ (recommended)"
                            echo "  - Using Homebrew: brew install --cask temurin"
                            echo "  - Azul Zulu: https://www.azul.com/downloads/?os=macos&architecture=arm-64-bit"
                            echo "Note: For best performance, ensure you're using an arm64 native JDK"
                        else
                            echo "  - Download from: https://adoptium.net/ (recommended)"
                            echo "  - Or use your system's package manager:"
                            echo "    - macOS: brew install --cask temurin"
                            echo "    - Ubuntu/Debian: sudo apt install openjdk-11-jdk"
                            echo "    - CentOS/RHEL: sudo yum install java-11-openjdk-devel"
                        fi
                        echo "  - See BUILDING.md in the project root for more details"
                        has_errors=true
                    fi
                    ;;
                mvn)
                    if [ "$MAVEN_OFFLINE" = false ]; then
                        mvn_version=$(mvn --version | head -n 1)
                        print_status "success" "✓ ${mvn_version}"
                    else
                        print_status "success" "✓ Maven (offline mode enabled)"
                    fi
                    ;;
                dot)
                    dot_version=$(dot -V 2>&1)
                    print_status "success" "✓ GraphViz: ${dot_version}"
                    # Set GRAPHVIZ_DOT if not already set
                    if [ -z "$GRAPHVIZ_DOT" ]; then
                        export GRAPHVIZ_DOT=$(command -v dot)
                        MVN_OPTS="$MVN_OPTS -Dgraphviz.dot.path=$GRAPHVIZ_DOT"
                    fi
                    ;;
                *)
                    print_status "success" "✓ ${tool}"
                    ;;
            esac
        else
            missing_tools+=("$tool")
            print_status "error" "✗ ${tool} not found"
            case "$tool" in
                java)
                    echo "Please install Java 11 or higher:"
                    if [[ "$(uname -sm)" == "Darwin arm64" ]]; then
                        echo "Apple Silicon (M1/M2) detected, recommended options:"
                        echo "  - Download native arm64 build from: https://adoptium.net/ (recommended)"
                        echo "  - Using Homebrew: brew install --cask temurin"
                        echo "  - Azul Zulu: https://www.azul.com/downloads/?os=macos&architecture=arm-64-bit"
                        echo "Note: For best performance, ensure you're using an arm64 native JDK"
                    else
                        echo "  - Download from: https://adoptium.net/ (recommended)"
                        echo "  - Or use your system's package manager:"
                        echo "    - macOS: brew install --cask temurin"
                        echo "    - Ubuntu/Debian: sudo apt install openjdk-11-jdk"
                        echo "    - CentOS/RHEL: sudo yum install java-11-openjdk-devel"
                    fi
                    echo "  - See BUILDING.md in the project root for more details"
                    ;;
                mvn)
                    echo "Please install Maven 3.6 or higher:"
                    echo "  - Download from: https://maven.apache.org/download.cgi"
                    echo "  - Or use your system's package manager:"
                    echo "    - macOS: brew install maven"
                    echo "    - Ubuntu/Debian: sudo apt install maven"
                    echo "    - CentOS/RHEL: sudo yum install maven"
                    echo "  - See installation guide: https://maven.apache.org/install.html"
                    echo "  - See BUILDING.md in the project root for more details"
                    ;;
                dot)
                    echo "Please install GraphViz (required for documentation generation):"
                    echo "  - Project page: https://graphviz.org/download/"
                    echo "  - Package managers:"
                    echo "    - macOS: brew install graphviz"
                    echo "    - Ubuntu/Debian: sudo apt install graphviz"
                    echo "    - CentOS/RHEL: sudo yum install graphviz"
                    echo "  - See BUILDING.md in the project root for more details"
                    ;;
                tar|gzip)
                    echo "Please install required system utilities:"
                    echo "  - macOS: These should be pre-installed"
                    echo "  - Ubuntu/Debian: sudo apt install tar gzip"
                    echo "  - CentOS/RHEL: sudo yum install tar gzip"
                    ;;
            esac
            has_errors=true
        fi
    done
    echo

    # 3. System Resources Check
    print_status "info" "Checking system resources..."
    
    # Memory check
    if command_exists free; then
        available_memory=$(free -m | awk '/^Mem:/{print $2}')
        if [ "$available_memory" -lt 2048 ]; then
            print_status "warning" "✗ Memory: ${available_memory}MB available (2048MB recommended)"
            echo "Tips to free up memory:"
            echo "  - Close unnecessary applications"
            echo "  - Clear browser cache and tabs"
            echo "  - Check for memory-intensive processes: top or htop"
            echo "  - If using a VM, consider increasing its memory allocation"
            has_warnings=true
        else
            print_status "success" "✓ Memory: ${available_memory}MB available"
        fi
    else
        print_status "warning" "? Memory check not available"
        echo "Note: Memory check is not available on macOS by default"
        echo "You can install htop for memory monitoring: brew install htop"
        has_warnings=true
    fi

    # Disk space check
    if command_exists df; then
        available_disk=$(df -m . | awk 'NR==2 {print $4}')
        if [ "$available_disk" -lt 1024 ]; then
            print_status "warning" "✗ Disk space: ${available_disk}MB available (1024MB recommended)"
            echo "Tips to free up disk space:"
            echo "  - Clear Maven cache: rm -rf ~/.m2/repository"
            echo "  - Clear Docker images/containers if using Docker"
            echo "  - Use 'du -sh *' to identify large directories"
            echo "  - Consider running: mvn clean"
            has_warnings=true
        else
            print_status "success" "✓ Disk space: ${available_disk}MB available"
        fi
    else
        print_status "warning" "? Disk space check not available"
        has_warnings=true
    fi
    echo

    # 4. Configuration Check
    print_status "info" "Checking configuration..."
    
    # Maven settings check
    if [ ! -f ~/.m2/settings.xml ]; then
        print_status "warning" "✗ Maven settings.xml not found"
        echo "Tips for Maven configuration:"
        echo "  - Create a minimal settings.xml:"
        echo "    mkdir -p ~/.m2"
        echo "    echo '<settings><localRepository>\${user.home}/.m2/repository</localRepository></settings>' > ~/.m2/settings.xml"
        echo "  - Or copy the example from: https://maven.apache.org/settings.html"
        echo "  - See also: BUILDING.md in the project root for project-specific settings"
        has_warnings=true
    else
        print_status "success" "✓ Maven settings.xml found"
    fi

    # Debug port check if debug mode is enabled
    if [ "$DEBUG" = true ]; then
        if ! [[ "$KARAF_DEBUG_PORT" =~ ^[0-9]+$ ]] || [ "$KARAF_DEBUG_PORT" -lt 1024 ] || [ "$KARAF_DEBUG_PORT" -gt 65535 ]; then
            print_status "error" "✗ Debug port: $KARAF_DEBUG_PORT (invalid)"
            echo "Please specify a valid port with --debug-port option"
            echo "Common debug ports: 5005 (default), 8000, 8453"
            has_errors=true
        elif command_exists nc && nc -z localhost "$KARAF_DEBUG_PORT" 2>/dev/null; then
            print_status "error" "✗ Debug port: $KARAF_DEBUG_PORT (already in use)"
            echo "Tips:"
            echo "  - Choose a different port with --debug-port option"
            echo "  - Check what's using the port: lsof -i :$KARAF_DEBUG_PORT"
            echo "  - Kill the process using the port if necessary"
            has_errors=true
        else
            print_status "success" "✓ Debug port: $KARAF_DEBUG_PORT available"
        fi
    fi

    # Karaf home check if deployment is enabled
    if [ "$DEPLOY" = true ]; then
        if [ -z "$CONTEXT_SERVER_KARAF_HOME" ]; then
            print_status "error" "Karaf home directory not set for deployment"
            has_errors=true
        elif [ ! -d "$CONTEXT_SERVER_KARAF_HOME" ]; then
            print_status "error" "Karaf home directory does not exist: $CONTEXT_SERVER_KARAF_HOME"
            has_errors=true
        elif [ ! -w "$CONTEXT_SERVER_KARAF_HOME" ]; then
            print_status "error" "Karaf home directory not writable: $CONTEXT_SERVER_KARAF_HOME"
            has_errors=true
        else
            print_status "success" "Karaf home directory validated: $CONTEXT_SERVER_KARAF_HOME"
        fi
    fi

    # 5. Option Validation
    print_status "info" "Validating options..."
    
    if [ "$SKIP_TESTS" = true ] && [ "$RUN_INTEGRATION_TESTS" = true ]; then
        print_status "error" "Cannot use --skip-tests and --integration-tests together"
        has_errors=true
    fi

    if [ ! -z "$SINGLE_TEST" ] && [ "$RUN_INTEGRATION_TESTS" = false ]; then
        print_status "error" "Single test specified (--single-test) but integration tests are not enabled. Use --integration-tests to run the test."
        has_errors=true
    fi

    if [ "$IT_DEBUG" = true ] && [ "$RUN_INTEGRATION_TESTS" = false ]; then
        print_status "error" "Integration test debug (--it-debug) enabled but integration tests are not enabled. Use --integration-tests to run the test."
        has_errors=true
    fi

    if [ "$IT_DEBUG" = true ]; then
        if ! [[ "$IT_DEBUG_PORT" =~ ^[0-9]+$ ]] || [ "$IT_DEBUG_PORT" -lt 1024 ] || [ "$IT_DEBUG_PORT" -gt 65535 ]; then
            print_status "error" "✗ Integration test debug port: $IT_DEBUG_PORT (invalid)"
            echo "Please specify a valid port with --it-debug-port option"
            echo "Common debug ports: 5006 (default), 8000, 8453"
            has_errors=true
        elif command_exists nc && nc -z localhost "$IT_DEBUG_PORT" 2>/dev/null; then
            print_status "error" "✗ Integration test debug port: $IT_DEBUG_PORT (already in use)"
            echo "Tips:"
            echo "  - Choose a different port with --it-debug-port option"
            echo "  - Check what's using the port: lsof -i :$IT_DEBUG_PORT"
            echo "  - Kill the process using the port if necessary"
            has_errors=true
        else
            print_status "success" "✓ Integration test debug port: $IT_DEBUG_PORT available"
        fi
    fi

    if [ "$MAVEN_OFFLINE" = true ]; then
        if [ "$PURGE_MAVEN_CACHE" = true ]; then
            print_status "error" "Cannot use --purge-maven-cache in offline mode"
            has_errors=true
        fi
        if [ "$USE_MAVEN_CACHE" = false ]; then
            print_status "warning" "Using --no-maven-cache with offline mode may cause build failures"
            has_warnings=true
        fi
    fi

    # Final status and prompts
    echo
    if [ "$has_errors" = true ]; then
        print_status "error" "Critical requirements not met. Please fix the errors above."
        echo
        echo "For more information and help:"
        echo "  - Read BUILDING.md in the project root directory"
        echo "  - Visit Apache Unomi website: https://unomi.apache.org/"
        echo "  - Check the troubleshooting guide: https://unomi.apache.org/contribute/building-and-deploying.html"
        echo "  - Ask for help on the mailing list: dev@unomi.apache.org"
        echo "  - Report issues: https://issues.apache.org/jira/browse/UNOMI"
        exit 1
    fi

    if [ "$has_warnings" = true ]; then
        print_status "warning" "Some non-critical requirements not met"
        echo "You can proceed, but you may encounter issues during the build"
        echo "See warnings above for recommendations"
        echo "For more information, check BUILDING.md in the project root"
        prompt_continue "Continue despite warnings?"
    else
        print_status "success" "All requirements checked successfully"
    fi
}

# Construct Maven command
MVN_CMD="mvn"
MVN_OPTS=""

# Add Maven debug option
if [ "$MAVEN_DEBUG" = true ]; then
    MVN_OPTS="$MVN_OPTS -X"
    echo "Maven debug output enabled"
fi

# Add Maven offline option
if [ "$MAVEN_OFFLINE" = true ]; then
    MVN_OPTS="$MVN_OPTS -o"
    echo "Maven offline mode enabled"

    # Warn if purge cache is enabled with offline mode
    if [ "$PURGE_MAVEN_CACHE" = true ]; then
        echo "Warning: Purging Maven cache while in offline mode may cause build failures"
        read -p "Continue anyway? (y/N) " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            exit 1
        fi
    fi
fi

# Add Maven cache option
if [ "$USE_MAVEN_CACHE" = false ]; then
    MVN_OPTS="$MVN_OPTS -Dmaven.build.cache.enabled=false"
fi

# Verify Maven settings
if [ ! -f ~/.m2/settings.xml ]; then
    echo "Warning: Maven settings.xml not found at ~/.m2/settings.xml"
    read -p "Continue anyway? (y/N) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        exit 1
    fi
fi

# Add profile options
PROFILES=""
if [ "$RUN_INTEGRATION_TESTS" = true ]; then
    if [ "$USE_OPENSEARCH" = true ]; then
        MVN_OPTS="$MVN_OPTS -Duse.opensearch=true -P opensearch"
        echo "Running integration tests with OpenSearch"
    else
        echo "Running integration tests with ElasticSearch"
    fi
    MVN_OPTS="$MVN_OPTS -P integration-tests"
    
    # Add single test option if specified
    if [ ! -z "$SINGLE_TEST" ]; then
        MVN_OPTS="$MVN_OPTS -Dit.test=$SINGLE_TEST"
        echo "Running single integration test: $SINGLE_TEST"
    fi
    
    # Add integration test debug options if enabled
    if [ "$IT_DEBUG" = true ]; then
        DEBUG_OPTS="port=$IT_DEBUG_PORT"
        if [ "$IT_DEBUG_SUSPEND" = true ]; then
            DEBUG_OPTS="$DEBUG_OPTS,hold:true"
            echo "Integration test debug enabled with suspend (port: $IT_DEBUG_PORT)"
        else
            DEBUG_OPTS="$DEBUG_OPTS,hold:false"
            echo "Integration test debug enabled (port: $IT_DEBUG_PORT)"
        fi
        MVN_OPTS="$MVN_OPTS -Dit.karaf.debug=$DEBUG_OPTS"
    fi

    # Add migration test exclusion if specified
    if [ "$SKIP_MIGRATION_TESTS" = true ]; then
        MVN_OPTS="$MVN_OPTS -Dit.test.exclude.pattern=**/migration/**/*IT.java"
        echo "Skipping migration tests"
    fi
else
    if [ "$SKIP_TESTS" = true ]; then
        PROFILES="$PROFILES,!integration-tests,!run-tests"
        MVN_OPTS="$MVN_OPTS -DskipTests"
    fi
    
    # Warn if single test was specified but integration tests are not enabled
    if [ ! -z "$SINGLE_TEST" ]; then
        print_status "warning" "Single test specified but integration tests are not enabled. Use --integration-tests to run the test."
    fi
fi

if [ ! -z "$PROFILES" ]; then
    # Remove leading comma if present
    PROFILES=${PROFILES#,}
    MVN_OPTS="$MVN_OPTS -P$PROFILES"
fi

# Add GraphViz path to Maven options if manually specified
if [ ! -z "$GRAPHVIZ_DOT" ]; then
    MVN_OPTS="$MVN_OPTS -Dgraphviz.dot.path=$GRAPHVIZ_DOT"
fi

# Progress tracking functions
start_timer() {
    start_time=$(date +%s)
}

get_elapsed_time() {
    local end_time=$(date +%s)
    local elapsed=$((end_time - start_time))
    printf "%02d:%02d" $((elapsed/60)) $((elapsed%60))
}

# Build command
cat << "EOF"
     ____  _    _ _____ _      ____
    |  _ \| |  | |_   _| |    |  _ \
    | |_) | |  | | | | | |    | | | |
    |  _ <| |  | | | | | |    | | | |
    | |_) | |__| |_| |_| |____| |_| |
    |____/ \____/|_____|______|____/

EOF
echo "Building..."
echo "Estimated time: 3-5 minutes for build, 50-60 minutes with integration tests"
start_timer

# Build phases with enhanced output
total_steps=2
current_step=0

print_progress $((++current_step)) $total_steps "Cleaning previous build..."
if [ "$HAS_COLORS" -eq 1 ]; then
    echo -e "${GRAY}Running: $MVN_CMD clean $MVN_OPTS${NC}"
else
    echo "Running: $MVN_CMD clean $MVN_OPTS"
fi
$MVN_CMD clean $MVN_OPTS || {
    print_status "error" "Maven clean failed"
    exit 1
}

print_progress $((++current_step)) $total_steps "Compiling and installing artifacts..."
if [ "$HAS_COLORS" -eq 1 ]; then
    echo -e "${GRAY}Running: $MVN_CMD install $MVN_OPTS${NC}"
else
    echo "Running: $MVN_CMD install $MVN_OPTS"
fi
$MVN_CMD install $MVN_OPTS || {
    print_status "error" "Maven install failed"
    exit 1
}

print_status "success" "Build completed in $(get_elapsed_time)"

# Deployment section with enhanced output
if [ "$DEPLOY" = true ]; then
    # Validate Karaf home directory
    if [ -z "$CONTEXT_SERVER_KARAF_HOME" ]; then
        print_status "error" "Karaf home directory not set. Use --karaf-home option."
        exit 1
    fi
    if [ ! -d "$CONTEXT_SERVER_KARAF_HOME" ]; then
        print_status "error" "Karaf home directory does not exist: $CONTEXT_SERVER_KARAF_HOME"
        exit 1
    fi
    if [ ! -w "$CONTEXT_SERVER_KARAF_HOME/deploy" ]; then
        print_status "error" "No write permission to Karaf deploy directory: $CONTEXT_SERVER_KARAF_HOME/deploy"
        exit 1
    fi

    if [ "$HAS_COLORS" -eq 1 ]; then
        echo -e "${MAGENTA}"
        cat << "EOF"
     ____  _____ ____  _     _____   __
    |  _ \| ____|  _ \| |   / _ \ \ / /
    | | | |  _| | |_) | |  | | | \ V /
    | |_| | |___|  __/| |__| |_| || |
    |____/|_____|_|   |_____\___/ |_|

EOF
        echo -e "${NC}"
    else
        cat << "EOF"
     ____  _____ ____  _     _____   __
    |  _ \| ____|  _ \| |   / _ \ \ / /
    | | | |  _| | |_) | |  | | | \ V /
    | |_| | |___|  __/| |__| |_| || |
    |____/|_____|_|   |_____\___/ |_|

EOF
    fi

    start_timer
    total_deploy_steps=3
    current_step=0

    print_progress $((++current_step)) $total_deploy_steps "Copying KAR package..."
    if [ ! -f "kar/target/unomi-kar-$UNOMI_VERSION.kar" ]; then
        print_status "error" "KAR file not found: kar/target/unomi-kar-$UNOMI_VERSION.kar"
        exit 1
    fi
    cp -v kar/target/unomi-kar-$UNOMI_VERSION.kar $CONTEXT_SERVER_KARAF_HOME/deploy/ || {
        print_status "error" "Failed to copy KAR file to deploy directory"
        exit 1
    }
    print_status "success" "KAR package copied successfully"

    print_progress $((++current_step)) $total_deploy_steps "Purging Karaf Maven repository..."
    if [ -d "$CONTEXT_SERVER_KARAF_HOME/data/maven/repository" ]; then
        rm -rf "$CONTEXT_SERVER_KARAF_HOME/data/maven/repository/"* || {
            print_status "error" "Failed to purge Karaf Maven repository"
            exit 1
        }
        print_status "success" "Karaf Maven repository purged"
    else
        print_status "info" "Karaf Maven repository not found, skipping purge"
    fi

    print_progress $((++current_step)) $total_deploy_steps "Purging Karaf temporary files..."
    if [ -d "$CONTEXT_SERVER_KARAF_HOME/data/tmp" ]; then
        rm -rf "$CONTEXT_SERVER_KARAF_HOME/data/tmp/"* || {
            print_status "error" "Failed to purge Karaf temporary directory"
            exit 1
        }
        print_status "success" "Karaf temporary files purged"
    else
        print_status "info" "Karaf temporary directory not found, skipping purge"
    fi

    print_status "success" "Deployment completed in $(get_elapsed_time)"
fi

# Karaf startup section
if [ "$NO_KARAF" = false ]; then
    if [ "$HAS_COLORS" -eq 1 ]; then
        echo -e "${GREEN}"
        cat << "EOF"
     _  __    _    ____      _    _____
    | |/ /   / \  |  _ \    / \  |  ___|
    | ' /   / _ \ | |_) |  / _ \ | |_
    | . \  / ___ \|  _ <  / ___ \|  _|
    |_|\_\/_/   \_\_| \_\/_/   \_\_|
EOF
        echo -e "${NC}"
    else
        cat << "EOF"
     _  __    _    ____      _    _____
    | |/ /   / \  |  _ \    / \  |  ___|
    | ' /   / _ \ | |_) |  / _ \ | |_
    | . \  / ___ \|  _ <  / ___ \|  _|
    |_|\_\/_/   \_\_| \_\/_/   \_\_|
EOF
    fi

    total_karaf_steps=4  # Increased for additional checks
    current_step=0

    # Check Karaf environment
    print_progress $((++current_step)) $total_karaf_steps "Checking Karaf environment..."
    if [ ! -d "package/target" ]; then
        print_status "error" "Build directory not found. Did the build complete successfully?"
        exit 1
    fi

    pushd package/target || {
        print_status "error" "Failed to change directory to package/target"
        exit 1
    }

    # Verify Unomi version is set
    if [ -z "$UNOMI_VERSION" ]; then
        print_status "error" "UNOMI_VERSION is not set"
        exit 1
    fi

    print_progress $((++current_step)) $total_karaf_steps "Uncompressing Unomi package..."
    if [ ! -f "unomi-$UNOMI_VERSION.tar.gz" ]; then
        print_status "error" "Unomi package not found: unomi-$UNOMI_VERSION.tar.gz"
        exit 1
    fi
    tar zxvf unomi-$UNOMI_VERSION.tar.gz

    print_progress $((++current_step)) $total_karaf_steps "Installing optional databases..."
    if [ -f "../../GeoLite2-City.mmdb" ]; then
        print_status "info" "Installing GeoLite2 City database..."
        cp -v ../../GeoLite2-City.mmdb unomi-$UNOMI_VERSION/etc || {
            print_status "error" "Failed to copy GeoLite2 database"
            exit 1
        }
    else
        print_status "info" "GeoLite2 City database not found (optional)"
    fi

    if [ -f "../../allCountries.zip" ]; then
        print_status "info" "Installing Geonames countries database..."
        cp -v ../../allCountries.zip unomi-$UNOMI_VERSION/etc || {
            print_status "error" "Failed to copy Geonames database"
            exit 1
        }
    else
        print_status "info" "Geonames countries database not found (optional)"
    fi

    cd unomi-$UNOMI_VERSION/bin || {
        print_status "error" "Failed to change directory to Karaf bin directory"
        exit 1
    }

    print_progress $((++current_step)) $total_karaf_steps "Starting Karaf..."
    if [ "$DEBUG" = true ]; then
        print_status "info" "Debug mode enabled (port: $KARAF_DEBUG_PORT, suspend: $KARAF_DEBUG_SUSPEND)"
        if command -v nc >/dev/null 2>&1; then
            if nc -z localhost "$KARAF_DEBUG_PORT" 2>/dev/null; then
                print_status "error" "Port $KARAF_DEBUG_PORT is already in use"
                exit 1
            fi
        fi
        export KARAF_DEBUG=true
        export JAVA_DEBUG_OPTS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=$KARAF_DEBUG_SUSPEND,address=$KARAF_DEBUG_PORT"
    fi

    if [ ! -x "./karaf" ]; then
        print_status "error" "Karaf executable not found or not executable"
        exit 1
    fi

    if [ ! -z "$AUTO_START" ]; then
        print_status "info" "Configuring auto-start for $AUTO_START"
        export KARAF_OPTS="-Dunomi.autoStart=$AUTO_START"
    else
        print_status "info" "Use [unomi:start] to start Unomi after Karaf initialization"
    fi

    ./karaf || {
        print_status "error" "Karaf failed to start"
        exit 1
    }
    popd || true
else
    print_status "info" "Skipping Karaf startup (--no-karaf specified)"
    if [ ! -z "$AUTO_START" ]; then
        print_status "info" "Note: auto-start ($AUTO_START) will be applied when Karaf is started manually"
    fi
fi

cat << "EOF"
     ____   ___  _   _ _____ _
    |  _ \ / _ \| \ | | ____| |
    | | | | | | |  \| |  _| | |
    | |_| | |_| | |\  | |___|_|
    |____/ \___/|_| \_|_____(_)

EOF
echo "Operation completed successfully."
