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
package org.apache.unomi.services.impl.tenants;

import org.apache.unomi.api.tenants.Tenant;
import org.apache.unomi.api.tenants.TenantService;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

public class TenantMigrationService {

    private static final Logger logger = LoggerFactory.getLogger(TenantMigrationService.class);

    private PersistenceService persistenceService;
    private TenantService tenantService;

    public void setPersistenceService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    public void setTenantService(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    public boolean migrateTenant(String sourceTenantId, String targetTenantId) {
        try {
            // Verify tenants exist
            Tenant sourceTenant = tenantService.getTenant(sourceTenantId);
            if (sourceTenant == null) {
                logger.error("Source tenant {} not found", sourceTenantId);
                return false;
            }

            Tenant targetTenant = tenantService.getTenant(targetTenantId);
            if (targetTenant == null) {
                logger.error("Target tenant {} not found", targetTenantId);
                return false;
            }

            // Define item types to migrate
            List<String> itemTypes = Arrays.asList("profile", "event", "session");

            // Perform migration using persistence service
            return persistenceService.migrateTenantData(sourceTenantId, targetTenantId, itemTypes);
        } catch (Exception e) {
            logger.error("Error during tenant migration from {} to {}", sourceTenantId, targetTenantId, e);
            return false;
        }
    }
}
