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
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.karaf.shell.support.table.ShellTable;
import org.apache.unomi.api.Scope;
import org.apache.unomi.shell.commands.BaseCommand;
import org.apache.unomi.shell.completers.ScopeCompleter;

/**
 * Shows details about a specific scope
 */
@Command(scope = "unomi", name = "scope-show", description = "Shows details about a specific scope")
@Service
public class ScopeShowCommand extends BaseCommand {

    @Argument(index = 0, name = "scopeId", description = "The identifier of the scope", required = true)
    @Completion(ScopeCompleter.class)
    String scopeId;

    @Override
    public Object execute() throws Exception {
        Scope scope = persistenceService.load(scopeId, Scope.class);
        if (scope == null) {
            System.err.println("Scope with id '" + scopeId + "' not found.");
            return null;
        }

        ShellTable table = new ShellTable();
        table.column("Property");
        table.column("Value");

        table.addRow().addContent("ID", scope.getItemId());
        table.addRow().addContent("Name", scope.getMetadata().getName());
        table.addRow().addContent("Description", scope.getMetadata().getDescription());
        table.addRow().addContent("Enabled", scope.getMetadata().isEnabled());

        table.print(System.out);
        return null;
    }
} 