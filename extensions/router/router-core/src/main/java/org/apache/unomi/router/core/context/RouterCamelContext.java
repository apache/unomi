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
import org.apache.camel.Route;
import org.apache.camel.component.jackson.JacksonDataFormat;
import org.apache.camel.core.osgi.OsgiDefaultCamelContext;
import org.apache.camel.model.RouteDefinition;
import org.apache.unomi.api.services.ClusterService;
import org.apache.unomi.api.services.ConfigSharingService;
import org.apache.unomi.api.services.ProfileService;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.apache.unomi.router.api.ExportConfiguration;
import org.apache.unomi.router.api.IRouterCamelContext;
import org.apache.unomi.router.api.ImportConfiguration;
import org.apache.unomi.router.api.RouterConstants;
import org.apache.unomi.router.api.services.ImportExportConfigurationService;
import org.apache.unomi.router.api.services.ProfileExportService;
import org.apache.unomi.router.core.event.UpdateCamelRouteEvent;
import org.apache.unomi.router.core.processor.ExportRouteCompletionProcessor;
import org.apache.unomi.router.core.processor.ImportConfigByFileNameProcessor;
import org.apache.unomi.router.core.processor.ImportRouteCompletionProcessor;
import org.apache.unomi.router.core.processor.UnomiStorageProcessor;
import org.apache.unomi.router.core.route.*;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Map;

/**
 * Created by amidani on 04/05/2017.
 */
public class RouterCamelContext implements IRouterCamelContext {

    private Logger logger = LoggerFactory.getLogger(RouterCamelContext.class.getName());
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
    private BundleContext bundleContext;
    private ConfigSharingService configSharingService;
    private ClusterService clusterService;

    public static String EVENT_ID_REMOVE = "org.apache.unomi.router.event.remove";
    public static String EVENT_ID_IMPORT = "org.apache.unomi.router.event.import";
    public static String EVENT_ID_EXPORT = "org.apache.unomi.router.event.export";

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

    public void setClusterService(ClusterService clusterService) {
        this.clusterService = clusterService;
    }

    public void setTracing(boolean tracing) {
        camelContext.setTracing(true);
    }

    public void initCamelContext() throws Exception {
        logger.info("Initialize Camel Context...");

        configSharingService.setProperty(RouterConstants.IMPORT_ONESHOT_UPLOAD_DIR, uploadDir);
        configSharingService.setProperty(RouterConstants.KEY_HISTORY_SIZE, execHistorySize);

        camelContext = new OsgiDefaultCamelContext(bundleContext);

        //--IMPORT ROUTES

        //Source
        ProfileImportFromSourceRouteBuilder builderReader = new ProfileImportFromSourceRouteBuilder(kafkaProps, configType);
        builderReader.setProfileService(profileService);
        builderReader.setImportConfigurationService(importConfigurationService);
        builderReader.setJacksonDataFormat(jacksonDataFormat);
        builderReader.setAllowedEndpoints(allowedEndpoints);
        builderReader.setContext(camelContext);
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
        profileExportCollectRouteBuilder.setExportConfigurationService(exportConfigurationService);
        profileExportCollectRouteBuilder.setPersistenceService(persistenceService);
        profileExportCollectRouteBuilder.setAllowedEndpoints(allowedEndpoints);
        profileExportCollectRouteBuilder.setJacksonDataFormat(jacksonDataFormat);
        profileExportCollectRouteBuilder.setContext(camelContext);
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

        logger.debug("postConstruct {" + bundleContext.getBundle() + "}");

        importConfigurationService.setRouterCamelContext(this);
        exportConfigurationService.setRouterCamelContext(this);

        logger.info("Camel Context {} initialized successfully.");
    }

    public void killExistingRoute(String routeId, boolean fireEvent) throws Exception {
        //Active routes
        Route route = camelContext.getRoute(routeId);
        if (route != null) {
            RouteDefinition routeDefinition = camelContext.getRouteDefinition(routeId);
            if (routeDefinition != null) {
                camelContext.removeRouteDefinition(routeDefinition);
            }
        }

        if (fireEvent) {
            UpdateCamelRouteEvent event = new UpdateCamelRouteEvent(EVENT_ID_REMOVE);
            event.setRouteId(routeId);
            clusterService.sendEvent(event);
        }
    }

    public void updateProfileReaderRoute(Object configuration, boolean fireEvent) throws Exception {
        if (configuration instanceof ImportConfiguration) {
            updateProfileImportReaderRoute((ImportConfiguration) configuration, fireEvent);
        } else {
            updateProfileExportReaderRoute((ExportConfiguration) configuration, fireEvent);
        }
    }

    private void updateProfileImportReaderRoute(ImportConfiguration importConfiguration, boolean fireEvent) throws Exception {
        killExistingRoute(importConfiguration.getItemId(), false);
        //Handle transforming an import config oneshot <--> recurrent
        if (RouterConstants.IMPORT_EXPORT_CONFIG_TYPE_RECURRENT.equals(importConfiguration.getConfigType())) {
            ProfileImportFromSourceRouteBuilder builder = new ProfileImportFromSourceRouteBuilder(kafkaProps, configType);
            builder.setImportConfigurationList(Arrays.asList(importConfiguration));
            builder.setImportConfigurationService(importConfigurationService);
            builder.setProfileService(profileService);
            builder.setAllowedEndpoints(allowedEndpoints);
            builder.setJacksonDataFormat(jacksonDataFormat);
            builder.setContext(camelContext);
            camelContext.addRoutes(builder);

            if (fireEvent) {
                UpdateCamelRouteEvent event = new UpdateCamelRouteEvent(EVENT_ID_IMPORT);
                event.setConfiguration(importConfiguration);
                clusterService.sendEvent(event);
            }
        }
    }

    private void updateProfileExportReaderRoute(ExportConfiguration exportConfiguration, boolean fireEvent) throws Exception {
        killExistingRoute(exportConfiguration.getItemId(), false);
        //Handle transforming an import config oneshot <--> recurrent
        if (RouterConstants.IMPORT_EXPORT_CONFIG_TYPE_RECURRENT.equals(exportConfiguration.getConfigType())) {
            ProfileExportCollectRouteBuilder profileExportCollectRouteBuilder = new ProfileExportCollectRouteBuilder(kafkaProps, configType);
            profileExportCollectRouteBuilder.setExportConfigurationList(Arrays.asList(exportConfiguration));
            profileExportCollectRouteBuilder.setExportConfigurationService(exportConfigurationService);
            profileExportCollectRouteBuilder.setPersistenceService(persistenceService);
            profileExportCollectRouteBuilder.setAllowedEndpoints(allowedEndpoints);
            profileExportCollectRouteBuilder.setJacksonDataFormat(jacksonDataFormat);
            profileExportCollectRouteBuilder.setContext(camelContext);
            camelContext.addRoutes(profileExportCollectRouteBuilder);

            if (fireEvent) {
                UpdateCamelRouteEvent event = new UpdateCamelRouteEvent(EVENT_ID_EXPORT);
                event.setConfiguration(exportConfiguration);
                clusterService.sendEvent(event);
            }
        }
    }

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

    public void preDestroy() throws Exception {
        //This is to shutdown Camel context
        //(will stop all routes/components/endpoints etc and clear internal state/cache)
        this.camelContext.stop();
        logger.info("Camel context for profile import is shutdown.");
    }
}
