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
package org.apache.unomi.shell.services.internal;

import org.apache.commons.lang3.StringUtils;
import org.apache.karaf.features.Dependency;
import org.apache.karaf.features.Feature;
import org.apache.karaf.features.FeatureState;
import org.apache.karaf.features.FeaturesService;
import org.apache.unomi.lifecycle.BundleWatcher;
import org.apache.unomi.shell.migration.MigrationService;
import org.apache.unomi.shell.services.UnomiManagementService;
import org.osgi.framework.BundleContext;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

/**
 * Implementation of the {@link UnomiManagementService} interface, providing functionality to manage
 * the lifecycle of Apache Unomi, including the installation and activation of features using
 * Karaf's {@link org.apache.karaf.features.FeaturesService}.
 *
 * <p>This service handles the following responsibilities:</p>
 * <ul>
 *   <li>Loading configuration from the OSGi Configuration Admin service, including start features configuration and feature lists.</li>
 *   <li>Starting Apache Unomi by installing and starting the configured features for a selected start features configuration.</li>
 *   <li>Stopping Apache Unomi by uninstalling features in reverse order to ensure proper teardown.</li>
 *   <li>Interfacing with the {@link org.apache.unomi.shell.migration.MigrationService} for migration tasks during startup.</li>
 * </ul>
 *
 * <p>The class is designed to be used within an OSGi environment and integrates with the Configuration Admin service
 * to dynamically adjust its behavior based on external configurations. It leverages the {@link FeaturesService} to
 * manage Karaf features dynamically.</p>
 *
 * <h3>Configuration</h3>
 * <p>The service reads its configuration from the OSGi Configuration Admin under the PID <code>org.apache.unomi.start</code>.
 * The configuration includes:</p>
 * <ul>
 *   <li><b>startFeatures</b>: A semicolon-separated list of features mapped to persistence implementations
 *       in the format <code>persistenceImplementation:feature1,feature2</code>.</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * <p>This service can be controlled programmatically through its methods:</p>
 * <ul>
 *   <li>{@link #startUnomi(String, boolean)}: Installs and starts features for the specified start features configuration.</li>
 *   <li>{@link #stopUnomi()}: Stops and uninstalls the previously started features.</li>
 * </ul>
 *
 * <h3>Dependencies</h3>
 * <p>The following dependencies are required for this service:</p>
 * <ul>
 *   <li>{@link MigrationService}: Handles migration tasks during startup.</li>
 *   <li>{@link FeaturesService}: Provides access to Karaf's feature management API for installing, starting, and stopping features.</li>
 * </ul>
 *
 * <p>This service is registered as an OSGi component and automatically activated when the bundle is started.
 * It is configured to listen for configuration updates and adapt its behavior accordingly.</p>
 *
 * @author dgaillard
 * @see org.apache.unomi.shell.services.UnomiManagementService
 * @see org.apache.unomi.shell.migration.MigrationService
 * @see org.apache.karaf.features.FeaturesService
 * @see org.apache.karaf.features.Feature
 */
@Component(service = UnomiManagementService.class, immediate = true)
public class UnomiManagementServiceImpl implements UnomiManagementService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UnomiManagementServiceImpl.class.getName());
    private static final int DEFAULT_TIMEOUT = 300; // 5 minutes timeout

    private static final String UNOMI_SETUP_PID = "org.apache.unomi.setup";
    private static final String CDP_GRAPHQL_FEATURE = "cdp-graphql-feature";

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    private MigrationService migrationService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    private ConfigurationAdmin configurationAdmin;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    private FeaturesService featuresService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    private BundleWatcher bundleWatcher;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<String> installedDistributionDependencies = new ArrayList<>();
    private final List<String> startedDistributionDependencies = new ArrayList<>();

    @Activate
    public void init(ComponentContext componentContext) throws Exception {
        LOGGER.info("Initializing Unomi management service");
        try {
            BundleContext bundleContext = componentContext.getBundleContext();

            UnomiSetup setup = getUnomiSetup();
            if (setup == null) {
                LOGGER.info("No previously setup distribution found");
                //We are setting a default distribution if none is set to avoid the need of calling setup manually after installation
                if (StringUtils.isNotBlank(bundleContext.getProperty("unomi.distribution"))) {
                    setup = createUnomiSetup(bundleContext.getProperty("unomi.distribution"));
                    LOGGER.info("UnomiSetup created for distribution provided from context: {}", setup.getDistribution());
                } else {
                    setup = createUnomiSetup("unomi-distribution-elasticsearch");
                    LOGGER.info("UnomiSetup created for default distribution: {}", setup.getDistribution());
                }
            }

            if (StringUtils.isNotBlank(bundleContext.getProperty("unomi.autoMigrate"))) {
                migrationService.migrateUnomi(bundleContext.getProperty("unomi.autoMigrate"), true, null);
            }

            if (StringUtils.isNotBlank(bundleContext.getProperty("unomi.autoStart")) && bundleContext.getProperty("unomi.autoStart").equals("true")) {
                LOGGER.info("Auto-starting unomi management service for unomi distribution: {}", setup.getDistribution());
                // Don't wait for completion during initialization
                startUnomi(true, false);
            }
        } catch (Exception e) {
            LOGGER.error("Error during Unomi startup:", e);
            throw e;
        }
    }

    private UnomiSetup getUnomiSetup() throws IOException {
        Configuration configuration = configurationAdmin.getConfiguration(UNOMI_SETUP_PID, "?");
        return UnomiSetup.fromDictionary(configuration.getProperties());
    }

    private UnomiSetup createUnomiSetup(String distribution) throws IOException {
        Configuration configuration = configurationAdmin.getConfiguration(UNOMI_SETUP_PID, "?");
        UnomiSetup setup = UnomiSetup.init().withDistribution(distribution);
        configuration.update(setup.toProperties());
        return setup;
    }

    @Override
    public void setupUnomiDistribution(String distribution, boolean overwrite) throws Exception {
        UnomiSetup existingSetup = getUnomiSetup();
        if (existingSetup != null && !overwrite) {
            throw new IllegalStateException("Unomi distribution is already set up with distribution: " + existingSetup.getDistribution());
        }
        createUnomiSetup(distribution);
    }

    @Override
    public void startUnomi(boolean mustStartFeatures) throws Exception {
        // Default to waiting for completion
        startUnomi(mustStartFeatures, true);
    }

    @Override
    public void startUnomi(boolean mustStartFeatures, boolean waitForCompletion) throws Exception {
        UnomiSetup setup = getUnomiSetup();
        Future<?> future = executor.submit(() -> {
            try {
                doStartUnomi(setup.getDistribution(), mustStartFeatures);
            } catch (Exception e) {
                LOGGER.error("Error starting Unomi:", e);
                throw new RuntimeException(e);
            }
        });

        if (waitForCompletion) {
            try {
                future.get(DEFAULT_TIMEOUT, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                LOGGER.error("Timeout waiting for Unomi to start", e);
                throw e;
            }
        }
    }

    private void doStartUnomi(String distribution, boolean mustStartDistribution) throws Exception {
        if (distribution == null || distribution.isEmpty()) {
            LOGGER.warn("No distribution provided, unable to start Unomi.");
            return;
        }
        try {
            Feature feature = featuresService.getFeature(distribution);
            if (feature == null) {
                LOGGER.error("Distribution feature not found: {}", distribution);
                return;
            }
            for (Dependency dependency : feature.getDependencies()) {
                if (!installedDistributionDependencies.contains(dependency.getName())) {
                    LOGGER.info("Installing distribution feature's dependency: {}", dependency.getName());
                    featuresService.installFeature(dependency.getName(), dependency.getVersion(), EnumSet.of(FeaturesService.Option.NoAutoStartBundles));
                    installedDistributionDependencies.add(dependency.getName());
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error installing distribution: {}", distribution, e);
        }

        if (mustStartDistribution) {
            LOGGER.info("Starting distribution: {}", distribution);
            for (String featureName : installedDistributionDependencies) {
                try {
                    Feature feature = featuresService.getFeature(featureName);
                    if (feature == null) {
                        LOGGER.error("Distribution feature's dependency not found: {}", featureName);
                        continue;
                    }
                    LOGGER.info("Starting dependency: {}", featureName);
                    startFeature(featureName);
                    startedDistributionDependencies.add(featureName); // Keep track of started distribution dependencies
                } catch (Exception e) {
                    LOGGER.error("Error starting feature: {}", featureName, e);
                }
            }
        }
    }

    @Override
    public void stopUnomi() throws Exception {
        // Default to waiting for completion
        stopUnomi(true);
    }

    @Override
    public void stopUnomi(boolean waitForCompletion) throws Exception {
        Future<?> future = executor.submit(() -> {
            try {
                doStopUnomi();
            } catch (Exception e) {
                LOGGER.error("Error stopping Unomi:", e);
                throw new RuntimeException(e);
            }
        });

        if (waitForCompletion) {
            try {
                future.get(DEFAULT_TIMEOUT, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                LOGGER.error("Timeout waiting for Unomi to stop", e);
                throw e;
            }
        }
    }

    private void doStopUnomi() throws Exception {
        if (startedDistributionDependencies.isEmpty()) {
            LOGGER.info("No features to stop.");
        } else {
            LOGGER.info("Stopping features in reverse order...");
            ListIterator<String> iterator = startedDistributionDependencies.listIterator(startedDistributionDependencies.size());
            while (iterator.hasPrevious()) {
                String featureName = iterator.previous();
                try {
                    LOGGER.info("Stopping feature: {}", featureName);
                    stopFeature(featureName);
                } catch (Exception e) {
                    LOGGER.error("Error stopping feature: {}", featureName, e);
                }
            }

            startedDistributionDependencies.clear(); // Clear the list after stopping all features
        }
        if (installedDistributionDependencies.isEmpty()) {
            LOGGER.info("No features to uninstall.");
        } else {
            LOGGER.info("Stopping features in reverse order...");
            ListIterator<String> iterator = installedDistributionDependencies.listIterator(installedDistributionDependencies.size());
            while (iterator.hasPrevious()) {
                String featureName = iterator.previous();
                try {
                    LOGGER.info("Uninstalling feature: {}", featureName);
                    featuresService.uninstallFeature(featureName);
                } catch (Exception e) {
                    LOGGER.error("Error uninstalling feature: {}", featureName, e);
                }
            }
            installedDistributionDependencies.clear(); // Clear the list after stopping all features
        }
    }

    private void startFeature(String featureName) throws Exception {
        Feature feature = featuresService.getFeature(featureName);
        Map<String, Map<String, FeatureState>> stateChanges = new HashMap<>();
        Map<String, FeatureState> regionChanges = new HashMap<>();
        regionChanges.put(feature.getId(), FeatureState.Started);
        stateChanges.put(FeaturesService.ROOT_REGION, regionChanges);
        featuresService.updateFeaturesState(stateChanges, EnumSet.of(FeaturesService.Option.Verbose));
    }

    private void stopFeature(String featureName) throws Exception {
        Feature feature = featuresService.getFeature(featureName);
        Map<String, Map<String, FeatureState>> stateChanges = new HashMap<>();
        Map<String, FeatureState> regionChanges = new HashMap<>();
        regionChanges.put(feature.getId(), FeatureState.Resolved);
        stateChanges.put(FeaturesService.ROOT_REGION, regionChanges);
        featuresService.updateFeaturesState(stateChanges, EnumSet.of(FeaturesService.Option.Verbose));
    }

    @Deactivate
    public void deactivate() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

}
