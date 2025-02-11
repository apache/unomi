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
package org.apache.unomi.services;

import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.actions.ActionType;
import org.apache.unomi.api.security.SecurityServiceConfiguration;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.api.services.ExecutionContextManager;
import org.apache.unomi.api.services.SchedulerService;
import org.apache.unomi.api.services.cache.MultiTypeCacheService;
import org.apache.unomi.api.tenants.AuditService;
import org.apache.unomi.api.tenants.TenantService;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.apache.unomi.services.impl.ExecutionContextManagerImpl;
import org.apache.unomi.services.impl.KarafSecurityService;
import org.apache.unomi.services.impl.definitions.DefinitionsServiceImpl;
import org.apache.unomi.services.impl.events.EventServiceImpl;
import org.apache.unomi.services.impl.rules.RulesServiceImpl;
import org.apache.unomi.services.impl.rules.TestActionExecutorDispatcher;
import org.apache.unomi.services.impl.tenants.AuditServiceImpl;
import org.osgi.framework.BundleContext;

public class TestHelper {

    public static KarafSecurityService createSecurityService() {
        KarafSecurityService securityService = new KarafSecurityService();
        AuditService auditService = new AuditServiceImpl();
        securityService.setTenantAuditService(auditService);
        securityService.setConfiguration(new SecurityServiceConfiguration());
        securityService.init();
        return securityService;
    }

    public static ExecutionContextManagerImpl createExecutionContextManager(KarafSecurityService securityService) {
        ExecutionContextManagerImpl executionContextManager = new ExecutionContextManagerImpl();
        executionContextManager.setSecurityService(securityService);
        return executionContextManager;
    }

    public static DefinitionsServiceImpl createDefinitionService(
        PersistenceService persistenceService,
        BundleContext bundleContext,
        SchedulerService schedulerService,
        MultiTypeCacheService multiTypeCacheService,
        ExecutionContextManager executionContextManager,
        TenantService tenantService
    ) {
        DefinitionsServiceImpl definitionsService = new DefinitionsServiceImpl();
        definitionsService.setPersistenceService(persistenceService);
        definitionsService.setBundleContext(bundleContext);
        definitionsService.setSchedulerService(schedulerService);
        definitionsService.setCacheService(multiTypeCacheService);
        definitionsService.setContextManager(executionContextManager);
        definitionsService.setTenantService(tenantService);
        definitionsService.postConstruct();
        return definitionsService;
    }

    public static RulesServiceImpl createRulesService(
        PersistenceService persistenceService,
        BundleContext bundleContext,
        SchedulerService schedulerService,
        DefinitionsServiceImpl definitionsService,
        EventServiceImpl eventService,
        ExecutionContextManager executionContextManager,
        TenantService tenantService
    ) {
        RulesServiceImpl rulesService = new RulesServiceImpl();
        TestActionExecutorDispatcher actionExecutorDispatcher = new TestActionExecutorDispatcher(definitionsService, persistenceService);
        actionExecutorDispatcher.setDefaultReturnValue(EventService.PROFILE_UPDATED);

        rulesService.setBundleContext(bundleContext);
        rulesService.setPersistenceService(persistenceService);
        rulesService.setDefinitionsService(definitionsService);
        rulesService.setEventService(eventService);
        rulesService.setActionExecutorDispatcher(actionExecutorDispatcher);
        rulesService.setTenantService(tenantService);
        rulesService.setSchedulerService(schedulerService);
        rulesService.setContextManager(executionContextManager);

        // Create and register test action type
        ActionType testActionType = new ActionType();
        testActionType.setItemId("test");
        Metadata actionMetadata = new Metadata();
        actionMetadata.setId("test");
        actionMetadata.setEnabled(true);
        testActionType.setMetadata(actionMetadata);
        testActionType.setActionExecutor("test");
        definitionsService.setActionType(testActionType);

        // Create and register setEventOccurenceCountAction type
        ActionType setEventOccurenceCountActionType = new ActionType();
        setEventOccurenceCountActionType.setItemId("setEventOccurenceCountAction");
        Metadata setEventOccurenceCountMetadata = new Metadata();
        setEventOccurenceCountMetadata.setId("setEventOccurenceCountAction");
        setEventOccurenceCountMetadata.setEnabled(true);
        setEventOccurenceCountActionType.setMetadata(setEventOccurenceCountMetadata);
        setEventOccurenceCountActionType.setActionExecutor("setEventOccurenceCountAction");
        definitionsService.setActionType(setEventOccurenceCountActionType);

        // Initialize rule caches
        rulesService.postConstruct();
        eventService.addEventListenerService(rulesService);

        return rulesService;
    }

    public static SchedulerService createSchedulerService(
            PersistenceService persistenceService,
            ExecutionContextManager executionContextManager) {
        org.apache.unomi.services.impl.scheduler.SchedulerServiceImpl schedulerService =
            new org.apache.unomi.services.impl.scheduler.SchedulerServiceImpl();
        schedulerService.setPersistenceService(persistenceService);
        schedulerService.setThreadPoolSize(4); // Ensure enough threads for parallel execution
        schedulerService.setExecutorNode(true);
        schedulerService.setPurgeTaskEnabled(false); // Disable purge task by default for tests
        schedulerService.postConstruct();
        return schedulerService;
    }
}
