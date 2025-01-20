/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.unomi.shell.commands.scopes;

import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Completion;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.unomi.api.Scope;
import org.apache.unomi.shell.commands.BaseCommand;
import org.apache.unomi.shell.completers.ScopeCompleter;

/**
 * Updates an existing scope
 */
@Command(scope = "unomi", name = "scope-update", description = "Updates an existing scope")
@Service
public class ScopeUpdateCommand extends BaseCommand {

    @Argument(index = 0, name = "scopeId", description = "The identifier of the scope", required = true)
    @Completion(ScopeCompleter.class)
    String scopeId;

    @Option(name = "--name", description = "The name of the scope", required = false)
    String name;

    @Option(name = "--description", description = "The description of the scope", required = false)
    String description;

    @Option(name = "--enabled", description = "Enable the scope", required = false)
    boolean enabled = false;

    @Option(name = "--disabled", description = "Disable the scope", required = false)
    boolean disabled = false;

    @Override
    public Object execute() throws Exception {
        Scope scope = persistenceService.load(scopeId, Scope.class);
        if (scope == null) {
            System.err.println("Scope with id '" + scopeId + "' not found.");
            return null;
        }

        if (enabled && disabled) {
            System.err.println("Cannot specify both --enabled and --disabled options.");
            return null;
        }

        boolean modified = false;

        if (name != null) {
            scope.getMetadata().setName(name);
            modified = true;
        }

        if (description != null) {
            scope.getMetadata().setDescription(description);
            modified = true;
        }

        if (enabled || disabled) {
            scope.getMetadata().setEnabled(enabled);
            modified = true;
        }

        if (!modified) {
            System.out.println("No changes specified. Scope not updated.");
            return null;
        }

        persistenceService.save(scope);
        System.out.println("Scope '" + scopeId + "' updated successfully.");
        return null;
    }
} 