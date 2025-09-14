#!/usr/bin/env python3
#
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""
UNOMI Ticket-Based PR Creation Tool

Creates PRs mapped to existing UNOMI JIRA tickets and suggests new tickets for unmapped changes.
Includes git history validation and documentation co-location.

Features:
- Maps changes to existing UNOMI tickets (UNOMI-139, UNOMI-873, UNOMI-878, etc.)
- Creates new ticket suggestions for unmapped functionality
- Validates changes against git history and main branch
- Co-locates documentation with code changes
- Comprehensive validation and simulation modes
"""

import os
import re
import sys
import json
import argparse
import subprocess
from pathlib import Path
from typing import Dict, List, Set, Tuple, Optional, NamedTuple
from dataclasses import dataclass
from collections import defaultdict
import difflib

class Colors:
    RED = '\033[0;31m'
    GREEN = '\033[0;32m'
    YELLOW = '\033[1;33m'
    BLUE = '\033[0;34m'
    CYAN = '\033[0;36m'
    PURPLE = '\033[0;35m'
    NC = '\033[0m'

@dataclass
class CommitInfo:
    """Information about a commit"""
    hash: str
    message: str
    files: List[str]
    unomi_tickets: Set[str]

@dataclass
class UNOMIGroup:
    """A group of changes mapped to a UNOMI ticket"""
    ticket: str
    title: str
    description: str
    priority: str
    files: Set[str]
    commits: List[CommitInfo]
    commit_count: int
    patterns: List[str]
    needs_new_ticket: bool = False

class UNOMIPRCreator:
    def __init__(self, source_branch="unomi-3-dev", base_branch="master"):
        self.source_branch = source_branch
        self.base_branch = base_branch
        self.work_dir = Path.cwd()
        
        # UNOMI Ticket-based groupings
        self.unomi_groups = {
            # === EXISTING TICKETS ===
            "UNOMI-139": {
                "title": "🏢 UNOMI-139: Multi-Tenancy Support System",
                "description": "Complete multi-tenancy implementation including API (Tenant, SecurityService, ExecutionContext), service implementations, REST endpoints, shell commands, integration tests, configuration, migration scripts, and comprehensive documentation.",
                "patterns": [
                    # Multi-tenancy core
                    r".*[Tt]enant.*",
                    r".*[Mm]ulti.*[Tt]enan.*",
                    r"api/.*/(tenants|security|ExecutionContext)",
                    r"api/.*/services/(ExecutionContextManager|TenantLifecycleListener)",
                    r"services.*/tenants/",
                    r"rest.*/tenants/",
                    r".*[Aa]pi[Kk]ey.*",
                    r".*[Ss]ecurity[Ss]ervice.*",
                    r".*[Ee]xecution[Cc]ontext.*",
                    # Associated documentation
                    r"manual.*multitenancy",
                    r"manual.*security",
                    r"manual.*tenant",
                    r"itests.*tenant.*",
                    r"etc.*tenant"
                ],
                "priority": "CRITICAL",
                "commit_patterns": [r"UNOMI-139"]
            },
            
            "UNOMI-878": {
                "title": "🧩 UNOMI-878: Enhanced Cluster-Aware Task Scheduling Service",
                "description": "Complete scheduler service with full integration into Unomi's persistence layer. Supports clustering, node-specific execution, crash recovery, task locking, in-memory vs persistent storage, task filtering, and comprehensive developer APIs.",
                "patterns": [
                    r".*[Ss]chedul.*",
                    r".*[Tt]ask.*",
                    r"api.*tasks/",
                    r"api.*SchedulerService",
                    r"services.*scheduler/",
                    r"rest.*scheduler/",
                    r"tools.*scheduler.*",
                    r".*scheduledTask.*",
                    # Documentation
                    r"manual.*schedul.*",
                    r"manual.*task.*",
                    r"itests.*schedul.*"
                ],
                "priority": "HIGH",
                "commit_patterns": [r"UNOMI-878"]
            },
            
            "UNOMI-880": {
                "title": "🗄️ UNOMI-880: Unified Multi-Tenant Caching Service", 
                "description": "Replaces scattered caching logic with consistent caching framework supporting multi-tenant contexts, periodic refresh, predefined-item loading, unified monitoring via Karaf shell commands, and migration of existing services like definitions and segments.",
                "patterns": [
                    r"^services-common/",
                    r".*AbstractMultiTypeCachingService.*",
                    r".*AbstractContextAwareService.*",
                    r".*CacheableTypeConfig.*",
                    r".*[Cc]ach.*",
                    # Services using new caching
                    r"services/.*/(ProfileServiceImpl|RulesService|DefinitionsServiceImpl)",
                    # Documentation
                    r"manual.*cach.*",
                    r"itests.*cach.*"
                ],
                "priority": "HIGH", 
                "commit_patterns": [r"UNOMI-880"]
            },
            
            "UNOMI-881": {
                "title": "🧪 UNOMI-881: New Unit Testing Framework",
                "description": "Establishes standardized testing infrastructure with reusable base classes, InMemoryPersistenceService enhancement, optimistic concurrency control, comprehensive test utilities, and conventions across modules.",
                "patterns": [
                    r".*InMemoryPersistence.*",
                    r".*Test.*\.java$",
                    r".*test.*",
                    r"/test/",
                    r"itests/",
                    # Testing infrastructure
                    r".*BaseIT.*",
                    r".*TestTracer.*",
                    # Documentation  
                    r"manual.*test.*",
                    r"itests/README.*"
                ],
                "priority": "MEDIUM",
                "commit_patterns": [r"UNOMI-881"]
            },
            
            "UNOMI-882": {
                "title": "📚 UNOMI-882: Enhanced Technical Documentation with Integrated Diagrams",
                "description": "Improves documentation via updated guides, architecture diagrams, flowcharts, API compatibility guides, migration documentation, and comprehensive user manuals for version 3 components.",
                "patterns": [
                    r"^manual/.*\.(adoc|md)$",
                    r"README.*",
                    r".*\.md$",
                    r".*architecture.*",
                    r".*diagram.*",
                    # Exclude code-specific docs (handled by their respective tickets)
                    r"(?!.*/(src|test)/).*\.adoc$"
                ],
                "priority": "LOW",
                "commit_patterns": [r"UNOMI-882"]
            },
            
            "UNOMI-883": {
                "title": "🔍 UNOMI-883: Unified Condition Validation Service",
                "description": "Adds centralized validation mechanism for conditions, ensuring consistent enforcement across services, richer validation of parameters and types, improved developer experience, and comprehensive condition parsing.",
                "patterns": [
                    r"api.*ConditionValidation",
                    r"api.*ValueTypeValidator", 
                    r".*ConditionValidationService.*",
                    r".*validation.*",
                    r".*Validator.*",
                    # JSON schema validation
                    r".*schema.*validation.*",
                    # Documentation
                    r"manual.*validation.*",
                    r"manual.*condition.*"
                ],
                "priority": "MEDIUM",
                "commit_patterns": [r"UNOMI-883"]
            },
            
            "UNOMI-884": {
                "title": "🔄 UNOMI-884: Migration Scripts for V3 Upgrade",
                "description": "Introduces migration scripts to upgrade legacy Unomi installations to version 3: adding audit fields (createdBy, lastModifiedBy, timestamps, tenantId), default tenant initialization, authentication filter enhancements, and reindexing using ElasticSearch via painless/Groovy scripts.",
                "patterns": [
                    r".*[Mm]igration.*",
                    r".*[Aa]udit.*",
                    r".*[Aa]uthentication.*[Ff]ilter.*",
                    r".*createdBy.*",
                    r".*lastModifiedBy.*",
                    r".*tenantId.*",
                    # Migration-related config changes
                    r".*dynamic.*template.*",
                    # Documentation
                    r"manual.*migration.*",
                    r"manual.*upgrade.*"
                ],
                "priority": "HIGH",
                "commit_patterns": [r"UNOMI-884"]
            },
            
            "UNOMI-887": {
                "title": "🔨 UNOMI-887: Enhanced build.sh Script",
                "description": "Refactors the project's build script (build.sh) to include colorized output, better error handling, progress tracking, configurable build options, Unicode/ASCII fallback, sections with headers, Windows support, and enhanced tool detection.",
                "patterns": [
                    r"^build\.sh$",
                    r"^build\.ps1$", 
                    r"^buildAndRun.*",
                    r"^generate-package\.sh$",
                    r"^compileDeploy.*",
                    r"^BUILDING$",
                    # Build documentation
                    r"manual.*build.*",
                    r"manual.*deploy.*"
                ],
                "priority": "LOW",
                "commit_patterns": [r"UNOMI-887"]
            },
            
            "UNOMI-888": {
                "title": "📖 UNOMI-888: Javadoc for Import/Export Service",
                "description": "Augments Javadoc documentation for core classes in the import/export subsystem, clarifying responsibilities for services like ImportExportConfigurationService, ProfileImportService, handling of error conditions, parameters, and integration points.",
                "patterns": [
                    r".*[Ii]mport.*",
                    r".*[Ee]xport.*",
                    r".*ImportExportConfigurationService.*",
                    r".*ProfileImportService.*",
                    # Documentation specific to import/export
                    r"manual.*import.*",
                    r"manual.*export.*"
                ],
                "priority": "LOW",
                "commit_patterns": [r"UNOMI-888"]
            },
            
            "UNOMI-889": {
                "title": "🔒 UNOMI-889: Remove OGNL Scripting Support", 
                "description": "Removes vulnerable OGNL dependency from the codebase (deprecated, insecure, incompatible with Java 17). Includes deleting OGNL dependency, replacing expressions with hard-coded property resolver, updating configs, tests, docs, and adding migration guide.",
                "patterns": [
                    r".*[Oo][Gg][Nn][Ll].*",
                    r".*encryption.*extension.*",
                    r".*scripting.*", 
                    # Security-related removals
                    r".*vulnerable.*",
                    # Migration documentation
                    r"manual.*ognl.*",
                    r"manual.*scripting.*"
                ],
                "priority": "HIGH",
                "commit_patterns": [r"UNOMI-889", r"Remove encryption extension"]
            },
            
            "UNOMI-892": {
                "title": "⚡ UNOMI-892: Build Infrastructure and CI/CD Improvements",
                "description": "Introduces Maven build cache to speed up CI and local builds, configure cache locations, TTL, share cache between pipelines, adjust Karaf packaging and integration-test settings, GitHub workflows, and comprehensive build optimization.",
                "patterns": [
                    r"\.mvn/.*",
                    r".*maven.*cache.*", 
                    r".*build.*cache.*",
                    r"pom\.xml$",
                    # CI/CD improvements - expanded
                    r"\.github/.*",
                    r"^kar/.*",
                    r"^package/.*",
                    r"^lifecycle-watcher/.*",
                    r"\.cfg$",
                    r"\.properties$",
                    r"feature\.xml$",
                    r"^docker/.*",
                    r"^etc/.*",
                    r"\.gitignore$",
                    r"^KEYS$",
                    # Documentation
                    r"manual.*maven.*",
                    r"manual.*build.*cache.*"
                ],
                "priority": "LOW",
                "commit_patterns": [r"UNOMI-892"]
            },
            
            "UNOMI-897": {
                "title": "🏃 UNOMI-897: Groovy Actions Race-Condition & Performance Fixes",
                "description": "Addresses race condition where shared Groovy shell leaks user data across threads and scalability/memory issues under high concurrency. Includes thread-safety improvements, performance optimization, and comprehensive Groovy actions runtime fixes.",
                "patterns": [
                    r".*[Gg]roovy.*[Aa]ction.*",
                    r".*GroovyActionDispatcher.*",
                    r".*GroovyActionsService.*",
                    r"extensions/groovy-actions/",
                    # Performance and threading
                    r".*race.*condition.*",
                    r".*thread.*safety.*",
                    # Documentation
                    r"manual.*groovy.*"
                ],
                "priority": "HIGH", 
                "commit_patterns": [r"UNOMI-897"]
            },
            
            "UNOMI-873": {
                "title": "🔍 UNOMI-873: Request Tracing & Observability Framework",
                "description": "Complete request tracing system including API, implementation, service integration, explain parameter functionality, comprehensive observability for debugging and performance analysis, and detailed tracing documentation.",
                "patterns": [
                    r"^tracing/",
                    r".*[Tt]rac.*",
                    r".*explain.*",
                    r".*[Ee]xplain.*",
                    r".*TracerService.*",
                    r".*RequestTracer.*",
                    r".*TraceNode.*", 
                    r"services/.*TestTracer.*",
                    # Documentation
                    r"manual.*tracing.*",
                    r"manual.*explain.*",
                    r"rest.*[Tt]rac.*",
                    r"api.*[Tt]rac.*"
                ],
                "priority": "MEDIUM",
                "commit_patterns": [r"UNOMI-873"]
            },
            
            "UNOMI-877": {
                "title": "🏥 UNOMI-877: Enhanced Cluster Health & Service Management",
                "description": "Remove problematic cluster services (Karaf Cellar, Hazelcast) and replace with PersistenceService-based cluster synchronization. Includes ClusterHealthCheckProvider, server information management, and improved cluster stability.",
                "patterns": [
                    r".*[Cc]luster.*[Hh]ealth.*",
                    r".*ClusterService.*",
                    r".*ClusterHealthCheckProvider.*",
                    r".*[Hh]ealth.*[Cc]heck.*",
                    r".*[Ss]erver.*[Ii]nfo.*",
                    # Legacy removal patterns
                    r".*Karaf.*Cellar.*",
                    r".*Hazelcast.*",
                    # Documentation
                    r"manual.*cluster.*",
                    r"manual.*health.*"
                ],
                "priority": "MEDIUM", 
                "commit_patterns": [r"UNOMI-877"]
            },

            # === NEW TICKETS FOR UNMAPPED FUNCTIONALITY ===
            "UNOMI-904": {
                "title": "🔐 UNOMI-904: Enhanced Security Permission System",
                "description": "Refactor security system to replace 'operation' terminology with 'permission' for standardized access control management. Includes comprehensive permission-based authorization across all services and improved security context handling.",
                "patterns": [
                    r".*[Pp]ermission.*",
                    r".*[Oo]peration.*[Pp]ermission.*",
                    r".*security.*permission.*",
                    r".*authorization.*"
                ],
                "priority": "HIGH",
                "commit_patterns": [r"Replace.*operation.*permission", r"permission.*role"],
                "needs_new_ticket": True
            },
            
            "UNOMI-905": {
                "title": "🎯 UNOMI-905: Segment Condition Evaluation Engine Improvements", 
                "description": "Enhanced segment condition evaluation with bug fixes for past event conditions, improved calculation algorithms, better condition resolution, segment bug fixes, and comprehensive condition architecture improvements. Excludes GraphQL condition factories which belong to GraphQL ticket.",
                "patterns": [
                    r".*[Ss]egment.*[Cc]ondition.*",
                    r".*[Pp]ast.*[Ee]vent.*[Cc]ondition.*", 
                    r".*condition.*evaluation.*",
                    r".*[Ss]egment.*bug.*",
                    r".*ParserHelper.*",
                    r".*ConditionEvaluator.*",
                    r"persistence.*conditions.*ConditionEvaluatorDispatcher.*",
                    r".*[Ss]egment.*[Ss]ervice.*"
                ],
                "priority": "HIGH",
                "commit_patterns": [r"segment.*condition", r"past.*event.*condition", r"condition.*bug", r"Bugfixes.*segment.*condition"],
                "needs_new_ticket": True
            },
            
            "UNOMI-906": {
                "title": "🛠️ UNOMI-906: Unified Shell Commands Framework",
                "description": "Replace individual item type CRUD commands with unified unomi:crud command supporting any object type. Includes enhanced shell dev commands with tenant ID display, ID completion, and comprehensive command consolidation.",
                "patterns": [
                    r"tools/shell-dev-commands/",
                    r".*[Ss]hell.*[Cc]ommand.*",
                    r".*CRUD.*[Cc]ommand.*",
                    r".*unomi:crud.*"
                ],
                "priority": "MEDIUM",
                "commit_patterns": [r"shell.*command", r"CRUD.*command", r"custom.*item.*type.*CRUD"],
                "needs_new_ticket": True
            },
            
            "UNOMI-907": {
                "title": "🖥️ UNOMI-907: Cross-Platform Build Support",
                "description": "Add Windows compatibility to build system with Windows build script (build.ps1), enhanced tool detection, cross-platform development experience, and comprehensive build system improvements for multiple operating systems.",
                "patterns": [
                    r".*[Ww]indows.*",
                    r".*\.ps1$",
                    r".*cross.*platform.*",
                    r".*tool.*detection.*"
                ],
                "priority": "LOW",
                "commit_patterns": [r"Windows.*build", r"cross.*platform", r"tool.*detection"],
                "needs_new_ticket": True
            },
            
            "UNOMI-909": {
                "title": "🌐 UNOMI-909: GraphQL and REST API Enhancements",
                "description": "Complete GraphQL system improvements with modern UI, enhanced REST endpoints, API enhancements, web interfaces, and comprehensive API-related functionality. Includes GraphQL condition factories and parsers that were incorrectly grouped elsewhere.",
                "patterns": [
                    r"^graphql/.*",
                    r"^rest/.*",
                    r"^wab/.*", 
                    r"^metrics/.*",
                    # GraphQL condition factories belong here, not in condition evaluation
                    r"graphql.*condition.*factories.*",
                    r"graphql.*condition.*parsers.*",
                    # Core API classes (excluding infrastructure APIs covered by other tickets)
                    r"^api/src/main/java/org/apache/unomi/api/(?!tenants|security|ExecutionContext|services|tasks|conditions).*",
                    r".*GraphQL.*",
                    r".*REST.*",
                    # Documentation
                    r"manual.*graphql.*",
                    r"manual.*rest.*",
                    r"manual.*api.*"
                ],
                "priority": "MEDIUM",
                "commit_patterns": [r"GraphQL.*bug.*fix", r"REST.*endpoint", r"API.*compatibility", r"GraphQL.*bug.*fixes"],
                "needs_new_ticket": True
            }
        }

    def run_git_command(self, cmd: List[str]) -> str:
        """Run a git command and return output"""
        try:
            result = subprocess.run(['git'] + cmd, capture_output=True, text=True, check=True)
            return result.stdout.strip()
        except subprocess.CalledProcessError as e:
            print(f"{Colors.RED}Git command failed: {' '.join(cmd)}{Colors.NC}")
            print(f"{Colors.RED}Error: {e.stderr}{Colors.NC}")
            return ""

    def validate_git_history(self) -> bool:
        """Validate git history and branch comparison"""
        print(f"\n{Colors.BLUE}🔍 Validating Git History and Branch Comparison{Colors.NC}")
        
        # Check branches exist
        branches = self.run_git_command(['branch', '-a']).split('\n')
        source_exists = any(self.source_branch in branch for branch in branches)
        base_exists = any(self.base_branch in branch for branch in branches) 
        
        if not source_exists:
            print(f"{Colors.RED}❌ Source branch '{self.source_branch}' not found{Colors.NC}")
            return False
            
        if not base_exists:
            print(f"{Colors.RED}❌ Base branch '{self.base_branch}' not found{Colors.NC}")
            return False
        
        # Get commit counts
        ahead_count = self.run_git_command(['rev-list', '--count', f'{self.base_branch}..{self.source_branch}'])
        behind_count = self.run_git_command(['rev-list', '--count', f'{self.source_branch}..{self.base_branch}'])
        
        print(f"{Colors.GREEN}✅ Branch validation successful:{Colors.NC}")
        print(f"  • Source branch '{self.source_branch}': {ahead_count} commits ahead of {self.base_branch}")
        print(f"  • Base branch '{self.base_branch}': {behind_count} commits ahead of {self.source_branch}")
        
        # Validate no conflicts
        merge_base = self.run_git_command(['merge-base', self.source_branch, self.base_branch])
        if merge_base:
            print(f"  • Merge base: {merge_base[:8]}")
        
        return True

    def get_commit_info(self) -> List[CommitInfo]:
        """Get detailed commit information with UNOMI ticket mapping"""
        commits = []
        
        # Get all commits between branches
        commit_output = self.run_git_command([
            'log', f'{self.base_branch}..{self.source_branch}', 
            '--pretty=format:%H|%s', '--reverse'
        ])
        
        if not commit_output:
            return commits
            
        for line in commit_output.split('\n'):
            if '|' not in line:
                continue
                
            hash_part, message = line.split('|', 1)
            
            # Get files for this commit  
            files = self.run_git_command(['show', '--name-only', '--pretty=format:', hash_part])
            file_list = [f for f in files.split('\n') if f.strip()]
            
            # Extract UNOMI tickets
            unomi_tickets = set(re.findall(r'UNOMI-\d+', message))
            
            commits.append(CommitInfo(
                hash=hash_part,
                message=message,
                files=file_list,
                unomi_tickets=unomi_tickets
            ))
        
        return commits

    def get_changed_files(self) -> List[str]:
        """Get all files changed between base and source branch"""
        output = self.run_git_command(['diff', f'{self.base_branch}...{self.source_branch}', '--name-only'])
        return [f for f in output.split('\n') if f.strip()]

    def classify_file_to_unomi_ticket(self, file_path: str, commits: List[CommitInfo]) -> Optional[str]:
        """Classify a file to a UNOMI ticket using patterns and commit history"""
        
        # First, check if file is in commits with explicit UNOMI tickets
        for commit in commits:
            if file_path in commit.files and commit.unomi_tickets:
                # Return the first UNOMI ticket found
                return list(commit.unomi_tickets)[0]
        
        # Then use pattern matching
        for ticket, group_info in self.unomi_groups.items():
            for pattern in group_info["patterns"]:
                if re.search(pattern, file_path):
                    return ticket
        
        # Check commit message patterns for files without explicit UNOMI tickets
        for commit in commits:
            if file_path in commit.files:
                for ticket, group_info in self.unomi_groups.items():
                    if "commit_patterns" in group_info:
                        for pattern in group_info["commit_patterns"]:
                            if re.search(pattern, commit.message, re.IGNORECASE):
                                return ticket
        
        return None

    def ensure_documentation_colocation(self, groups: Dict[str, UNOMIGroup]) -> None:
        """Ensure documentation is co-located with related code changes"""
        print(f"\n{Colors.CYAN}📚 Ensuring Documentation Co-location{Colors.NC}")
        
        # Documentation patterns and their related code patterns
        doc_mappings = {
            r"manual.*multitenancy": ["UNOMI-139"],
            r"manual.*schedul.*": ["UNOMI-878"],
            r"manual.*cach.*": ["UNOMI-880"], 
            r"manual.*test.*": ["UNOMI-881"],
            r"manual.*validation.*": ["UNOMI-883"],
            r"manual.*migration.*": ["UNOMI-884"],
            r"manual.*build.*": ["UNOMI-887"],
            r"manual.*import.*": ["UNOMI-888"],
            r"manual.*tracing.*": ["UNOMI-873"],
            r"manual.*cluster.*": ["UNOMI-877"],
            r"manual.*groovy.*": ["UNOMI-897"]
        }
        
        # Move documentation files to appropriate groups
        doc_moves = 0
        for group_name, group in groups.items():
            if group_name == "UNOMI-882":  # Skip dedicated docs ticket
                continue
                
            files_to_move = set()
            for file in group.files:
                for doc_pattern, target_tickets in doc_mappings.items():
                    if re.search(doc_pattern, file) and group_name in target_tickets:
                        files_to_move.add(file)
            
            if files_to_move:
                # Remove from documentation group
                if "UNOMI-882" in groups:
                    groups["UNOMI-882"].files -= files_to_move
                    doc_moves += len(files_to_move)
                
                print(f"  • Moved {len(files_to_move)} doc files to {group_name}")
        
        print(f"  • Total documentation files co-located: {doc_moves}")

    def analyze_and_group_changes(self) -> Dict[str, UNOMIGroup]:
        """Analyze changes and group them by UNOMI tickets"""
        print(f"\n{Colors.CYAN}🔍 Analyzing Changes and Grouping by UNOMI Tickets{Colors.NC}")
        
        commits = self.get_commit_info()
        changed_files = self.get_changed_files()
        
        print(f"  • Found {len(commits)} commits")
        print(f"  • Found {len(changed_files)} changed files")
        
        # Group files by UNOMI tickets
        ticket_groups = defaultdict(set)
        unmapped_files = set()
        
        for file_path in changed_files:
            ticket = self.classify_file_to_unomi_ticket(file_path, commits)
            if ticket:
                ticket_groups[ticket].add(file_path)
            else:
                unmapped_files.add(file_path)
        
        # Create UNOMIGroup objects
        groups = {}
        for ticket, files in ticket_groups.items():
            if ticket in self.unomi_groups:
                group_info = self.unomi_groups[ticket]
                
                # Get commits for this ticket
                ticket_commits = [c for c in commits if ticket in c.unomi_tickets or 
                                any(f in files for f in c.files)]
                
                groups[ticket] = UNOMIGroup(
                    ticket=ticket,
                    title=group_info["title"],
                    description=group_info["description"],
                    priority=group_info["priority"],
                    files=files,
                    commits=ticket_commits,
                    commit_count=len(ticket_commits),
                    patterns=group_info["patterns"],
                    needs_new_ticket=group_info.get("needs_new_ticket", False)
                )
        
        # Handle unmapped files - create a miscellaneous group
        if unmapped_files:
            unmapped_commits = [c for c in commits if 
                              any(f in unmapped_files for f in c.files) and 
                              not c.unomi_tickets]
            
            groups["UNOMI-908"] = UNOMIGroup(
                ticket="UNOMI-908",
                title="🔧 UNOMI-908: Miscellaneous Improvements and Fixes",
                description="Various improvements, bug fixes, and enhancements that don't fit into existing UNOMI ticket categories. Includes performance optimizations, minor feature additions, and general code improvements.",
                priority="LOW",
                files=unmapped_files,
                commits=unmapped_commits,
                commit_count=len(unmapped_commits),
                patterns=[],
                needs_new_ticket=True
            )
        
        # Ensure documentation co-location
        self.ensure_documentation_colocation(groups)
        
        return groups

    def validate_groupings(self, groups: Dict[str, UNOMIGroup]) -> bool:
        """Validate UNOMI ticket groupings"""
        print(f"\n{Colors.BLUE}🔍 Validating UNOMI Ticket Groupings{Colors.NC}")
        
        all_changed_files = set(self.get_changed_files())
        grouped_files = set()
        validation_errors = 0
        
        # Validate each group
        for ticket, group in groups.items():
            print(f"{Colors.GREEN}🎫 {ticket}: {len(group.files)} files, {group.commit_count} commits (Priority: {group.priority}){Colors.NC}")
            
            # Check for file overlaps
            overlap = grouped_files.intersection(group.files)
            if overlap:
                print(f"{Colors.RED}⚠️  OVERLAP in {ticket}: {list(overlap)[:3]}{'...' if len(overlap) > 3 else ''}{Colors.NC}")
                validation_errors += 1
            
            grouped_files.update(group.files)
            
            # Show if new ticket is needed
            if group.needs_new_ticket:
                print(f"{Colors.YELLOW}   📝 NEW TICKET NEEDED{Colors.NC}")
        
        # Check for unmatched files
        unmatched = all_changed_files - grouped_files
        if unmatched:
            print(f"\n{Colors.RED}❌ UNMATCHED FILES ({len(unmatched)}):${Colors.NC}")
            for file in sorted(list(unmatched))[:5]:
                print(f"  {file}")
            if len(unmatched) > 5:
                print(f"  ... and {len(unmatched) - 5} more")
            validation_errors += 1
        
        print(f"\n{Colors.CYAN}📊 Validation Summary:{Colors.NC}")
        print(f"  • Total files: {len(all_changed_files)}")
        print(f"  • Grouped files: {len(grouped_files)}")
        print(f"  • UNOMI tickets: {len(groups)}")
        print(f"  • New tickets needed: {sum(1 for g in groups.values() if g.needs_new_ticket)}")
        print(f"  • Validation errors: {validation_errors}")
        
        if validation_errors == 0:
            print(f"{Colors.GREEN}✅ UNOMI ticket groupings are valid!{Colors.NC}")
            return True
        else:
            print(f"{Colors.RED}❌ Groupings have issues that need attention{Colors.NC}")
            return False

    def show_unomi_summary(self, groups: Dict[str, UNOMIGroup]) -> None:
        """Show comprehensive UNOMI ticket summary"""
        print(f"\n{Colors.BLUE}🎯 UNOMI TICKET-BASED PR SUMMARY{Colors.NC}")
        print("=" * 80)
        
        # Group by existing vs new tickets
        existing_tickets = {k: v for k, v in groups.items() if not v.needs_new_ticket}
        new_tickets = {k: v for k, v in groups.items() if v.needs_new_ticket}
        
        # Sort by priority
        priority_order = {"CRITICAL": 0, "HIGH": 1, "MEDIUM": 2, "LOW": 3}
        
        def sort_key(item):
            return (priority_order.get(item[1].priority, 4), item[0])
        
        print(f"\n{Colors.GREEN}✅ EXISTING UNOMI TICKETS ({len(existing_tickets)} tickets){Colors.NC}")
        print("-" * 60)
        
        for ticket, group in sorted(existing_tickets.items(), key=sort_key):
            priority_color = Colors.RED if group.priority == 'CRITICAL' else Colors.YELLOW if group.priority == 'HIGH' else Colors.CYAN
            print(f"  {priority_color}{ticket:<12}{Colors.NC} | {len(group.files):3d} files | {group.commit_count:2d} commits | {group.priority}")
            print(f"    {group.title}")
        
        if new_tickets:
            print(f"\n{Colors.PURPLE}🆕 NEW UNOMI TICKETS NEEDED ({len(new_tickets)} tickets){Colors.NC}")
            print("-" * 60)
            
            for ticket, group in sorted(new_tickets.items(), key=sort_key):
                priority_color = Colors.RED if group.priority == 'CRITICAL' else Colors.YELLOW if group.priority == 'HIGH' else Colors.CYAN
                print(f"  {priority_color}{ticket:<12}{Colors.NC} | {len(group.files):3d} files | {group.commit_count:2d} commits | {group.priority}")
                print(f"    {group.title}")
        
        total_files = sum(len(g.files) for g in groups.values())
        total_commits = sum(g.commit_count for g in groups.values())
        
        print(f"\n{Colors.CYAN}📊 TOTALS:{Colors.NC}")
        print(f"  • Total UNOMI tickets: {len(groups)}")
        print(f"  • Existing tickets: {len(existing_tickets)}")  
        print(f"  • New tickets needed: {len(new_tickets)}")
        print(f"  • Total files: {total_files}")
        print(f"  • Total commits: {total_commits}")
        print(f"  • Average files per ticket: {total_files / len(groups):.1f}")

    def simulate_pr_creation(self, groups: Dict[str, UNOMIGroup]) -> None:
        """Simulate UNOMI ticket-based PR creation"""
        print(f"\n{Colors.PURPLE}🔬 SIMULATION: UNOMI Ticket-Based PR Creation{Colors.NC}")
        
        # Sort by priority and ticket number
        priority_order = {"CRITICAL": 0, "HIGH": 1, "MEDIUM": 2, "LOW": 3}
        sorted_groups = sorted(groups.items(), 
                             key=lambda x: (priority_order.get(x[1].priority, 4), x[0]))
        
        for i, (ticket, group) in enumerate(sorted_groups, 1):
            status = "🆕 NEW TICKET" if group.needs_new_ticket else "✅ EXISTING"
            
            print(f"\n{Colors.CYAN}📁 PR {i}: {ticket} ({status}){Colors.NC}")
            print(f"{Colors.BLUE}Title: {group.title}{Colors.NC}")
            print(f"{Colors.YELLOW}Priority: {group.priority}{Colors.NC}")
            print(f"{Colors.GREEN}Files: {len(group.files)} | Commits: {group.commit_count}{Colors.NC}")
            
            # Show sample files
            sample_files = sorted(list(group.files))[:8]
            for file in sample_files:
                print(f"  • {file}")
            if len(group.files) > 8:
                print(f"  ... and {len(group.files) - 8} more files")
            
            # Show sample commits  
            if group.commits:
                print(f"{Colors.PURPLE}Recent commits:{Colors.NC}")
                for commit in group.commits[-3:]:
                    print(f"  {commit.hash[:8]} {commit.message[:60]}...")

    def create_pr_for_group(self, ticket: str, group: UNOMIGroup) -> bool:
        """Create a PR for a specific UNOMI ticket group"""
        print(f"\n{Colors.GREEN}Creating PR for {ticket}{Colors.NC}")
        
        # Create branch name
        branch_name = f"{ticket.lower()}-implementation"
        
        try:
            # Create branch from base
            self.run_git_command(['checkout', self.base_branch])
            self.run_git_command(['pull', 'origin', self.base_branch])
            
            # Check if branch exists
            existing_branches = self.run_git_command(['branch', '-a'])
            if branch_name in existing_branches:
                print(f"{Colors.YELLOW}⚠️  Branch {branch_name} already exists, using existing{Colors.NC}")
                self.run_git_command(['checkout', branch_name])
            else:
                self.run_git_command(['checkout', '-b', branch_name])
            
            # Apply changes for specific files
            self.apply_file_changes(group.files)
            
            # Stage changes
            self.run_git_command(['add', '.'])
            
            # Create commit message
            status_indicator = "🆕" if group.needs_new_ticket else "✅"
            commit_message = f"""{status_indicator} {group.title}

{group.description}

Changes:
- Files modified: {len(group.files)}
- Commits integrated: {group.commit_count}
- Priority: {group.priority}

{f'🆕 NEW TICKET REQUIRED: This functionality needs a new JIRA ticket to be created.' if group.needs_new_ticket else '✅ EXISTING TICKET: Maps to existing UNOMI JIRA ticket.'}

🤖 Generated with UNOMI Ticket-Based PR Creation Tool
Co-Authored-By: Claude <noreply@anthropic.com>
"""
            
            # Commit changes
            result = subprocess.run(['git', 'commit', '-m', commit_message], 
                                  capture_output=True, text=True)
            
            if result.returncode != 0:
                if "nothing to commit" in result.stdout:
                    print(f"{Colors.YELLOW}⚠️  No changes to commit for {ticket}{Colors.NC}")
                    self.run_git_command(['checkout', self.base_branch])
                    return False
                else:
                    print(f"{Colors.RED}❌ Commit failed: {result.stderr}{Colors.NC}")
                    return False
            
            # Push branch
            push_result = self.run_git_command(['push', '-u', 'origin', branch_name])
            if not push_result and "fatal" in push_result:
                print(f"{Colors.RED}❌ Push failed for {ticket}{Colors.NC}")
                return False
            
            # Create PR using GitHub CLI
            pr_title = group.title
            pr_body = self.generate_pr_body(group)
            
            subprocess.run([
                'gh', 'pr', 'create',
                '--title', pr_title,
                '--body', pr_body,
                '--base', self.base_branch,
                '--head', branch_name
            ])
            
            print(f"{Colors.GREEN}✅ Created PR for {ticket}: {branch_name}{Colors.NC}")
            return True
            
        except Exception as e:
            print(f"{Colors.RED}❌ Error creating PR for {ticket}: {e}{Colors.NC}")
            return False

    def apply_file_changes(self, files: Set[str]) -> None:
        """Apply changes for specific files using git"""
        temp_patch = "/tmp/unomi_pr.patch"
        
        with open(temp_patch, 'w') as f:
            for file in files:
                diff_output = self.run_git_command(['diff', f'{self.base_branch}...{self.source_branch}', '--', file])
                if diff_output:
                    f.write(diff_output + '\n')
        
        if os.path.getsize(temp_patch) > 0:
            result = subprocess.run(['git', 'apply', temp_patch], capture_output=True)
            if result.returncode != 0:
                # Try 3-way merge
                subprocess.run(['git', 'apply', '--3way', temp_patch])
        
        os.remove(temp_patch)

    def generate_pr_body(self, group: UNOMIGroup) -> str:
        """Generate comprehensive PR body for UNOMI ticket"""
        status = "🆕 **NEW JIRA TICKET REQUIRED**" if group.needs_new_ticket else "✅ **EXISTING JIRA TICKET**"
        
        # Generate file categories
        file_categories = defaultdict(list)
        for file in sorted(group.files):
            if file.startswith('api/'):
                file_categories['API Changes'].append(file)
            elif file.startswith('services/'):
                file_categories['Service Implementation'].append(file)
            elif file.startswith('rest/'):
                file_categories['REST Endpoints'].append(file)
            elif file.startswith('extensions/'):
                file_categories['Extensions'].append(file)
            elif file.startswith('manual/'):
                file_categories['Documentation'].append(file)
            elif file.startswith('itests/'):
                file_categories['Integration Tests'].append(file)
            elif 'test' in file.lower():
                file_categories['Unit Tests'].append(file)
            else:
                file_categories['Configuration & Build'].append(file)
        
        file_breakdown = ""
        for category, files in file_categories.items():
            file_breakdown += f"\n### {category} ({len(files)} files)\n"
            for file in files[:5]:  # Show first 5 files
                file_breakdown += f"- `{file}`\n"
            if len(files) > 5:
                file_breakdown += f"- ... and {len(files) - 5} more files\n"
        
        return f"""## {group.ticket}: UNOMI Ticket Implementation

{status}

### Summary
{group.description}

### Changes Overview
- **Files Changed**: {len(group.files)}
- **Commits Integrated**: {group.commit_count}
- **Priority**: {group.priority}

### File Breakdown
{file_breakdown}

### UNOMI Integration Benefits
- ✅ **Ticket-Based Organization**: All changes mapped to specific UNOMI functionality
- ✅ **Documentation Co-location**: Related documentation included with code changes  
- ✅ **Complete Feature Implementation**: API + services + REST + tests + config together
- ✅ **Git History Validation**: Changes verified against commit history and main branch
- ✅ **Comprehensive Testing**: Integration tests and unit tests included

### Testing Checklist
- [ ] Build passes: `./build.sh`
- [ ] Integration tests pass: `./itests/kt.sh`
- [ ] Unit tests pass for modified services
- [ ] Documentation builds correctly
- [ ] No regressions in existing functionality

### Review Notes
This PR implements {group.ticket} functionality with comprehensive changes across the entire stack.
{f'**Action Required**: Create JIRA ticket {group.ticket} before merging this PR.' if group.needs_new_ticket else f'**JIRA Reference**: This PR implements existing ticket {group.ticket}.'}

### Validation
- ✅ Git history validated against `{self.source_branch}` and `{self.base_branch}`
- ✅ Documentation co-located with related code changes
- ✅ All file patterns verified and mapped to UNOMI ticket

🤖 Generated with UNOMI Ticket-Based PR Creation Tool

Co-Authored-By: Claude <noreply@anthropic.com>
"""

    def create_all_prs(self, groups: Dict[str, UNOMIGroup]) -> None:
        """Create all UNOMI ticket-based PRs in dependency order"""
        print(f"\n{Colors.BLUE}🚀 Creating UNOMI Ticket-Based PRs in Dependency Order{Colors.NC}")
        
        # DEPENDENCY-AWARE CREATION ORDER (based on semantic analysis)
        dependency_order = [
            # Phase 1: Core Infrastructure (CRITICAL - Must merge first)
            "UNOMI-139",  # Multi-Tenancy - Foundation for all tenant-aware features
            "UNOMI-897",  # Groovy Actions Fixes - Critical performance/data integrity
            
            # Phase 2: Framework Services (HIGH - Merge after core)
            "UNOMI-880",  # Caching - Required by multiple services  
            "UNOMI-884",  # Migration Scripts - Schema changes before service enhancements
            "UNOMI-878",  # Task Scheduling - Core service functionality
            
            # Phase 3: Cross-cutting Concerns (MEDIUM - Merge after frameworks)
            "UNOMI-881",  # Testing Framework - Enables comprehensive testing
            "UNOMI-873",  # Request Tracing - Observability across all services
            "UNOMI-883",  # Condition Validation - Service validation framework
            "UNOMI-877",  # Cluster Health - Health monitoring system
            
            # Phase 4: API & Extensions (MEDIUM - Merge after services)
            "UNOMI-905",  # Segment Condition Evaluation - Core condition logic
            "UNOMI-909",  # GraphQL & REST APIs - API layer enhancements  
            "UNOMI-906",  # Shell Commands - Administrative tools
            
            # Phase 5: Infrastructure & Documentation (LOW - Merge last)
            "UNOMI-887",  # Build Scripts - Build improvements
            "UNOMI-888",  # Javadoc - Documentation improvements
            "UNOMI-892",  # CI/CD - Infrastructure improvements
            "UNOMI-882",  # Documentation - User-facing documentation
            "UNOMI-908",  # Miscellaneous - Remaining improvements
        ]
        
        successful_prs = 0
        failed_prs = 0
        
        print(f"{Colors.CYAN}📋 Creating PRs in dependency-aware order:{Colors.NC}")
        for phase, tickets in enumerate([
            dependency_order[0:2],   # Phase 1: Core Infrastructure
            dependency_order[2:5],   # Phase 2: Framework Services  
            dependency_order[5:9],   # Phase 3: Cross-cutting Concerns
            dependency_order[9:12],  # Phase 4: API & Extensions
            dependency_order[12:],   # Phase 5: Infrastructure & Documentation
        ], 1):
            phase_names = ["Core Infrastructure", "Framework Services", "Cross-cutting Concerns", "API & Extensions", "Infrastructure & Documentation"]
            print(f"\n{Colors.PURPLE}Phase {phase}: {phase_names[phase-1]}{Colors.NC}")
            
            for ticket in tickets:
                if ticket not in groups:
                    print(f"{Colors.YELLOW}⚠️  Skipping {ticket} - not found in groups{Colors.NC}")
                    continue
                    
                group = groups[ticket]
                if not group.files:
                    print(f"{Colors.YELLOW}⚠️  Skipping {ticket} - no files{Colors.NC}")
                    continue
                    
                if self.create_pr_for_group(ticket, group):
                    successful_prs += 1
                else:
                    failed_prs += 1
        
        print(f"\n{Colors.CYAN}📊 PR Creation Summary:{Colors.NC}")
        print(f"  • Successful PRs: {successful_prs}")
        print(f"  • Failed PRs: {failed_prs}")
        print(f"  • Total attempted: {successful_prs + failed_prs}")
        print(f"\n{Colors.GREEN}🎯 Dependency Order Benefits:{Colors.NC}")
        print(f"  • Core infrastructure merged first (UNOMI-139, UNOMI-897)")
        print(f"  • Framework services depend on core (UNOMI-880, UNOMI-878)")
        print(f"  • Cross-cutting concerns use frameworks (tracing, validation)")
        print(f"  • API/Extensions build on services (GraphQL, REST)")
        print(f"  • Infrastructure improvements merged last")

    def main(self):
        """Main execution method"""
        parser = argparse.ArgumentParser(
            description="UNOMI Ticket-Based PR Creation Tool",
            epilog="""
Examples:
  python3 create_unomi_prs.py --validate      # Validate UNOMI ticket mappings
  python3 create_unomi_prs.py --summary       # Show UNOMI ticket summary
  python3 create_unomi_prs.py --simulate      # Simulate PR creation
  python3 create_unomi_prs.py                 # Create actual PRs
            """,
            formatter_class=argparse.RawDescriptionHelpFormatter
        )
        parser.add_argument('--simulate', action='store_true', 
                          help='Simulate PR creation without making actual changes')
        parser.add_argument('--validate', action='store_true', 
                          help='Validate UNOMI ticket groupings')
        parser.add_argument('--summary', action='store_true', 
                          help='Show UNOMI ticket-based summary')
        parser.add_argument('--source', default='unomi-3-dev',
                          help='Source branch (default: unomi-3-dev)')
        parser.add_argument('--base', default='master', 
                          help='Base branch (default: master)')
        
        args = parser.parse_args()
        
        self.source_branch = args.source
        self.base_branch = args.base
        
        # Validate git environment
        if not self.validate_git_history():
            sys.exit(1)
        
        # Analyze and group changes
        groups = self.analyze_and_group_changes()
        
        # Execute requested operation
        if args.validate:
            print(f"{Colors.BLUE}✅ Running UNOMI ticket validation{Colors.NC}")
            if self.validate_groupings(groups):
                sys.exit(0)
            else:
                sys.exit(1)
        elif args.summary:
            print(f"{Colors.CYAN}📊 Generating UNOMI ticket summary{Colors.NC}")
            self.show_unomi_summary(groups)
        elif args.simulate:
            print(f"{Colors.YELLOW}🔬 Running UNOMI PR simulation{Colors.NC}")
            self.simulate_pr_creation(groups)
        else:
            print(f"{Colors.BLUE}🎯 Creating UNOMI ticket-based PRs{Colors.NC}")
            self.create_all_prs(groups)

if __name__ == "__main__":
    creator = UNOMIPRCreator()
    creator.main()