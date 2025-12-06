#!/usr/bin/env bash
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

# Load utility functions and configuration (can be used by other scripts)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [ -f "$SCRIPT_DIR/generate-manual-config.sh" ]; then
    source "$SCRIPT_DIR/generate-manual-config.sh"
fi
if [ -f "$SCRIPT_DIR/shell-utils.sh" ]; then
    source "$SCRIPT_DIR/shell-utils.sh"
fi

# Configuration is now loaded from generate-manual-config.sh
# Colors and utility functions are loaded from shell-utils.sh


show_banner() {
    clear
    echo -e "${CYAN}╔══════════════════════════════════════════════════════╗${NC}"
    typewriter_effect "${CYAN}║${WHITE}           🚀 APACHE UNOMI MANUAL GENERATOR 🚀          ${CYAN}║"
    echo -e "${CYAN}╚══════════════════════════════════════════════════════╝${NC}"
    echo
    rainbow_text "           ✨ The Ultimate Documentation Tool ✨"
    echo
}

show_usage() {
    echo -e "${YELLOW}${BOLD}USAGE:${NC}"
    echo -e "${WHITE}  $0 ${GREEN}publish${NC} ${CYAN}<user> <pass>${NC}                              ${DIM}# Generate all 2 versions + publish to SVN${NC}"
    echo -e "${WHITE}  $0 ${GREEN}simulate${NC} ${CYAN}<user> <pass>${NC}                             ${DIM}# Simulate publish (dry-run)${NC}"
    echo
    echo
    echo -e "${YELLOW}${BOLD}EXAMPLES:${NC}"
    echo -e "${WHITE}  $0 publish myuser mypass${NC}"
    echo -e "${WHITE}  $0 simulate myuser mypass${NC}"
    echo
    echo -e "${PURPLE}${BOLD}WHAT IT GENERATES:${NC}"
    echo -e "${GREEN}  Always generates exactly 2 versions:${NC}"
    echo -e "  ${CYAN}$LATEST_DIR/${NC}    - From $LATEST_BRANCH branch ($LATEST_VERSION)"
    echo -e "  ${CYAN}$STABLE_DIR/${NC}     - From $STABLE_BRANCH branch ($STABLE_VERSION)"
    echo
    echo -e "${PURPLE}${BOLD}MODES:${NC}"
    echo -e "${GREEN}  publish${NC}  - Generate all documentation and publish to Apache SVN"
    echo -e "           ${DIM}• Generates all 2 versions (latest + stable)${NC}"
    echo -e "           ${DIM}• Uploads to $SVN_WEBSITE_BASE/manual${NC}"
    echo -e "           ${DIM}• Publishes API docs from master branch${NC}"
    echo -e "           ${DIM}• Uploads release packages (PDF/ZIP) to Apache Dist SVN${NC}"
    echo -e "           ${DIM}• Removes any old versions automatically${NC}"
    echo
    echo -e "${GREEN}  simulate${NC} - Preview what publish would do (dry-run)"
    echo -e "           ${DIM}• Shows all commands that would be executed${NC}"
    echo -e "           ${DIM}• Safe to test without making changes${NC}"
    echo
    echo -e "${CYAN}${BOLD}REQUIREMENTS:${NC}"
    echo -e "${WHITE}  • Maven 3.6+ with Java 11+${NC}"
    echo -e "${WHITE}  • Git with access to master and 2_7_x branches${NC}"
    echo -e "${WHITE}  • SVN client (for publish/simulate modes)${NC}"
    echo -e "${WHITE}  • bc command (for progress animations)${NC}"
    echo
    echo -e "${CYAN}${BOLD}NOTES:${NC}"
    echo -e "${WHITE}  • All temporary files are created in $TEMP_DIR_BASE/${NC}"
    echo -e "${WHITE}  • Use 'mvn $MAVEN_CLEAN_GOAL' to remove all generated files${NC}"
    echo -e "${WHITE}  • Always generates exactly 2 versions (latest, 2_7_x)${NC}"
    echo -e "${WHITE}  • Script works on macOS and Linux${NC}"
    echo
}

execute_or_simulate_dist_svn() {
    local simulate="$1"
    local branch_name="$2"
    local version="$3"
    local manual_target_dir="$4"
    local svn_username="$5"
    local svn_password="$6"

    local DIST_SVN_URL="$SVN_DIST_BASE/$version"

    animate_loading "Preparing Apache Dist SVN upload" 2

    pushd "$manual_target_dir" >/dev/null

    log_info "Checking out Apache Dist SVN for version: ${GREEN}$version${NC}"
    if ! run_svn_command "svn checkout \"$DIST_SVN_URL\""; then
        log_error "Failed to checkout Apache Dist SVN for version $version"
        popd >/dev/null
        return 1
    fi

    log_info "Moving manual packages to dist directory..."

    # Helper function for safe move with logging
    move_file() {
        local src="$1"
        local dest="$2"
        local desc="$3"

        if [ -f "$src" ]; then
            if mv "$src" "$dest" 2>/dev/null; then
                log_info "Moved: $src → $dest"
            else
                log_warning "Failed to move $desc: $src → $dest"
            fi
        else
            log_warning "$desc not found: $src"
        fi
    }

    move_file "unomi-manual-$version.pdf" "$version/" "PDF file"
    move_file "unomi-manual-$version.pdf.asc" "$version/" "PDF signature"
    move_file "unomi-manual-$version.zip" "$version/" "ZIP file"
    move_file "unomi-manual-$version.pdf.sha512" "$version/" "PDF checksum"
    move_file "unomi-manual-$version.zip.asc" "$version/" "ZIP signature"
    move_file "unomi-manual-$version.zip.sha512" "$version/" "ZIP checksum"

    pushd "$version" >/dev/null

    animate_loading "Adding manual packages to SVN" 1
    # Extract function: add files to SVN but tolerate "already versioned" cases.
    add_manual_packages_to_svn_if_needed() {
        # Uses current directory; expects logging helpers and run_svn_command
        local pattern="unomi-manual*"
        # Try the add; capture output and status
        local output
        output="$(svn add $pattern 2>&1)"
        local status=$?

        if [ $status -eq 0 ]; then
            # Added successfully
            return 0
        fi

        # If some targets are already versioned, warn and continue
        if echo "$output" | grep -qE 'W150002|E200009|already under version control|Could not add all targets'; then
            log_warning "Manual packages already under version control; continuing."
            log_debug "svn add output: $output"
            return 0
        fi

        # Unexpected failure: log and propagate error
        log_error "Failed to add manual packages to SVN"
        log_debug "svn add output: $output"
        return 1
    }

    if ! add_manual_packages_to_svn_if_needed; then
        popd >/dev/null
        popd >/dev/null
        return 1
    fi
    if [ "$simulate" = "true" ]; then
        log_simulate "Would upload manual packages to Apache Dist SVN"
        log_command "svn commit -m \"Update Unomi manual packages for version $version\" --username=\"$svn_username\" --password=\"$svn_password\""
    else
        animate_loading "Committing manual packages to Apache Dist SVN" 2
        if ! run_svn_command "svn commit -m \"Update Unomi manual packages for version $version\" --username=\"$svn_username\" --password=\"$svn_password\""; then
            log_error "Failed to commit manual packages to Apache Dist SVN"
            popd >/dev/null
            popd >/dev/null
            return 1
        fi
    fi
    popd >/dev/null
    popd >/dev/null

    log_success "Manual packages uploaded to Apache Dist SVN! 📦"
}

check_requirements() {
    local mode="$1"
    local errors=0

    log_step "🔍 Checking system requirements..."
    echo

    # Use utility functions from shell-utils.sh
    check_command "java" "Java 11+" "true" || ((errors++))
    check_command "mvn" "Maven 3.6+" "true" || ((errors++))
    check_command "git" "Git" "true" || ((errors++))
    check_git_repository || ((errors++))

    # Check SVN based on mode
    if [ "$mode" = "publish" ] || [ "$mode" = "multi" ]; then
        check_command "svn" "SVN" "true" || ((errors++))
    else
        check_command "svn" "SVN" "false"
    fi

    check_command "bc" "bc (for enhanced animations)" "false"

    echo
    log_step "🔧 Additional environment checks..."

    check_directory_writable "." || ((errors++))
    check_disk_space "$MIN_DISK_SPACE_MB"
    check_memory "$MIN_MEMORY_MB"

    echo
    if [ $errors -eq 0 ]; then
        log_success "All required dependencies are available! 🎉"
        echo
        return 0
    else
        log_error "Found $errors requirement error(s). Please $MAVEN_INSTALL_GOAL missing dependencies."
        echo
        log_info "Installation hints:"
        echo -e "${DIM}  • Java 11+: https://adoptopenjdk.net/${NC}"
        echo -e "${DIM}  • Maven 3.6+: https://maven.apache.org/download.cgi${NC}"
        echo -e "${DIM}  • Git: https://git-scm.com/downloads${NC}"
        if [ "$mode" = "publish" ] || [ "$mode" = "multi" ]; then
            echo -e "${DIM}  • SVN: https://subversion.apache.org/download.cgi${NC}"
        fi
        echo -e "${DIM}  • bc: Usually available via package manager (brew, apt, yum)${NC}"
        echo
        return 1
    fi
}

detect_javadoc_output_dir() {
    local base_dir="${1:-.}"

    # Check multiple possible Javadoc output directories
    local possible_dirs=(
        "$base_dir/target/site/apidocs"      # Maven site plugin (classic)
        "$base_dir/target/reports/apidocs"   # Maven reporting plugin (newer)
        "$base_dir/target/apidocs"           # Direct javadoc plugin
    )

    for dir in "${possible_dirs[@]}"; do
        if [ -d "$dir" ]; then
            echo "$dir"
            return 0
        fi
    done

    # If none exist, check which one would be created based on POM configuration
    if grep -q "<reporting>" "$base_dir/pom.xml" 2>/dev/null; then
        echo "$base_dir/target/site/apidocs"  # Reporting section uses site
    else
        echo "$base_dir/target/reports/apidocs"  # Modern default
    fi
}

setup_environment() {
    log_step "Setting up environment..."

    DIRNAME=$(dirname "$0")

    if [ -f "$DIRNAME/setenv.sh" ]; then
        . "$DIRNAME/setenv.sh"
        log_info "Loaded environment from setenv.sh"
    fi

    # Create target directory structure for temporary files and logs
    mkdir -p "$TEMP_DIR_BASE/logs"
    TEMP_DIR="$TEMP_DIR_BASE"
    LOG_DIR="$TEMP_DIR/logs"

    # Create timestamped log file with absolute path
    TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
    MAIN_LOG="$(pwd)/$LOG_DIR/generate-manual_$TIMESTAMP.log"
    export MAIN_LOG

    # Initialize log file
    touch "$MAIN_LOG"

    # Only show essential paths to user, details go to logs
    log_info "Temporary directory: ${GREEN}$(basename "$TEMP_DIR")${NC}"
    # Log full details to file only
    if [ -n "${MAIN_LOG:-}" ]; then
        echo "$(date '+%Y-%m-%d %H:%M:%S') [INFO] Full temporary directory path: $(cd "$TEMP_DIR" && pwd)" >> "$MAIN_LOG"
        echo "$(date '+%Y-%m-%d %H:%M:%S') [INFO] Log directory path: $(cd "$LOG_DIR" && pwd)" >> "$MAIN_LOG"
        echo "$(date '+%Y-%m-%d %H:%M:%S') [INFO] Main log file: $MAIN_LOG" >> "$MAIN_LOG"
    fi

    set -e

    LOCAL_BRANCH_NAME=$(git rev-parse --abbrev-ref HEAD)
    log_info "Current git branch: ${GREEN}${LOCAL_BRANCH_NAME}${NC}"
}

generate_docs_local() {
    local branch_name="$1"
    local version="$2"
    local output_dir="$3"

    log_step "🎯 Generating complete documentation for ${CYAN}${branch_name}${NC} version ${YELLOW}${version}${NC}"

    # IMPORTANT PATH DISTINCTION:
    # CLONE_* variables: Paths within the current temporary git clone directory
    # PROJECT_* variables: Paths within the original project root directory
    # This prevents confusion between temporary clone staging and project staging

    animate_loading "Cleaning previous builds" 1
    if ! run_maven_command "mvn $MAVEN_CLEAN_GOAL"; then
        log_error "Maven clean failed for $branch_name"
        return 1
    fi

    log_step "📚 Building manual documentation (HTML, PDF, ZIP)..."
    pushd manual >/dev/null

    # Generate complete documentation set for all branches with versioned output
    animate_loading "Generating versioned PDF and HTML docs" 3
    if ! run_maven_command "mvn -Ddoc.archive=true -Ddoc.output.pdf=target/generated-docs/pdf/$branch_name -Ddoc.output.html=target/generated-docs/html/$branch_name -Ddoc.version=$version -P $MAVEN_SIGN_PROFILE $MAVEN_INSTALL_GOAL"; then
        log_error "Maven versioned documentation build failed for $branch_name"
        popd >/dev/null
        return 1
    fi

    popd >/dev/null

    log_step "📖 Generating Javadoc API documentation..."
    animate_loading "Building aggregated Javadocs" 4
    if ! run_maven_command "mvn -DskipTests $MAVEN_INSTALL_GOAL $MAVEN_JAVADOC_GOAL -P $MAVEN_INTEGRATION_PROFILE"; then
        log_warning "Maven Javadoc generation failed for $branch_name (likely Java version issue)"
        log_info "Continuing without API documentation..."
        # Don't fail the entire process for Javadoc issues
    else
        log_success "Javadoc generation completed successfully"

        # Dynamically detect the actual Javadoc output directory
        ACTUAL_API_DIR=$(detect_javadoc_output_dir ".")
        log_debug "Detected Javadoc directory: $(cd "$ACTUAL_API_DIR" && pwd)"

        if [ -d "$ACTUAL_API_DIR" ]; then
            log_info "Javadoc directory found with $(find "$ACTUAL_API_DIR" -name "*.html" 2>/dev/null | wc -l) HTML files"
            # Update CLONE_API_TARGET_DIR to the actual location for this branch
            CLONE_API_TARGET_DIR="$ACTUAL_API_DIR"
        else
            log_warning "Javadoc directory not found after generation: $(cd "$(dirname "$ACTUAL_API_DIR")" && pwd)/$(basename "$ACTUAL_API_DIR")"
        fi
    fi

    log_step "📦 Staging documentation files..."

    # Create staging directories using output_dir for final directory structure
    mkdir -p "$CLONE_STAGING_DIR/unomi-api/$output_dir"
    mkdir -p "$CLONE_STAGING_DIR/manual/$output_dir"

    animate_loading "Copying API documentation" 1
    # Copy API docs - they are generated in target/site/apidocs
    log_debug "Checking API docs in: $(cd "$CLONE_API_TARGET_DIR" && pwd)"
    if [ -d "$CLONE_API_TARGET_DIR" ]; then
        log_debug "API docs directory exists, copying $(find "$CLONE_API_TARGET_DIR" -name "*.html" 2>/dev/null | wc -l) HTML files"
        log_debug "Copying API docs: $(cd "$CLONE_API_TARGET_DIR" && pwd)/* → $(cd "$CLONE_STAGING_DIR/unomi-api/$output_dir" && pwd)/"
        if ! cp -R "$CLONE_API_TARGET_DIR"/* "$CLONE_STAGING_DIR/unomi-api/$output_dir/" 2>/dev/null; then
            log_warning "Failed to copy API documentation files: $(cd "$CLONE_API_TARGET_DIR" && pwd)/* → $(cd "$CLONE_STAGING_DIR/unomi-api/$output_dir" && pwd)/"
        else
            log_debug "Successfully copied API documentation files"
        fi
    else
        log_warning "API docs directory does not exist: $(cd "$(dirname "$CLONE_API_TARGET_DIR")" 2>/dev/null && pwd)/$(basename "$CLONE_API_TARGET_DIR") (absolute path)"
    fi

    animate_loading "Copying manual documentation" 1
    # Copy manual documentation from versioned directory
    if [ -d "$CLONE_MANUAL_TARGET_DIR/html/$branch_name" ]; then
        log_debug "Copying manual docs: $(cd "$CLONE_MANUAL_TARGET_DIR/html/$branch_name" && pwd)/* → $(cd "$CLONE_STAGING_DIR/manual/$output_dir" && pwd)/"
        if cp -Rf "$CLONE_MANUAL_TARGET_DIR/html/$branch_name"/* "$CLONE_STAGING_DIR/manual/$output_dir/"; then
            log_debug "Successfully copied manual documentation files"
        else
            log_warning "Failed to copy manual documentation files: $(cd "$CLONE_MANUAL_TARGET_DIR/html/$branch_name" 2>/dev/null && pwd)/* → $(cd "$CLONE_STAGING_DIR/manual/$output_dir" && pwd)/"
        fi
    else
        log_warning "Manual HTML not found in expected location: $(cd "$CLONE_MANUAL_TARGET_DIR/html" 2>/dev/null && pwd)/$branch_name (absolute path)"
    fi

    log_success "Complete documentation set generated! 🎉"
    log_debug "Documentation staged in: $(cd "$CLONE_STAGING_DIR" && pwd)/"
}

multi_version_generate() {
    local mode="$1"  # "publish" or "simulate"
    local svn_username="$2"
    local svn_password="$3"
    shift 3
    local version_configs=("$@")

    echo
    if [ "$mode" = "simulate" ]; then
        log_step "🎭 Simulating multi-version documentation generation"
    else
        log_step "🎯 Multi-version documentation generation"
    fi
    log_info "Processing ${#version_configs[@]} version configurations..."

    local REPO_ROOT=$(pwd)
    local DEBUG_TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
    local TEMP_REPO_PREFIX="$TEMP_DIR/repo_${DEBUG_TIMESTAMP}"
    local SVN_BASE_URL="$SVN_WEBSITE_BASE/manual"
    local TEMP_CHECKOUT_DIR="$TEMP_DIR/svn-manual_${DEBUG_TIMESTAMP}"

    animate_loading "Checking out SVN manual directory" 2
    # if [ -d "$TEMP_CHECKOUT_DIR" ]; then
    #     rm -rf "$TEMP_CHECKOUT_DIR"  # Commented out for debugging
    # fi
    log_debug "SVN checkout directory: $TEMP_CHECKOUT_DIR"
    if ! run_svn_command "svn checkout \"$SVN_BASE_URL\" \"$TEMP_CHECKOUT_DIR\" --username=\"$svn_username\" --password=\"$svn_password\""; then
        log_error "Failed to checkout SVN manual directory"
        return 1
    fi

    local LATEST_VERSION=""
    local GENERATED_VERSIONS=()

    for config in "${version_configs[@]}"; do
        IFS=':' read -r branch_name version_number target_dir is_latest <<< "$config"

        echo
        log_step "🔄 Processing ${CYAN}$branch_name${NC}:${YELLOW}$version_number${NC} → ${GREEN}$target_dir${NC} (latest=$is_latest)"

        local TEMP_REPO_DIR="${TEMP_REPO_PREFIX}_${branch_name}"

        animate_loading "Creating temporary repository for branch $branch_name" 1
        # if [ -d "$TEMP_REPO_DIR" ]; then
        #     rm -rf "$TEMP_REPO_DIR"  # Commented out for debugging
        # fi
        log_debug "Temporary repo directory: $TEMP_REPO_DIR"
        log_info "Cloning from official repository: ${CYAN}$GIT_REPO_URL${NC}"

        if ! run_git_command "git clone \"$GIT_REPO_URL\" \"$TEMP_REPO_DIR\""; then
            log_error "Git clone failed for $branch_name"
            continue
        fi

        pushd "$TEMP_REPO_DIR" >/dev/null

        if [ "$branch_name" != "master" ]; then
            log_debug "Checking out branch: $branch_name"
            if ! run_git_command "git checkout \"$branch_name\""; then
                log_error "Git checkout failed for $branch_name"
                popd >/dev/null
                continue
            fi
        fi

        animate_loading "Generating documentation for $branch_name" 3
        generate_docs_local "$branch_name" "$version_number" "$target_dir"

        if [ ! -d "$CLONE_STAGING_DIR/manual" ]; then
            log_error "Documentation generation failed for $branch_name"
            popd >/dev/null
            continue
        fi

        popd >/dev/null

        # Copy documentation from clone staging to main project staging area (for all modes)
        log_debug "Copying documentation to main staging area: $target_dir"
        mkdir -p "$REPO_ROOT/$PROJECT_STAGING_DIR/manual/$target_dir"
        mkdir -p "$REPO_ROOT/$PROJECT_STAGING_DIR/unomi-api/$target_dir"

        if [ -d "$TEMP_REPO_DIR/$CLONE_STAGING_DIR/manual/$target_dir" ]; then
            log_debug "Copying manual docs to main staging: $(cd "$TEMP_REPO_DIR/$CLONE_STAGING_DIR/manual/$target_dir" && pwd)/* → $(cd "$REPO_ROOT/$PROJECT_STAGING_DIR/manual/$target_dir" && pwd)/"
            if ! cp -Rf "$TEMP_REPO_DIR/$CLONE_STAGING_DIR/manual/$target_dir"/* "$REPO_ROOT/$PROJECT_STAGING_DIR/manual/$target_dir/" 2>/dev/null; then
                log_warning "Failed to copy manual documentation files: $(cd "$TEMP_REPO_DIR/$CLONE_STAGING_DIR/manual/$target_dir" 2>/dev/null && pwd)/* → $(cd "$REPO_ROOT/$PROJECT_STAGING_DIR/manual/$target_dir" && pwd)/"
            else
                log_debug "Successfully copied manual documentation to main staging"
            fi
        fi

        if [ -d "$TEMP_REPO_DIR/$CLONE_STAGING_DIR/unomi-api/$target_dir" ]; then
            log_debug "Copying API docs to main staging: $(cd "$TEMP_REPO_DIR/$CLONE_STAGING_DIR/unomi-api/$target_dir" && pwd)/* → $(cd "$REPO_ROOT/$PROJECT_STAGING_DIR/unomi-api/$target_dir" && pwd)/"
            if ! cp -Rf "$TEMP_REPO_DIR/$CLONE_STAGING_DIR/unomi-api/$target_dir"/* "$REPO_ROOT/$PROJECT_STAGING_DIR/unomi-api/$target_dir/" 2>/dev/null; then
                log_warning "Failed to copy API documentation files: $(cd "$TEMP_REPO_DIR/$CLONE_STAGING_DIR/unomi-api/$target_dir" 2>/dev/null && pwd)/* → $(cd "$REPO_ROOT/$PROJECT_STAGING_DIR/unomi-api/$target_dir" && pwd)/"
            else
                log_debug "Successfully copied API documentation to main staging"
            fi
        fi

        # Copy documentation to SVN directory
        local TARGET_DIR="$TEMP_CHECKOUT_DIR/$target_dir"
        log_debug "Copying documentation to SVN directory: $target_dir"

        if [ ! -d "$TARGET_DIR" ]; then
            mkdir -p "$TARGET_DIR"
            pushd "$TEMP_CHECKOUT_DIR" >/dev/null
            run_svn_command "svn add \"$target_dir\""
            popd >/dev/null
        fi

        rm -rf "$TARGET_DIR"/*
        log_debug "Copying to SVN: $(cd "$TEMP_REPO_DIR/$CLONE_STAGING_DIR/manual/$target_dir" && pwd)/* → $(cd "$TARGET_DIR" && pwd)/"
        if cp -R "$TEMP_REPO_DIR/$CLONE_STAGING_DIR/manual"/$target_dir/* "$TARGET_DIR"/; then
            log_debug "Successfully copied documentation to SVN directory"
        else
            log_warning "Failed to copy documentation to SVN directory: $(cd "$TEMP_REPO_DIR/$CLONE_STAGING_DIR/manual/$target_dir" 2>/dev/null && pwd)/* → $(cd "$TARGET_DIR" && pwd)/"
        fi

        pushd "$TEMP_CHECKOUT_DIR" >/dev/null
        run_svn_command "svn add --force \"$target_dir\""
        popd >/dev/null

        GENERATED_VERSIONS+=("$target_dir")

        if [ "$is_latest" = "true" ]; then
            LATEST_VERSION="$target_dir"
        fi

        # Apache Dist SVN upload for all branches that generate release artifacts
        if [ "$branch_name" != "master" ]; then  # Only upload to Dist SVN for actual releases, not development snapshots
            echo
            local simulate_mode="false"
            if [ "$mode" = "simulate" ]; then
                simulate_mode="true"
                log_step "🎭 Simulating Apache Dist SVN upload for $branch_name and version $version_number..."
            else
                log_step "📦 Uploading release artifacts to Apache Dist SVN for $branch_name and version $version_number..."
            fi

            local MANUAL_TARGET_DIR="$TEMP_REPO_DIR/manual/target"
            if ! execute_or_simulate_dist_svn "$simulate_mode" "$branch_name" "$version_number" "$MANUAL_TARGET_DIR" "$svn_username" "$svn_password"; then
                # rm -rf "$TEMP_REPO_DIR"  # Commented out for debugging
                log_debug "Preserving temp directory for analysis: $TEMP_REPO_DIR"
                continue
            fi
        fi

        # rm -rf "$TEMP_REPO_DIR"  # Commented out for debugging
        log_debug "Preserving temp directory for analysis: $TEMP_REPO_DIR"
    done

    log_step "🧹 Managing version retention..."
    pushd "$TEMP_CHECKOUT_DIR" >/dev/null

    # Always preserve the currently generated versions
    local DIRS_TO_KEEP=("${GENERATED_VERSIONS[@]}")

    log_info "Generated versions (preserved): ${GREEN}${DIRS_TO_KEEP[*]}${NC}"

    for dir in */; do
        dir_name="${dir%/}"

        if [[ ! " ${DIRS_TO_KEEP[*]} " =~ " $dir_name " ]]; then
            log_warning "Removing old version directory: $dir_name"
            run_svn_command "svn remove \"$dir_name\""
        else
            log_info "Preserving directory: $dir_name"
        fi
    done

    if [ "$mode" == "publish" ]; then
        animate_loading "Committing all changes to SVN" 3
        local COMMIT_MSG="Update manual documentation

Generated versions: ${GENERATED_VERSIONS[*]}
Latest version: $LATEST_VERSION
Preserved versions: ${DIRS_TO_KEEP[*]}"

        if ! run_svn_command "svn commit -m \"$COMMIT_MSG\" --username=\"$svn_username\" --password=\"$svn_password\""; then
            log_error "Failed to commit to SVN"
            return 1
        fi
    else
        animate_loading "Simulating committing all changes to SVN" 3
        local COMMIT_MSG="Update manual documentation

Generated versions: ${GENERATED_VERSIONS[*]}
Latest version: $LATEST_VERSION
Preserved versions: ${DIRS_TO_KEEP[*]}"

        log_command "svn commit -m \"$COMMIT_MSG\" --username=\"$svn_username\" --password=\"$svn_password\""
    fi

    popd >/dev/null
    # rm -rf "$TEMP_CHECKOUT_DIR"  # Commented out for debugging
    log_debug "Preserving SVN checkout directory for analysis: $TEMP_CHECKOUT_DIR"

    if [ "$mode" = "simulate" ]; then
        log_success "Multi-version documentation simulation completed! 🎊"
        log_info "Generated versions: ${GREEN}${GENERATED_VERSIONS[*]}${NC}"
        if [ -n "$LATEST_VERSION" ]; then
            log_info "Latest version set to: ${GREEN}$LATEST_VERSION${NC}"
        fi
        # Count release branches (non-master) for dist upload summary
        local RELEASE_BRANCHES=()
        for version in "${GENERATED_VERSIONS[@]}"; do
            if [ "$version" != "master" ]; then
                RELEASE_BRANCHES+=("$version")
            fi
        done
        if [ ${#RELEASE_BRANCHES[@]} -gt 0 ]; then
            log_info "Would upload manual packages to Apache Dist SVN for: ${GREEN}${RELEASE_BRANCHES[*]}${NC}"
        fi
    else
        log_success "Multi-version documentation generation completed! 🎊"
        log_info "Generated versions: ${GREEN}${GENERATED_VERSIONS[*]}${NC}"
        if [ -n "$LATEST_VERSION" ]; then
            log_info "Latest version set to: ${GREEN}$LATEST_VERSION${NC}"
        fi
        # Count release branches (non-master) for dist upload summary
        local RELEASE_BRANCHES=()
        for version in "${GENERATED_VERSIONS[@]}"; do
            if [ "$version" != "master" ]; then
                RELEASE_BRANCHES+=("$version")
            fi
        done
        if [ ${#RELEASE_BRANCHES[@]} -gt 0 ]; then
            log_info "Manual packages uploaded to Apache Dist SVN for: ${GREEN}${RELEASE_BRANCHES[*]}${NC}"
        fi
    fi
}

celebrate_completion() {
    echo
    echo -e "${GREEN}${BOLD}🎉 SUCCESS! 🎉${NC}"
    echo -e "${GREEN}Documentation generation completed successfully!${NC}"
    echo
    echo -e "${CYAN}Thanks for using the Unomi Manual Generator! 🚀${NC}"
    echo
}

# Main script logic
main() {
    show_banner

    if [ $# -eq 0 ]; then
        show_usage
        exit 1
    fi

    local mode="$1"

    # Check requirements before proceeding
    if ! check_requirements "$mode"; then
        exit 1
    fi

    setup_environment
    shift

    case "$mode" in
        "publish")
            if [ $# -ne 2 ]; then
                log_error "Publish mode requires exactly 2 arguments: username, password"
                show_usage
                exit 1
            fi

            # Always generate and publish all 2 versions
            multi_version_generate "publish" "$1" "$2" "$LATEST_BRANCH:$LATEST_VERSION:$LATEST_DIR:true" "$STABLE_BRANCH:$STABLE_VERSION:$STABLE_DIR:false"
            celebrate_completion
            ;;

        "simulate")
            if [ $# -ne 2 ]; then
                log_error "Simulate mode requires exactly 2 arguments: username, password"
                show_usage
                exit 1
            fi

            # Always simulate all 2 versions
            multi_version_generate "simulate" "$1" "$2" "$LATEST_BRANCH:$LATEST_VERSION:$LATEST_DIR:true" "$STABLE_BRANCH:$STABLE_VERSION:$STABLE_DIR:false"
            celebrate_completion
            ;;

        *)
            log_error "Unknown mode: $mode"
            show_usage
            exit 1
            ;;
    esac

}

# Run the main function
main "$@"
