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

import org.apache.karaf.shell.api.action.*;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.unomi.api.tenants.Tenant;
import org.apache.unomi.api.tenants.TenantService;
import org.apache.unomi.shell.completers.TenantCompleter;

@Command(scope = "tenant", name = "delete", description = "Delete a tenant")
@Service
public class TenantDeleteCommand implements Action {

    @Reference
    private TenantService tenantService;

    @Argument(index = 0, name = "tenantId", description = "Tenant ID to delete", required = true)
    @Completion(TenantCompleter.class)
    String tenantId;

    @Option(name = "--force", description = "Force deletion without confirmation", required = false)
    boolean force = false;

    @Override
    public Object execute() throws Exception {
        Tenant tenant = tenantService.getTenant(tenantId);
        if (tenant == null) {
            System.err.println("Tenant not found.");
            return null;
        }

        if (!force) {
            System.out.println("Are you sure you want to delete tenant '" + tenantId + "'? This action cannot be undone. [y/N]");
            int c = System.in.read();
            if (c != 'y' && c != 'Y') {
                System.out.println("Deletion cancelled.");
                return null;
            }
        }

        tenantService.deleteTenant(tenantId);
        System.out.println("Tenant '" + tenantId + "' deleted successfully.");
        return null;
    }
}
