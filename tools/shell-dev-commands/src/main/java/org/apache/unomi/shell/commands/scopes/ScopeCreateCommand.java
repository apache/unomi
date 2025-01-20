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
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.Scope;
import org.apache.unomi.shell.commands.BaseCommand;

/**
 * Creates a new scope
 */
@Command(scope = "unomi", name = "scope-create", description = "Creates a new scope")
@Service
public class ScopeCreateCommand extends BaseCommand {

    @Argument(index = 0, name = "scopeId", description = "The identifier of the scope", required = true)
    String scopeId;

    @Argument(index = 1, name = "name", description = "The name of the scope", required = true)
    String name;

    @Option(name = "--description", description = "The description of the scope", required = false)
    String description;

    @Option(name = "--disabled", description = "Create the scope in disabled state", required = false)
    boolean disabled = false;

    @Override
    public Object execute() throws Exception {
        Scope scope = new Scope();
        scope.setItemId(scopeId);

        Metadata metadata = new Metadata();
        metadata.setId(scopeId);
        metadata.setName(name);
        metadata.setDescription(description);
        metadata.setEnabled(!disabled);
        scope.setMetadata(metadata);

        persistenceService.save(scope);
        System.out.println("Scope '" + scopeId + "' created successfully.");
        return null;
    }
} 