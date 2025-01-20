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
package org.apache.unomi.shell.commands.tenants;

import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Completion;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.unomi.api.tenants.ApiKey;
import org.apache.unomi.api.tenants.Tenant;
import org.apache.unomi.api.tenants.TenantService;
import org.apache.unomi.shell.completers.TenantCompleter;

@Command(scope = "unomi", name = "tenant-show", description = "Show tenant details")
@Service
public class TenantShowCommand implements Action {

    @Reference
    private TenantService tenantService;

    @Argument(index = 0, name = "tenantId", description = "Tenant ID", required = true)
    @Completion(TenantCompleter.class)
    String tenantId;

    @Override
    public Object execute() throws Exception {
        Tenant tenant = tenantService.getTenant(tenantId);
        if (tenant == null) {
            System.err.println("Tenant not found.");
            return null;
        }

        System.out.println("Tenant Details:");
        System.out.println("ID: " + tenant.getItemId());
        System.out.println("Name: " + tenant.getName());
        System.out.println("Description: " + tenant.getDescription());
        System.out.println("Status: " + tenant.getStatus());
        System.out.println("Creation Date: " + tenant.getCreationDate());
        System.out.println("Last Modified: " + tenant.getLastModificationDate());

        // Show API keys
        ApiKey publicKey = tenantService.getApiKey(tenant.getItemId(), ApiKey.ApiKeyType.PUBLIC);
        ApiKey privateKey = tenantService.getApiKey(tenant.getItemId(), ApiKey.ApiKeyType.PRIVATE);

        if (publicKey != null) {
            System.out.println("Public API Key: " + publicKey.getKey());
        }
        if (privateKey != null) {
            System.out.println("Private API Key: " + privateKey.getKey());
        }

        return null;
    }
}
