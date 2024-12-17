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
import org.apache.karaf.features.Feature;
import org.apache.karaf.features.FeatureState;
import org.apache.karaf.features.FeaturesService;
import org.apache.unomi.lifecycle.BundleWatcher;
import org.apache.unomi.shell.migration.MigrationService;
import org.apache.unomi.shell.services.UnomiManagementService;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Implementation of the {@link UnomiManagementService} interface, providing functionality to manage
 * the lifecycle of Apache Unomi, including the installation and activation of features using
 * Karaf's {@link org.apache.karaf.features.FeaturesService}.
 *
 * <p>This service handles the following responsibilities:</p>
 * <ul>
 *   <li>Loading configuration from the OSGi Configuration Admin service, including persistence implementation and feature lists.</li>
 *   <li>Starting Apache Unomi by installing and starting the configured features for a selected persistence implementation.</li>
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
 *   <li>{@link #startUnomi(String, boolean)}: Installs and starts features for the specified persistence implementation.</li>
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
 */@Component(service = UnomiManagementService.class, immediate = true, configurationPid = "org.apache.unomi.start")
@Designate(ocd = UnomiManagementServiceConfiguration.class)
public class UnomiManagementServiceImpl implements UnomiManagementService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UnomiManagementServiceImpl.class.getName());

    private static final String CDP_GRAPHQL_FEATURE = "cdp-graphql-feature";

    private BundleContext bundleContext;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    private MigrationService migrationService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    private FeaturesService featuresService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    private BundleWatcher bundleWatcher;

    private Map<String, List<String>> startFeatures = new HashMap<String, List<String>>();
    private final List<String> installedFeatures = new ArrayList<>();
    private final List<String> startedFeatures = new ArrayList<>();

    private String selectedPersistenceImplementation = "elasticsearch";

    @Activate
    public void init(ComponentContext componentContext, UnomiManagementServiceConfiguration config) throws Exception {
        try {
            this.bundleContext = componentContext.getBundleContext();
            this.startFeatures = parseStartFeatures(config.startFeatures());

            if (StringUtils.isNotBlank(bundleContext.getProperty("unomi.autoMigrate"))) {
                migrationService.migrateUnomi(bundleContext.getProperty("unomi.autoMigrate"), true, null);
            }

            if (StringUtils.isNotBlank(bundleContext.getProperty("unomi.autoStart")) &&
                    bundleContext.getProperty("unomi.autoStart").equals("true")) {
                startUnomi(selectedPersistenceImplementation, true);
            }

        } catch (Exception e) {
            LOGGER.error("Error during Unomi startup when processing 'unomi.autoMigrate' or 'unomi.autoStart' properties:", e);
        }
    }

    private List<String> getAdditionalFeaturesToInstall() {
        List<String> featuresToInstall = new ArrayList<>();
        if (Boolean.parseBoolean(bundleContext.getProperty("org.apache.unomi.graphql.feature.activated"))) {
            featuresToInstall.add(CDP_GRAPHQL_FEATURE);
            bundleWatcher.addRequiredBundle("org.apache.unomi.cdp-graphql-api-impl");
            bundleWatcher.addRequiredBundle("org.apache.unomi.graphql-ui");
        }
        return featuresToInstall;
    }

    private Map<String, List<String>> parseStartFeatures(String startFeaturesConfig) {
        Map<String, List<String>> startFeatures = new HashMap<>();
        if (startFeaturesConfig == null || startFeaturesConfig.isEmpty()) {
            return startFeatures;
        }

        String[] entries = startFeaturesConfig.split(";");
        for (String entry : entries) {
            String[] parts = entry.split(":");
            if (parts.length == 2) {
                String key = parts[0].trim();
                List<String> features = new ArrayList<>(Arrays.asList(parts[1].split(",")));
                startFeatures.put(key, features);
            } else {
                LOGGER.warn("Invalid start feature entry: {}", entry);
            }
        }
        return startFeatures;
    }

    @Override
    public void startUnomi(String selectedPersistenceImplementation, boolean mustStartFeatures) throws BundleException {
        if (selectedPersistenceImplementation != null) {
            this.selectedPersistenceImplementation = selectedPersistenceImplementation;
        }
        List<String> features = startFeatures.get(selectedPersistenceImplementation);
        if (features == null || features.isEmpty()) {
            LOGGER.warn("No features configured for persistence implementation: {}", selectedPersistenceImplementation);
            return;
        }
        features.addAll(getAdditionalFeaturesToInstall());

        LOGGER.info("Installing features for persistence implementation: {}", selectedPersistenceImplementation);
        for (String featureName : features) {
            try {
                Feature feature = featuresService.getFeature(featureName);
                if (feature == null) {
                    LOGGER.error("Feature not found: {}", featureName);
                    continue;
                }

                if (!installedFeatures.contains(featureName)) {
                    LOGGER.info("Installing feature: {}", featureName);
                    featuresService.installFeature(featureName, EnumSet.of(FeaturesService.Option.NoAutoStartBundles));
                    installedFeatures.add(featureName);
                }
            } catch (Exception e) {
                LOGGER.error("Error installing feature: {}", featureName, e);
            }
        }

        if (mustStartFeatures) {
            LOGGER.info("Starting features for persistence implementation: {}", selectedPersistenceImplementation);
            for (String featureName : features) {
                try {
                    Feature feature = featuresService.getFeature(featureName);
                    if (feature == null) {
                        LOGGER.error("Feature not found: {}", featureName);
                        continue;
                    }
                    if (mustStartFeatures) {
                        LOGGER.info("Starting feature: {}", featureName);
                        startFeature(featureName);
                        startedFeatures.add(featureName); // Keep track of started features
                    }
                } catch (Exception e) {
                    LOGGER.error("Error starting feature: {}", featureName, e);
                }
            }
        }
    }

    @Override
    public void stopUnomi() throws BundleException {
        if (startedFeatures.isEmpty()) {
            LOGGER.info("No features to stop.");
        } else {
            LOGGER.info("Stopping features in reverse order...");
            ListIterator<String> iterator = startedFeatures.listIterator(startedFeatures.size());
            while (iterator.hasPrevious()) {
                String featureName = iterator.previous();
                try {
                    LOGGER.info("Stopping feature: {}", featureName);
                    stopFeature(featureName);
                } catch (Exception e) {
                    LOGGER.error("Error stopping feature: {}", featureName, e);
                }
            }

            startedFeatures.clear(); // Clear the list after stopping all features
        }
        if (installedFeatures.isEmpty()) {
            LOGGER.info("No features to uninstall.");
        } else {
            LOGGER.info("Stopping features in reverse order...");
            ListIterator<String> iterator = installedFeatures.listIterator(installedFeatures.size());
            while (iterator.hasPrevious()) {
                String featureName = iterator.previous();
                try {
                    LOGGER.info("Uninstalling feature: {}", featureName);
                    featuresService.uninstallFeature(featureName);
                } catch (Exception e) {
                    LOGGER.error("Error uninstalling feature: {}", featureName, e);
                }
            }
            installedFeatures.clear(); // Clear the list after stopping all features
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

}
