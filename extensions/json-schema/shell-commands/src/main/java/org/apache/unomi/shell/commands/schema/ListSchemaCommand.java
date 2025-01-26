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
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.unomi.api.tenants.TenantService;
import org.apache.unomi.schema.api.JsonSchemaWrapper;
import org.apache.unomi.schema.api.SchemaService;

import java.util.List;
import java.util.Set;

@Service
@Command(scope = "unomi", name = "schema-list", description = "List all available JSON schemas")
public class ListSchemaCommand implements Action {

    @Reference
    private SchemaService schemaService;

    @Reference
    private TenantService tenantService;

    @Option(name = "--target", description = "Filter schemas by target", required = false)
    String target;

    @Override
    public Object execute() throws Exception {
        if (target != null) {
            List<JsonSchemaWrapper> schemas = schemaService.getSchemasByTarget(target);
            System.out.println("Schemas for target '" + target + "':");
            schemas.forEach(schema -> {
                System.out.println("ID: " + schema.getItemId());
                System.out.println("  Name: " + schema.getName());
                System.out.println("  Target: " + schema.getTarget());
                if (schema.getExtendsSchemaId() != null) {
                    System.out.println("  Extends: " + schema.getExtendsSchemaId());
                }
                System.out.println("  Tenant: " + schema.getTenantId());
                System.out.println();
            });
        } else {
            Set<String> schemaIds = schemaService.getInstalledJsonSchemaIds();
            System.out.println("All installed schemas:");
            schemaIds.forEach(id -> {
                JsonSchemaWrapper schema = schemaService.getSchema(id);
                System.out.println("ID: " + id);
                System.out.println("  Name: " + schema.getName());
                System.out.println("  Target: " + schema.getTarget());
                if (schema.getExtendsSchemaId() != null) {
                    System.out.println("  Extends: " + schema.getExtendsSchemaId());
                }
                System.out.println("  Tenant: " + schema.getTenantId());
                System.out.println();
            });
        }
        return null;
    }
}
