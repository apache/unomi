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
package org.apache.unomi.shell.commands.schema;

import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.unomi.api.tenants.TenantService;
import org.apache.unomi.schema.api.SchemaService;

import java.nio.file.Files;
import java.nio.file.Paths;

@Service
@Command(scope = "unomi", name = "schema-add", description = "Add a new JSON schema")
public class AddSchemaCommand implements Action {

    @Reference
    private SchemaService schemaService;

    @Reference
    private TenantService tenantService;

    @Argument(index = 0, name = "file", description = "Path to the JSON schema file", required = true)
    String schemaFile;

    @Override
    public Object execute() throws Exception {
        String schema = new String(Files.readAllBytes(Paths.get(schemaFile)));
        try {
            schemaService.saveSchema(schema);
            System.out.println("Schema successfully added.");
        } catch (Exception e) {
            System.err.println("Error adding schema: " + e.getMessage());
        }
        return null;
    }
}
