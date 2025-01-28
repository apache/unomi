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
package org.apache.unomi.shell.dev.commands.tenants;

import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Completion;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.unomi.api.tenants.Tenant;
import org.apache.unomi.api.tenants.TenantService;
import org.apache.unomi.shell.dev.completers.TenantCompleter;

@Command(scope = "unomi", name = "tenant-set-current", description = "Set the current tenant ID for this shell session")
@Service
public class TenantSetCurrentCommand implements Action {

    @Reference
    private TenantService tenantService;

    @Argument(index = 0, name = "tenantId", description = "Tenant ID to set as current", required = true)
    @Completion(TenantCompleter.class)
    String tenantId;

    @Override
    public Object execute() throws Exception {
        // Verify the tenant exists
        Tenant tenant = tenantService.getTenant(tenantId);
        if (tenant == null && !"system".equals(tenantId)) {
            System.err.println("Error: Tenant '" + tenantId + "' not found");
            return null;
        }

        // Set the current tenant
        tenantService.setCurrentTenant(tenantId);
        System.out.println("Current tenant set to: " + tenantId);

        if (tenant == null) {
            // This happens in the case of the system tenant being used.
            return null;
        }
        // Show additional tenant details
        System.out.println("Tenant details:");
        System.out.println("  Name: " + tenant.getName());
        System.out.println("  Status: " + tenant.getStatus());
        return null;
    }
}
