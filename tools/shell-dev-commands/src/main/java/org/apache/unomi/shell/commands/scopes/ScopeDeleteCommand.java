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
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.karaf.shell.api.console.Session;
import org.apache.unomi.api.Scope;
import org.apache.unomi.shell.commands.BaseCommand;
import org.apache.unomi.shell.completers.ScopeCompleter;

/**
 * Deletes a scope
 */
@Command(scope = "unomi", name = "scope-delete", description = "Deletes a scope")
@Service
public class ScopeDeleteCommand extends BaseCommand {

    @Reference
    Session session;

    @Argument(index = 0, name = "scopeId", description = "The identifier of the scope", required = true)
    @Completion(ScopeCompleter.class)
    String scopeId;

    @Option(name = "--force", description = "Skip confirmation", required = false)
    boolean force = false;

    @Override
    public Object execute() throws Exception {
        Scope scope = persistenceService.load(scopeId, Scope.class);
        if (scope == null) {
            System.err.println("Scope with id '" + scopeId + "' not found.");
            return null;
        }

        if (!force && !confirm(session, "Are you sure you want to delete scope '" + scopeId + "'?")) {
            System.out.println("Scope deletion cancelled");
            return null;
        }

        persistenceService.remove(scopeId, Scope.class);
        System.out.println("Scope '" + scopeId + "' deleted successfully.");
        return null;
    }
} 