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

# Generate Manual Configuration File
# Edit these variables to configure which versions are generated
# Part of the Apache Unomi Manual Generator toolkit

# === VERSION CONFIGURATION ===
# Latest version (master branch)
LATEST_BRANCH="master"
LATEST_VERSION="3.1.0-SNAPSHOT"
LATEST_DIR="latest"

# Stable version (release branch)
STABLE_BRANCH="unomi-3.0.x"
STABLE_VERSION="3.0.0"
STABLE_DIR="3_0_x"

# === INFRASTRUCTURE CONFIGURATION ===
# Git repository URL
GIT_REPO_URL="https://gitbox.apache.org/repos/asf/unomi.git"

# SVN Base URLs
SVN_WEBSITE_BASE="https://svn.apache.org/repos/asf/unomi/website"
SVN_DIST_BASE="https://dist.apache.org/repos/dist/release/unomi"

# Temporary directory base (will be created under target/)
TEMP_DIR_BASE="target/generated-manual"

# === SYSTEM REQUIREMENTS ===
# Minimum system requirements for validation
MIN_DISK_SPACE_MB=200
MIN_MEMORY_MB=512
JAVA_MIN_VERSION=11
MAVEN_MIN_VERSION="3.6"

# === LOGGING CONFIGURATION ===
# Log file retention (days)
LOG_RETENTION_DAYS=7

# Log levels: DEBUG, INFO, WARN, ERROR
DEFAULT_LOG_LEVEL="INFO"

# === MAVEN CONFIGURATION ===
# Maven profiles to use
MAVEN_SIGN_PROFILE="sign"
MAVEN_INTEGRATION_PROFILE="integration-tests"

# Maven goals for different operations
MAVEN_CLEAN_GOAL="clean"
MAVEN_INSTALL_GOAL="install"
MAVEN_JAVADOC_GOAL="javadoc:aggregate"

# === DOCUMENTATION PATHS ===
# These paths are relative to INDIVIDUAL GIT CLONE directories (not the original project root)
# Each temporary git clone will have these paths within it
CLONE_MANUAL_SOURCE_DIR="manual/src/main/asciidoc"
CLONE_MANUAL_TARGET_DIR="manual/target/generated-docs"
CLONE_API_TARGET_DIR="target/site/apidocs"
CLONE_STAGING_DIR="target/staging"

# These paths are relative to the ORIGINAL PROJECT ROOT for aggregated results
PROJECT_STAGING_DIR="target/staging"

# === TIMEOUTS ===
# Command timeouts in seconds
MAVEN_TIMEOUT=1800  # 30 minutes
GIT_TIMEOUT=300     # 5 minutes
SVN_TIMEOUT=600     # 10 minutes

# Export all configuration variables
export LATEST_BRANCH LATEST_VERSION LATEST_DIR
export STABLE_BRANCH STABLE_VERSION STABLE_DIR
export GIT_REPO_URL SVN_WEBSITE_BASE SVN_DIST_BASE TEMP_DIR_BASE
export MIN_DISK_SPACE_MB MIN_MEMORY_MB JAVA_MIN_VERSION MAVEN_MIN_VERSION
export LOG_RETENTION_DAYS DEFAULT_LOG_LEVEL
export MAVEN_SIGN_PROFILE MAVEN_INTEGRATION_PROFILE
export MAVEN_CLEAN_GOAL MAVEN_INSTALL_GOAL MAVEN_JAVADOC_GOAL
export CLONE_MANUAL_SOURCE_DIR CLONE_MANUAL_TARGET_DIR CLONE_API_TARGET_DIR CLONE_STAGING_DIR
export PROJECT_STAGING_DIR
export MAVEN_TIMEOUT GIT_TIMEOUT SVN_TIMEOUT
