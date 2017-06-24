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
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.model.RouteDefinition;
import org.apache.unomi.router.api.ImportConfiguration;
import org.apache.unomi.router.api.services.ImportConfigurationService;
import org.apache.unomi.router.core.processor.ImportConfigByFileNameProcessor;
import org.apache.unomi.router.core.processor.RouteCompletionProcessor;
import org.apache.unomi.router.core.processor.UnomiStorageProcessor;
import org.apache.unomi.router.core.route.ProfileImportFromSourceRouteBuilder;
import org.apache.unomi.router.core.route.ProfileImportOneShotRouteBuilder;
import org.apache.unomi.router.core.route.ProfileImportToUnomiRouteBuilder;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.SynchronousBundleListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Created by amidani on 04/05/2017.
 */
public class ProfileImportCamelContext implements SynchronousBundleListener {

    private final String IMPORT_CONFIG_TYPE_RECURRENT = "recurrent";
    private Logger logger = LoggerFactory.getLogger(ProfileImportCamelContext.class.getName());
    private CamelContext camelContext;
    private UnomiStorageProcessor unomiStorageProcessor;
    private RouteCompletionProcessor routeCompletionProcessor;
    private ImportConfigByFileNameProcessor importConfigByFileNameProcessor;
    private ImportConfigurationService importConfigurationService;
    private JacksonDataFormat jacksonDataFormat;
    private String uploadDir;
    private Map<String, String> kafkaProps;
    private String configType;
    private BundleContext bundleContext;

    public void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    public void initCamelContext() throws Exception {
        logger.info("Initialize Camel Context...");
        camelContext = new DefaultCamelContext();

        ProfileImportFromSourceRouteBuilder builderReader = new ProfileImportFromSourceRouteBuilder(kafkaProps, configType);
        builderReader.setImportConfigurationService(importConfigurationService);
        builderReader.setJacksonDataFormat(jacksonDataFormat);
        builderReader.setContext(camelContext);
        camelContext.addRoutes(builderReader);

        //One shot import route
        ProfileImportOneShotRouteBuilder builderOneShot = new ProfileImportOneShotRouteBuilder(kafkaProps, configType);
        builderOneShot.setImportConfigByFileNameProcessor(importConfigByFileNameProcessor);
        builderOneShot.setJacksonDataFormat(jacksonDataFormat);
        builderOneShot.setUploadDir(uploadDir);
        builderOneShot.setContext(camelContext);
        camelContext.addRoutes(builderOneShot);


        ProfileImportToUnomiRouteBuilder builderProcessor = new ProfileImportToUnomiRouteBuilder(kafkaProps, configType);
        builderProcessor.setUnomiStorageProcessor(unomiStorageProcessor);
        builderProcessor.setRouteCompletionProcessor(routeCompletionProcessor);
        builderProcessor.setJacksonDataFormat(jacksonDataFormat);
        builderProcessor.setContext(camelContext);
        camelContext.addRoutes(builderProcessor);

        camelContext.start();

        logger.debug("postConstruct {" + bundleContext.getBundle() + "}");

        processBundleStartup(bundleContext);
        for (Bundle bundle : bundleContext.getBundles()) {
            if (bundle.getBundleContext() != null) {
                processBundleStartup(bundle.getBundleContext());
            }
        }
        bundleContext.addBundleListener(this);
        logger.info("Camel Context {} initialized successfully.");

    }

    private boolean stopRoute(String routeId) throws Exception {
        return camelContext.stopRoute(routeId, 10L, TimeUnit.SECONDS, true);
    }

    public void updateProfileImportReaderRoute(ImportConfiguration importConfiguration) throws Exception {
        //Active routes
        Route route = camelContext.getRoute(importConfiguration.getItemId());
        if (route != null && stopRoute(importConfiguration.getItemId())) {
            camelContext.removeRoute(importConfiguration.getItemId());
        }

        //Inactive routes
        RouteDefinition routeDefinition = camelContext.getRouteDefinition(importConfiguration.getItemId());
        if (routeDefinition != null) {
            camelContext.removeRouteDefinition(routeDefinition);
        }
        //Handle transforming an import config oneshot <--> recurrent
        if (IMPORT_CONFIG_TYPE_RECURRENT.equals(importConfiguration.getConfigType())) {
            ProfileImportFromSourceRouteBuilder builder = new ProfileImportFromSourceRouteBuilder(kafkaProps, configType);
            builder.setImportConfigurationList(Arrays.asList(importConfiguration));
            builder.setImportConfigurationService(importConfigurationService);
            builder.setJacksonDataFormat(jacksonDataFormat);
            builder.setContext(camelContext);
            camelContext.addRoutes(builder);
        }
    }

    public CamelContext getCamelContext() {
        return camelContext;
    }

    public void setUnomiStorageProcessor(UnomiStorageProcessor unomiStorageProcessor) {
        this.unomiStorageProcessor = unomiStorageProcessor;
    }

    public void setRouteCompletionProcessor(RouteCompletionProcessor routeCompletionProcessor) {
        this.routeCompletionProcessor = routeCompletionProcessor;
    }

    public void setImportConfigByFileNameProcessor(ImportConfigByFileNameProcessor importConfigByFileNameProcessor) {
        this.importConfigByFileNameProcessor = importConfigByFileNameProcessor;
    }

    public void setImportConfigurationService(ImportConfigurationService importConfigurationService) {
        this.importConfigurationService = importConfigurationService;
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

    public void preDestroy() throws Exception {
        bundleContext.removeBundleListener(this);
        //This is to shutdown Camel context
        //(will stop all routes/components/endpoints etc and clear internal state/cache)
        this.camelContext.stop();
        logger.info("Camel context for profile import is shutdown.");
    }

    private void processBundleStartup(BundleContext bundleContext) {
        if (bundleContext == null) {
            return;
        }
    }

    @Override
    public void bundleChanged(BundleEvent bundleEvent) {

    }
}
