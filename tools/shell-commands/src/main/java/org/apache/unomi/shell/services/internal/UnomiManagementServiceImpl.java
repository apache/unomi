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
 *   <li>Loading distribution's feature from an environment variable, a java option or by calling the setupUnomiDistribution method.</li>
 *   <li>Starting Apache Unomi by installing and starting the configured features for a selected distribution.</li>
 *   <li>Stopping Apache Unomi by uninstalling features in reverse order to ensure proper teardown.</li>
 *   <li>Interfacing with the {@link org.apache.unomi.shell.migration.MigrationService} for migration tasks during startup.</li>
 * </ul>
 *
 * <p>The class is designed to be used within an OSGi environment. It leverages the {@link FeaturesService} to
 * manage Karaf features dynamically.</p>
 *
 * <p><b>Configuration</b></p>
 * <p>The service stores its distribution's name using the OSGi Configuration Admin under the PID <code>org.apache.unomi.setup</code>.
 * This allows the service to persist the selected distribution across restarts. The default distribution is unomi-distribution-elasticsearch</p>
 *
 * <p><b>Usage</b></p>
 * <p>This service can be controlled programmatically through its methods:</p>
 * <ul>
 *   <li>{@link #setupUnomiDistribution(String, boolean)}: Sets up the Unomi distribution's feature name.</li>
 *   <li>{@link #startUnomi(boolean)}: Installs and starts features for the configured distribution.</li>
 *   <li>{@link #stopUnomi()}: Stops and uninstalls the previously started features.</li>
 * </ul>
 *
 * <p><b>Dependencies</b></p>
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

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    private MigrationService migrationService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    private ConfigurationAdmin configurationAdmin;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    private FeaturesService featuresService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    private BundleWatcher bundleWatcher;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<String> trackedInstalledDistributionDependencies = new ArrayList<>();
    private final List<String> trackedStartedDistributionDependencies = new ArrayList<>();

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
                try {
                    Feature depFeature = featuresService.getFeature(dependency.getName(), dependency.getVersion());
                    if (depFeature != null) {
                        List<Feature> karafInstalledFeatures = Arrays.stream(featuresService.listInstalledFeatures()).toList();
                        if (!trackedInstalledDistributionDependencies.contains(dependency.getName())) {
                            Optional<Feature> karafInstalledFeature = karafInstalledFeatures.stream()
                                    .filter(f -> f.getName().equals(depFeature.getName()) && f.getVersion().equals(depFeature.getVersion())).findFirst();
                            if (karafInstalledFeature.isEmpty()) {
                                LOGGER.info("Installing distribution's dependency feature: {}", depFeature);
                                featuresService.installFeature(depFeature, EnumSet.of(FeaturesService.Option.NoAutoStartBundles));
                            } else {
                                LOGGER.info("Feature {} is already installed, skipping installation.", karafInstalledFeature.get());
                            }
                            LOGGER.info("Installing distribution feature's dependency: {}", dependency.getName());
                            featuresService.installFeature(dependency.getName(), dependency.getVersion(), EnumSet.of(FeaturesService.Option.NoAutoStartBundles));
                            trackedInstalledDistributionDependencies.add(dependency.getName());
                        }
                    } else {
                        LOGGER.error("Distribution's dependency feature not found: {}", dependency);
                    }
                } catch (Exception e) {
                    LOGGER.error("Error installing distribution's dependency feature: {}", dependency, e);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error installing distribution: {}", distribution, e);
        }

        if (mustStartDistribution) {
            LOGGER.info("Starting distribution: {}", distribution);
            for (String featureName : trackedInstalledDistributionDependencies) {
                try {
                    Feature feature = featuresService.getFeature(featureName);
                    if (feature == null) {
                        LOGGER.error("Distribution feature's dependency not found: {}", featureName);
                        continue;
                    }
                    LOGGER.info("Starting distribution's dependency feature: {}", featureName);
                    startFeature(feature.getName(), feature.getVersion());
                    trackedStartedDistributionDependencies.add(featureName); // Keep track of started distribution dependencies
                } catch (Exception e) {
                    LOGGER.error("Error starting distribution's dependency feature: {}", featureName, e);
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
        if (trackedStartedDistributionDependencies.isEmpty()) {
            LOGGER.info("No distribution's dependency features to stop.");
        } else {
            LOGGER.info("Stopping distribution's dependency features in reverse order...");
            ListIterator<String> iterator = trackedStartedDistributionDependencies.listIterator(trackedStartedDistributionDependencies.size());
            while (iterator.hasPrevious()) {
                String featureName = iterator.previous();
                try {
                    LOGGER.info("Stopping distribution's dependency feature: {}", featureName);
                    stopFeature(featureName);
                } catch (Exception e) {
                    LOGGER.error("Error stopping distribution's dependency feature: {}", featureName, e);
                }
            }

            trackedStartedDistributionDependencies.clear(); // Clear the list after stopping all features
        }
        if (trackedInstalledDistributionDependencies.isEmpty()) {
            LOGGER.info("No distribution's dependency features to uninstall.");
        } else {
            LOGGER.info("Stopping distribution's dependency features in reverse order...");
            ListIterator<String> iterator = trackedInstalledDistributionDependencies.listIterator(trackedInstalledDistributionDependencies.size());
            while (iterator.hasPrevious()) {
                String featureName = iterator.previous();
                try {
                    LOGGER.info("Uninstalling distribution's dependency feature: {}", featureName);
                    featuresService.uninstallFeature(featureName);
                } catch (Exception e) {
                    LOGGER.error("Error uninstalling distribution's dependency feature: {}", featureName, e);
                }
            }
            trackedInstalledDistributionDependencies.clear(); // Clear the list after stopping all features
        }
    }

    private void startFeature(String featureName, String version) throws Exception {
        Feature feature = featuresService.getFeature(featureName, version);
        Map<String, Map<String, FeatureState>> stateChanges = new HashMap<>();
        Map<String, FeatureState> regionChanges = new HashMap<>();
        FeatureState state = featuresService.getState(feature.getId());
        if (state != FeatureState.Started) {
            regionChanges.put(feature.getId(), FeatureState.Started);
            stateChanges.put(FeaturesService.ROOT_REGION, regionChanges);
            featuresService.updateFeaturesState(stateChanges, EnumSet.of(FeaturesService.Option.Verbose));
        }
    }

    private void stopFeature(String featureName) throws Exception {
        Feature feature = featuresService.getFeature(featureName);
        Map<String, Map<String, FeatureState>> stateChanges = new HashMap<>();
        Map<String, FeatureState> regionChanges = new HashMap<>();
        FeatureState state = featuresService.getState(feature.getId());
        if (state == FeatureState.Started) {
            regionChanges.put(feature.getId(), FeatureState.Resolved);
            stateChanges.put(FeaturesService.ROOT_REGION, regionChanges);
            featuresService.updateFeaturesState(stateChanges, EnumSet.of(FeaturesService.Option.Verbose));
        }
    }

    @Override
    public String getCurrentDistribution() throws Exception {
        UnomiSetup setup = getUnomiSetup();
        return setup != null ? setup.getDistribution() : null;
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
