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
package org.apache.unomi.router.core.context;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Route;
import org.apache.camel.component.jackson.JacksonDataFormat;
import org.apache.camel.core.osgi.OsgiDefaultCamelContext;
import org.apache.camel.management.event.ExchangeCompletedEvent;
import org.apache.camel.management.event.ExchangeCreatedEvent;
import org.apache.camel.management.event.ExchangeSentEvent;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.support.EventNotifierSupport;
import org.apache.unomi.api.Item;
import org.apache.unomi.api.services.ConfigSharingService;
import org.apache.unomi.api.services.ExecutionContextManager;
import org.apache.unomi.api.services.ProfileService;
import org.apache.unomi.api.services.SchedulerService;
import org.apache.unomi.api.tasks.ScheduledTask;
import org.apache.unomi.api.tenants.Tenant;
import org.apache.unomi.api.tenants.TenantService;
import org.apache.unomi.api.security.SecurityService;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.apache.unomi.router.api.ExportConfiguration;
import org.apache.unomi.router.api.IRouterCamelContext;
import org.apache.unomi.router.api.ImportConfiguration;
import org.apache.unomi.router.api.RouteIds;
import org.apache.unomi.router.api.RouterConstants;
import org.apache.unomi.router.api.services.ImportExportConfigurationService;
import org.apache.unomi.router.api.services.ProfileExportService;
import org.apache.unomi.router.core.processor.ExportRouteCompletionProcessor;
import org.apache.unomi.router.core.processor.ImportConfigByFileNameProcessor;
import org.apache.unomi.router.core.processor.ImportRouteCompletionProcessor;
import org.apache.unomi.router.core.processor.UnomiStorageProcessor;
import org.apache.unomi.router.core.route.*;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * The main Camel context manager for the Unomi Router component.
 * This class manages the lifecycle of all import and export routes,
 * handles route configuration updates, and maintains the Camel context.
 *
 * <p>Features:
 * <ul>
 *   <li>Initializes and manages the Camel context</li>
 *   <li>Sets up import and export routes</li>
 *   <li>Handles route configuration updates</li>
 *   <li>Manages route lifecycle (start/stop/update)</li>
 *   <li>Supports Kafka ({@link RouterConstants#CONFIG_TYPE_KAFKA}) and in-process
 *       {@code direct:} endpoints when configured as {@link RouterConstants#CONFIG_TYPE_NOBROKER}</li>
 * </ul>
 *
 * @since 1.0
 */
public class RouterCamelContext implements IRouterCamelContext {

    private static final Logger LOGGER = LoggerFactory.getLogger(RouterCamelContext.class.getName());
    private CamelContext camelContext;
    private UnomiStorageProcessor unomiStorageProcessor;
    private ImportRouteCompletionProcessor importRouteCompletionProcessor;
    private ExportRouteCompletionProcessor exportRouteCompletionProcessor;
    private ImportConfigByFileNameProcessor importConfigByFileNameProcessor;
    private ImportExportConfigurationService<ImportConfiguration> importConfigurationService;
    private ImportExportConfigurationService<ExportConfiguration> exportConfigurationService;
    private PersistenceService persistenceService;
    private ProfileService profileService;
    private ProfileExportService profileExportService;
    private JacksonDataFormat jacksonDataFormat;
    private String uploadDir;
    private String execHistorySize;
    private String execErrReportSize;
    private Map<String, String> kafkaProps;
    private String configType;
    private String allowedEndpoints;
    private String permittedImportBaseDirs;
    private String permittedExportBaseDirs;
    private BundleContext bundleContext;
    private ConfigSharingService configSharingService;
    private ExecutionContextManager contextManager;
    private SecurityService securityService;
    private TenantService tenantService;

    /** Tenants whose recurring routes could not be built at startup; the refresh timer retries them. */
    private final Set<String> importTenantsToRetry = ConcurrentHashMap.newKeySet();
    private final Set<String> exportTenantsToRetry = ConcurrentHashMap.newKeySet();
    private volatile boolean tenantListingToRetry;
    private final Set<String> tenantsWithLoggedLoadFailure = ConcurrentHashMap.newKeySet();
    static final long MAX_STARTUP_RETRY_DELAY_MS = 60_000;
    private long startupRetryDelay;
    private long nextStartupRetryTime;

    private SchedulerService schedulerService;
    private ScheduledTask scheduledTask;

    private Integer configsRefreshInterval = 1000;
    private static final int MAX_ROUTE_CREATION_RETRIES = 5;
    private final Map<RetryKey, Integer> routeCreationRetryCount = new ConcurrentHashMap<>();

    /** Identifies a route refresh attempt being retried, scoped by direction and tenant so import/export configs and different tenants can never collide. */
    private record RetryKey(String direction, String tenantId, String configId) {
    }

    public void setExecHistorySize(String execHistorySize) {
        this.execHistorySize = execHistorySize;
    }

    public void setExecErrReportSize(String execErrReportSize) {
        this.execErrReportSize = execErrReportSize;
    }

    public void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    public void setConfigSharingService(ConfigSharingService configSharingService) {
        this.configSharingService = configSharingService;
    }

    public void setContextManager(ExecutionContextManager contextManager) {
        this.contextManager = contextManager;
    }

    public void setTenantService(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    /** {@inheritDoc} */
    @Override
    public void setTracing(boolean tracing) {
        camelContext.setTracing(tracing);
    }

    public void setSchedulerService(SchedulerService schedulerService) {
        this.schedulerService = schedulerService;
    }

    public void init() throws Exception {
        LOGGER.info("Initialize Camel Context...");

        configSharingService.setProperty(RouterConstants.IMPORT_ONESHOT_UPLOAD_DIR, uploadDir);
        // shared with router-rest, which validates a configuration's endpoint before it is stored
        configSharingService.setProperty(RouterConstants.CONFIG_ALLOWED_ENDPOINTS, allowedEndpoints);
        configSharingService.setProperty(RouterConstants.CONFIG_IMPORT_BASE_DIRS, permittedImportBaseDirs);
        configSharingService.setProperty(RouterConstants.CONFIG_EXPORT_BASE_DIRS, permittedExportBaseDirs);
        configSharingService.setProperty(RouterConstants.KEY_HISTORY_SIZE, execHistorySize);

        try {
            initCamel();
        } catch (Exception e) {
            // Do not leave a context behind that nothing will ever stop.
            if (camelContext != null) {
                try {
                    camelContext.stop();
                } catch (Exception stopFailure) {
                    e.addSuppressed(stopFailure);
                }
                camelContext = null;
            }
            throw e;
        }

        initTimers();
        LOGGER.info("Camel Context initialized successfully.");
    }

    public void destroy() throws Exception {
        if (scheduledTask != null) {
            schedulerService.cancelTask(scheduledTask.getItemId());
        }
        //This is to shutdown Camel context
        //(will stop all routes/components/endpoints etc and clear internal state/cache)
        this.camelContext.stop();
        LOGGER.info("Camel context for profile import is shutdown.");
    }

    private void initTimers() {
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                refreshRoutes();
            }
        };
        scheduledTask = schedulerService.createRecurringTask("camel-route-refresh", configsRefreshInterval, TimeUnit.MILLISECONDS, task, false);
    }

    /**
     * One tick of the refresh timer: retries what could not be started at startup, then rebuilds or removes
     * the routes of the configurations that were saved, deleted or re-queued since the previous tick.
     */
    void refreshRoutes() {
        try {
            retryStartupLoadsWhenDue(System.currentTimeMillis());
        } catch (Exception e) {
            // must not keep the refreshes below from being processed
            LOGGER.warn("Unexpected error while retrying to start the recurring routes that could not be started at startup", e);
        }
        try {
            Map<String, Map<String, RouterConstants.CONFIG_CAMEL_REFRESH>> tenantsImportConfigsToRefresh = importConfigurationService.consumeConfigsToBeRefresh();

            for (Map.Entry<String, Map<String, RouterConstants.CONFIG_CAMEL_REFRESH>> tenantImportConfigsToRefresh : tenantsImportConfigsToRefresh.entrySet()) {
                String tenantId = tenantImportConfigsToRefresh.getKey();
                refreshRoutesOfTenant(tenantId, () -> {
                    try {
                        for (Map.Entry<String, RouterConstants.CONFIG_CAMEL_REFRESH> importConfigToRefresh : tenantImportConfigsToRefresh.getValue().entrySet()) {
                            String configId = importConfigToRefresh.getKey();
                            RouterConstants.CONFIG_CAMEL_REFRESH refreshType = importConfigToRefresh.getValue();
                            RetryKey retryKey = new RetryKey("import", tenantId, configId);
                            try {
                                if (refreshType.equals(RouterConstants.CONFIG_CAMEL_REFRESH.UPDATED)) {
                                    updateProfileImportReaderRoute(tenantId, configId, true);
                                    routeCreationRetryCount.remove(retryKey);
                                } else if (refreshType.equals(RouterConstants.CONFIG_CAMEL_REFRESH.REMOVED)) {
                                    killExistingRoute(tenantId, configId, true);
                                    routeCreationRetryCount.remove(retryKey);
                                }
                            } catch (Exception e) {
                                int attempt = routeCreationRetryCount.merge(retryKey, 1, Integer::sum);
                                if (attempt <= MAX_ROUTE_CREATION_RETRIES) {
                                    LOGGER.error("Refreshing({}) camel route {} failed (attempt {}/{}) — will retry on next tick",
                                            refreshType, configId, attempt, MAX_ROUTE_CREATION_RETRIES, e);
                                    importConfigurationService.requeueForRefresh(tenantId, configId, refreshType);
                                } else {
                                    LOGGER.error("Refreshing({}) camel route {} failed after {} attempts — giving up",
                                            refreshType, configId, MAX_ROUTE_CREATION_RETRIES, e);
                                    routeCreationRetryCount.remove(retryKey);
                                    markImportRouteCreationFailed(configId);
                                }
                            }
                        }
                    } catch (Exception e) {
                        LOGGER.error("Unexpected error while refreshing import/export camel routes for tenant {}", tenantId, e);
                    }
                    return null;
                });
            }

            Map<String, Map<String, RouterConstants.CONFIG_CAMEL_REFRESH>> tenantsExportConfigsToRefresh = exportConfigurationService.consumeConfigsToBeRefresh();
            for (Map.Entry<String, Map<String, RouterConstants.CONFIG_CAMEL_REFRESH>> tenantExportConfigsToRefresh : tenantsExportConfigsToRefresh.entrySet()) {
                String tenantId = tenantExportConfigsToRefresh.getKey();
                refreshRoutesOfTenant(tenantId, () -> {
                    try {
                        for (Map.Entry<String, RouterConstants.CONFIG_CAMEL_REFRESH> exportConfigToRefresh : tenantExportConfigsToRefresh.getValue().entrySet()) {
                            String configId = exportConfigToRefresh.getKey();
                            RouterConstants.CONFIG_CAMEL_REFRESH refreshType = exportConfigToRefresh.getValue();
                            RetryKey retryKey = new RetryKey("export", tenantId, configId);
                            try {
                                if (refreshType.equals(RouterConstants.CONFIG_CAMEL_REFRESH.UPDATED)) {
                                    updateProfileExportReaderRoute(tenantId, configId, true);
                                    routeCreationRetryCount.remove(retryKey);
                                } else if (refreshType.equals(RouterConstants.CONFIG_CAMEL_REFRESH.REMOVED)) {
                                    killExistingRoute(tenantId, configId, true);
                                    routeCreationRetryCount.remove(retryKey);
                                }
                            } catch (Exception e) {
                                int attempt = routeCreationRetryCount.merge(retryKey, 1, Integer::sum);
                                if (attempt <= MAX_ROUTE_CREATION_RETRIES) {
                                    LOGGER.error("Refreshing({}) camel route {} failed (attempt {}/{}) — will retry on next tick",
                                            refreshType, configId, attempt, MAX_ROUTE_CREATION_RETRIES, e);
                                    exportConfigurationService.requeueForRefresh(tenantId, configId, refreshType);
                                } else {
                                    LOGGER.error("Refreshing({}) camel route {} failed after {} attempts — giving up",
                                            refreshType, configId, MAX_ROUTE_CREATION_RETRIES, e);
                                    routeCreationRetryCount.remove(retryKey);
                                    markExportRouteCreationFailed(configId);
                                }
                            }
                        }
                    } catch (Exception e) {
                        LOGGER.error("Unexpected error while refreshing import/export camel routes for tenant {}", tenantId, e);
                    }
                    return null;
                });
            }
        } catch (Exception e) {
            LOGGER.error("Unexpected error while refreshing import/export camel routes", e);
        }
    }

    /** Runs the refresh of one tenant's routes; a tenant that cannot be switched to must not keep the others from being refreshed. */
    private void refreshRoutesOfTenant(String tenantId, Supplier<Object> refresh) {
        try {
            contextManager.executeAsTenant(tenantId, refresh);
        } catch (Exception e) {
            LOGGER.error("Could not refresh the camel routes of tenant {}", tenantId, e);
        }
    }

    /** Creates the Camel context the routes are added to; a seam for running the route setup outside OSGi. */
    CamelContext createCamelContext() {
        return new OsgiDefaultCamelContext(bundleContext);
    }

    void initCamel() throws Exception {
        camelContext = createCamelContext();

        // Setup listener, we might want to improve this to know exactly what is running at a given time and expose an API to query this information
        camelContext.getManagementStrategy().addEventNotifier(new EventNotifierSupport() {
            @Override
            public void notify(EventObject event) throws Exception {
                if (event instanceof ExchangeCreatedEvent) {
                    ExchangeCreatedEvent exchangeCreatedEvent = (ExchangeCreatedEvent) event;
                    Exchange exchange = exchangeCreatedEvent.getExchange();
                    LOGGER.info("Exchange Created: {}", exchange.getExchangeId());
                } else if (event instanceof ExchangeSentEvent) {
                    ExchangeSentEvent sentEvent = (ExchangeSentEvent) event;
                    LOGGER.info("Processed: {} in {}ms by endpoint {} ", sentEvent.getExchange().getIn().getBody(), sentEvent.getTimeTaken(), sentEvent.getEndpoint().getEndpointUri());
                } else if (event instanceof ExchangeCompletedEvent) {
                    ExchangeCompletedEvent completedEvent = (ExchangeCompletedEvent) event;
                    Exchange exchange = completedEvent.getExchange();
                    LOGGER.info("Exchange Completed: {}", exchange.getExchangeId());
                }
            }

            @Override
            public boolean isEnabled(EventObject event) {
                return event instanceof ExchangeCreatedEvent || event instanceof ExchangeCompletedEvent ||  event instanceof ExchangeSentEvent;
            }
        });

        // Listed once for the import and the export routes, so that both start for the same tenants.
        Set<String> startupTenantIds = contextManager.executeAsSystem(() -> startupTenantIds());

        //--IMPORT ROUTES

        //Source
        ProfileImportFromSourceRouteBuilder builderReader = new ProfileImportFromSourceRouteBuilder(kafkaProps, configType);
        builderReader.setProfileService(profileService);
        builderReader.setImportConfigurationService(importConfigurationService);
        // Load configs under each tenant: getAll() alone only sees the current (system) context.
        builderReader.setImportConfigurationList(loadConfigurationsAcrossTenants(importConfigurationService, startupTenantIds, importTenantsToRetry));
        builderReader.setJacksonDataFormat(jacksonDataFormat);
        builderReader.setAllowedEndpoints(allowedEndpoints);
        builderReader.setPermittedImportBaseDirs(permittedImportBaseDirs);
        builderReader.setContext(camelContext);
        builderReader.setExecutionContextManager(contextManager);
        builderReader.setSecurityService(securityService);
        camelContext.addRoutes(builderReader);

        //One shot import route
        ProfileImportOneShotRouteBuilder builderOneShot = new ProfileImportOneShotRouteBuilder(kafkaProps, configType);
        builderOneShot.setProfileService(profileService);
        builderOneShot.setImportConfigByFileNameProcessor(importConfigByFileNameProcessor);
        builderOneShot.setJacksonDataFormat(jacksonDataFormat);
        builderOneShot.setUploadDir(uploadDir);
        builderOneShot.setContext(camelContext);
        camelContext.addRoutes(builderOneShot);

        //Unomi sink route
        ProfileImportToUnomiRouteBuilder builderProcessor = new ProfileImportToUnomiRouteBuilder(kafkaProps, configType);
        builderProcessor.setUnomiStorageProcessor(unomiStorageProcessor);
        builderProcessor.setImportRouteCompletionProcessor(importRouteCompletionProcessor);
        builderProcessor.setJacksonDataFormat(jacksonDataFormat);
        builderProcessor.setContext(camelContext);
        camelContext.addRoutes(builderProcessor);

        //--EXPORT ROUTES

        //Profiles collect
        ProfileExportCollectRouteBuilder profileExportCollectRouteBuilder = new ProfileExportCollectRouteBuilder(kafkaProps, configType);
        profileExportCollectRouteBuilder.setExportConfigurationList(loadConfigurationsAcrossTenants(exportConfigurationService, startupTenantIds, exportTenantsToRetry));
        profileExportCollectRouteBuilder.setExportConfigurationService(exportConfigurationService);
        profileExportCollectRouteBuilder.setPersistenceService(persistenceService);
        profileExportCollectRouteBuilder.setAllowedEndpoints(allowedEndpoints);
        profileExportCollectRouteBuilder.setPermittedExportBaseDirs(permittedExportBaseDirs);
        profileExportCollectRouteBuilder.setJacksonDataFormat(jacksonDataFormat);
        profileExportCollectRouteBuilder.setContext(camelContext);
        profileExportCollectRouteBuilder.setExecutionContextManager(contextManager);
        camelContext.addRoutes(profileExportCollectRouteBuilder);

        //Write to destination
        ProfileExportProducerRouteBuilder profileExportProducerRouteBuilder = new ProfileExportProducerRouteBuilder(kafkaProps, configType);
        profileExportProducerRouteBuilder.setProfileService(profileService);
        profileExportProducerRouteBuilder.setProfileExportService(profileExportService);
        profileExportProducerRouteBuilder.setExportRouteCompletionProcessor(exportRouteCompletionProcessor);
        profileExportProducerRouteBuilder.setAllowedEndpoints(allowedEndpoints);
        profileExportProducerRouteBuilder.setJacksonDataFormat(jacksonDataFormat);
        profileExportProducerRouteBuilder.setContext(camelContext);
        camelContext.addRoutes(profileExportProducerRouteBuilder);

        camelContext.start();
    }

    /** Persists a visible failure status on the configuration once route creation has exhausted all retries, instead of only logging it. */
    private void markImportRouteCreationFailed(String configId) {
        ImportConfiguration config = importConfigurationService.load(configId);
        if (config != null) {
            config.setStatus(RouterConstants.CONFIG_STATUS_ROUTE_CREATION_FAILED);
            importConfigurationService.save(config, false);
        }
    }

    /** Persists a visible failure status on the configuration once route creation has exhausted all retries, instead of only logging it. */
    private void markExportRouteCreationFailed(String configId) {
        ExportConfiguration config = exportConfigurationService.load(configId);
        if (config != null) {
            config.setStatus(RouterConstants.CONFIG_STATUS_ROUTE_CREATION_FAILED);
            exportConfigurationService.save(config, false);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void killExistingRoute(String tenantId, String configId, boolean fireEvent) throws Exception {
        String routeId = RouteIds.of(tenantId, configId);
        //Active routes
        Route route = camelContext.getRoute(routeId);
        if (route != null) {
            RouteDefinition routeDefinition = camelContext.getRouteDefinition(routeId);
            if (routeDefinition != null) {
                camelContext.removeRouteDefinition(routeDefinition);
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public void updateProfileImportReaderRoute(String tenantId, String configId, boolean fireEvent) throws Exception {
        killExistingRoute(tenantId, configId, false);

        ImportConfiguration importConfiguration = importConfigurationService.load(configId);
        if (importConfiguration == null) {
            throw new IllegalStateException("Cannot update profile import reader route, config: " + configId + " not found — will be retried");
        }

        if (RouterConstants.IMPORT_EXPORT_CONFIG_TYPE_RECURRENT.equals(importConfiguration.getConfigType())) {
            ProfileImportFromSourceRouteBuilder builder = new ProfileImportFromSourceRouteBuilder(kafkaProps, configType);
            builder.setImportConfigurationList(Arrays.asList(importConfiguration));
            builder.setImportConfigurationService(importConfigurationService);
            builder.setProfileService(profileService);
            builder.setAllowedEndpoints(allowedEndpoints);
            builder.setPermittedImportBaseDirs(permittedImportBaseDirs);
            builder.setJacksonDataFormat(jacksonDataFormat);
            builder.setContext(camelContext);
            builder.setExecutionContextManager(contextManager);
            builder.setSecurityService(securityService);
            camelContext.addRoutes(builder);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void updateProfileExportReaderRoute(String tenantId, String configId, boolean fireEvent) throws Exception {
        killExistingRoute(tenantId, configId, false);

        ExportConfiguration exportConfiguration = exportConfigurationService.load(configId);
        if (exportConfiguration == null) {
            throw new IllegalStateException("Cannot update profile export reader route, config: " + configId + " not found — will be retried");
        }

        if (RouterConstants.IMPORT_EXPORT_CONFIG_TYPE_RECURRENT.equals(exportConfiguration.getConfigType())) {
            ProfileExportCollectRouteBuilder profileExportCollectRouteBuilder = new ProfileExportCollectRouteBuilder(kafkaProps, configType);
            profileExportCollectRouteBuilder.setExportConfigurationList(Collections.singletonList(exportConfiguration));
            profileExportCollectRouteBuilder.setExportConfigurationService(exportConfigurationService);
            profileExportCollectRouteBuilder.setPersistenceService(persistenceService);
            profileExportCollectRouteBuilder.setExecutionContextManager(contextManager);
            profileExportCollectRouteBuilder.setAllowedEndpoints(allowedEndpoints);
            profileExportCollectRouteBuilder.setPermittedExportBaseDirs(permittedExportBaseDirs);
            profileExportCollectRouteBuilder.setJacksonDataFormat(jacksonDataFormat);
            profileExportCollectRouteBuilder.setContext(camelContext);
            camelContext.addRoutes(profileExportCollectRouteBuilder);
        }
    }

    /**
     * {@inheritDoc}
     * <p>The concrete type is {@link org.apache.camel.CamelContext}; callers may narrow the reference safely.</p>
     */
    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    public void setUnomiStorageProcessor(UnomiStorageProcessor unomiStorageProcessor) {
        this.unomiStorageProcessor = unomiStorageProcessor;
    }

    public void setImportRouteCompletionProcessor(ImportRouteCompletionProcessor importRouteCompletionProcessor) {
        this.importRouteCompletionProcessor = importRouteCompletionProcessor;
    }

    public void setExportRouteCompletionProcessor(ExportRouteCompletionProcessor exportRouteCompletionProcessor) {
        this.exportRouteCompletionProcessor = exportRouteCompletionProcessor;
    }

    public void setImportConfigByFileNameProcessor(ImportConfigByFileNameProcessor importConfigByFileNameProcessor) {
        this.importConfigByFileNameProcessor = importConfigByFileNameProcessor;
    }

    public void setImportConfigurationService(ImportExportConfigurationService<ImportConfiguration> importConfigurationService) {
        this.importConfigurationService = importConfigurationService;
    }

    public void setExportConfigurationService(ImportExportConfigurationService<ExportConfiguration> exportConfigurationService) {
        this.exportConfigurationService = exportConfigurationService;
    }

    public void setPersistenceService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    public void setProfileExportService(ProfileExportService profileExportService) {
        this.profileExportService = profileExportService;
    }

    public void setProfileService(ProfileService profileService) {
        this.profileService = profileService;
    }

    public void setJacksonDataFormat(JacksonDataFormat jacksonDataFormat) {
        this.jacksonDataFormat = jacksonDataFormat;
    }

    public void setUploadDir(String uploadDir) {
        this.uploadDir = uploadDir;
    }

    public void setKafkaProps(Map<String, String> kafkaProps) {
        this.kafkaProps = kafkaProps;
    }

    public void setConfigType(String configType) {
        this.configType = configType;
    }

    public void setAllowedEndpoints(String allowedEndpoints) {
        this.allowedEndpoints = allowedEndpoints;
    }

    public void setConfigsRefreshInterval(int configsRefreshInterval) {
        this.configsRefreshInterval = configsRefreshInterval;
    }

    public void setSecurityService(SecurityService securityService) {
        this.securityService = securityService;
    }

    /**
     * Sets the comma-separated base directories an import {@code file} endpoint may resolve into.
     *
     * @param permittedImportBaseDirs the permitted import base directories
     */
    public void setPermittedImportBaseDirs(String permittedImportBaseDirs) {
        this.permittedImportBaseDirs = permittedImportBaseDirs;
    }

    /**
     * Sets the comma-separated base directories an export {@code file} endpoint may resolve into.
     *
     * @param permittedExportBaseDirs the permitted export base directories
     */
    public void setPermittedExportBaseDirs(String permittedExportBaseDirs) {
        this.permittedExportBaseDirs = permittedExportBaseDirs;
    }

    /**
     * Loads import/export configurations for the given tenants.
     * {@link ImportExportConfigurationService#getAll()} is context-scoped, so a single call
     * under the system context at startup would miss recurring configs of migrated tenants.
     * <p>A tenant whose configurations cannot be loaded does not block the others: it is added to
     * {@code tenantsToRetry} and the refresh timer builds its routes once it can be loaded.</p>
     */
    <T> List<T> loadConfigurationsAcrossTenants(ImportExportConfigurationService<T> service, Set<String> tenantIds,
                                                Set<String> tenantsToRetry) {
        // Startup has no request subject; nest tenant switches under system like other router paths.
        return contextManager.executeAsSystem(() -> {
            List<T> all = new ArrayList<>();
            for (String tenantId : tenantIds) {
                try {
                    all.addAll(contextManager.executeAsTenant(tenantId, service::getAll));
                } catch (Exception e) {
                    LOGGER.error("Could not load router configurations for tenant {}, its recurring routes are not "
                            + "started; the load will be retried", tenantId, e);
                    tenantsToRetry.add(tenantId);
                    tenantsWithLoggedLoadFailure.add(tenantId);
                }
            }
            return all;
        });
    }

    /** Every tenant plus the system tenant, or the system tenant alone until tenants can be listed. */
    Set<String> startupTenantIds() {
        Set<String> tenantIds = new LinkedHashSet<>();
        try {
            for (Tenant tenant : tenantService.getAllTenants()) {
                tenantIds.add(tenant.getItemId());
            }
        } catch (Exception e) {
            LOGGER.error("Could not list tenants, only the recurring routes of the system tenant are started; "
                    + "the load will be retried", e);
            tenantListingToRetry = true;
        }
        tenantIds.add(TenantService.SYSTEM_TENANT);
        return tenantIds;
    }

    /**
     * Called on every tick of the refresh timer: retries what could not be started at startup, waiting
     * twice as long after each unsuccessful attempt, up to {@link #MAX_STARTUP_RETRY_DELAY_MS}.
     *
     * @param now the current time in milliseconds
     */
    void retryStartupLoadsWhenDue(long now) {
        if (now < nextStartupRetryTime) {
            return;
        }
        if (retryStartupLoads()) {
            startupRetryDelay = 0;
        } else {
            startupRetryDelay = Math.min(Math.max(configsRefreshInterval, startupRetryDelay * 2), MAX_STARTUP_RETRY_DELAY_MS);
            LOGGER.warn("Not all recurring routes could be started yet (tenants still to list: {}, import tenants to load: {}, "
                            + "export tenants to load: {}), next attempt in {} ms",
                    tenantListingToRetry, importTenantsToRetry, exportTenantsToRetry, startupRetryDelay);
        }
        nextStartupRetryTime = now + startupRetryDelay;
    }

    /**
     * Builds the recurring routes that could not be built at startup because tenants could not be listed or
     * a tenant's configurations could not be loaded.
     *
     * @return {@code true} when nothing is left to retry
     */
    boolean retryStartupLoads() {
        if (!tenantListingToRetry && importTenantsToRetry.isEmpty() && exportTenantsToRetry.isEmpty()) {
            return true;
        }
        contextManager.executeAsSystem(() -> {
            if (tenantListingToRetry) {
                try {
                    // the system tenant was loaded at startup, the others were not known
                    for (Tenant tenant : tenantService.getAllTenants()) {
                        importTenantsToRetry.add(tenant.getItemId());
                        exportTenantsToRetry.add(tenant.getItemId());
                    }
                    tenantListingToRetry = false;
                } catch (Exception e) {
                    // the stack trace was logged at startup
                    LOGGER.warn("Still cannot list tenants to start their recurring routes: {}", e.toString());
                }
            }
            retryStartupLoad(importTenantsToRetry, importConfigurationService);
            retryStartupLoad(exportTenantsToRetry, exportConfigurationService);
        });
        return !tenantListingToRetry && importTenantsToRetry.isEmpty() && exportTenantsToRetry.isEmpty();
    }

    /**
     * Hands the configurations of the tenants that can now be loaded to the refresh the timer runs next,
     * which builds their routes with its usual retries and failure status.
     */
    private <T extends Item> void retryStartupLoad(Set<String> tenantsToRetry, ImportExportConfigurationService<T> service) {
        for (String tenantId : tenantsToRetry) {
            try {
                List<T> configs = contextManager.executeAsTenant(tenantId, service::getAll);
                for (T config : configs) {
                    service.requeueForRefresh(tenantId, config.getItemId(), RouterConstants.CONFIG_CAMEL_REFRESH.UPDATED);
                }
                tenantsToRetry.remove(tenantId);
                LOGGER.info("Router configurations of tenant {} loaded, its recurring routes are being started", tenantId);
            } catch (Exception e) {
                if (tenantsWithLoggedLoadFailure.add(tenantId)) {
                    LOGGER.warn("Still cannot load router configurations for tenant {}", tenantId, e);
                } else {
                    // the stack trace was logged with the first failure
                    LOGGER.warn("Still cannot load router configurations for tenant {}: {}", tenantId, e.toString());
                }
            }
        }
    }
}
