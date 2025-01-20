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
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.karaf.shell.support.table.ShellTable;
import org.apache.unomi.api.tenants.Tenant;
import org.apache.unomi.api.tenants.TenantService;

import java.util.List;

@Command(scope = "unomi", name = "tenant-list", description = "List all tenants")
@Service
public class TenantListCommand implements Action {

    @Reference
    private TenantService tenantService;

    @Option(name = "--csv", description = "Output table in CSV format", required = false, multiValued = false)
    boolean csv;

    @Override
    public Object execute() throws Exception {
        List<Tenant> tenants = tenantService.getAllTenants();

        if (csv) {
            System.out.println("ID,Name,Description,Status");
            for (Tenant tenant : tenants) {
                System.out.printf("%s,%s,%s,%s%n",
                    tenant.getItemId(),
                    tenant.getName(),
                    tenant.getDescription(),
                    tenant.getStatus());
            }
            return null;
        }

        ShellTable table = new ShellTable();
        table.column("ID");
        table.column("Name");
        table.column("Description");
        table.column("Status");

        for (Tenant tenant : tenants) {
            table.addRow().addContent(
                tenant.getItemId(),
                tenant.getName(),
                tenant.getDescription(),
                tenant.getStatus());
        }

        table.print(System.out);
        return null;
    }
}
