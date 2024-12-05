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
import org.apache.unomi.shell.migration.MigrationService;
import org.apache.unomi.shell.services.UnomiManagementService;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;

import java.util.*;

/**
 * TODO We really should replace this with Karaf Features that we activate. We could still keep the starting mechanism
 * to work properly with the migration process but we should remove the hardcoded list of packages in this class.
 * @author dgaillard
 */
@Component(service = UnomiManagementService.class, immediate = true)
public class UnomiManagementServiceImpl implements UnomiManagementService {

    private BundleContext bundleContext;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    private MigrationService migrationService;

    private final List<String> bundleSymbolicNames = new ArrayList<>();
    private List<String> reversedBundleSymbolicNames;
    private Map<String,String> persistenceImplementations = new HashMap<>();
    private Map<String,String> persistenceConditions = new HashMap<>();
    private String selectedPersistenceImplementation = "elasticsearch";

    @Activate
    public void init(ComponentContext componentContext) throws Exception {
        this.bundleContext = componentContext.getBundleContext();
        initReversedBundleSymbolicNames();

        if (StringUtils.isNotBlank(bundleContext.getProperty("unomi.autoMigrate"))) {
            migrationService.migrateUnomi(bundleContext.getProperty("unomi.autoMigrate"), true, null);
        }

        if (StringUtils.isNotBlank(bundleContext.getProperty("unomi.autoStart")) &&
                bundleContext.getProperty("unomi.autoStart").equals("true")) {
            startUnomi(selectedPersistenceImplementation);
        }
    }

    @Override
    public void startUnomi(String selectedPersistenceImplementation) throws BundleException {
        if (selectedPersistenceImplementation != null) {
            this.selectedPersistenceImplementation = selectedPersistenceImplementation;
        }
        for (String bundleSymbolicName : bundleSymbolicNames) {
            if (bundleSymbolicName.equals("${persistenceImplementationName}")) {
                bundleSymbolicName = persistenceImplementations.get(selectedPersistenceImplementation);
            } else if (bundleSymbolicName.equals("${persistenceConditionsName}")) {
                bundleSymbolicName = persistenceConditions.get(selectedPersistenceImplementation);
                if (bundleSymbolicName == null) {
                    continue;
                }
            }
            for (Bundle bundle : bundleContext.getBundles()) {
                if (bundle.getSymbolicName().equals(bundleSymbolicName)) {
                    if (bundle.getState() == Bundle.RESOLVED) {
                        bundle.start();
                    }
                    break;
                }
            }
        }
    }

    @Override
    public void stopUnomi() throws BundleException {
        for (String bundleSymbolicName : reversedBundleSymbolicNames) {
            if (bundleSymbolicName.equals("${persistenceImplementationName}")) {
                bundleSymbolicName = persistenceImplementations.get(selectedPersistenceImplementation);
            } else if (bundleSymbolicName.equals("${persistenceConditionsName}")) {
                bundleSymbolicName = persistenceConditions.get(selectedPersistenceImplementation);
                if (bundleSymbolicName == null) {
                    continue;
                }
            }
            for (Bundle bundle : bundleContext.getBundles()) {
                if (bundle.getSymbolicName().equals(bundleSymbolicName)) {
                    if (bundle.getState() == Bundle.ACTIVE) {
                        bundle.stop();
                    }
                    break;
                }
            }
        }
    }

    public void initReversedBundleSymbolicNames() {
        persistenceImplementations.clear();
        persistenceImplementations.put("elasticsearch", "org.apache.unomi.persistence-elasticsearch-core");
        persistenceImplementations.put("opensearch", "org.apache.unomi.persistence-opensearch-core");
        persistenceConditions.clear();
        persistenceConditions.put("opensearch", "org.apache.unomi.persistence-opensearch-conditions");
        bundleSymbolicNames.clear();
        bundleSymbolicNames.add("org.apache.unomi.lifecycle-watcher");
        bundleSymbolicNames.add("org.apache.unomi.api");
        bundleSymbolicNames.add("org.apache.unomi.common");
        bundleSymbolicNames.add("org.apache.unomi.scripting");
        bundleSymbolicNames.add("org.apache.unomi.metrics");
        bundleSymbolicNames.add("org.apache.unomi.persistence-spi");
        bundleSymbolicNames.add("${persistenceImplementationName}");
        bundleSymbolicNames.add("${persistenceConditionsName}");
        bundleSymbolicNames.add("org.apache.unomi.services");
        bundleSymbolicNames.add("org.apache.unomi.cxs-lists-extension-services");
        bundleSymbolicNames.add("org.apache.unomi.cxs-lists-extension-rest");
        bundleSymbolicNames.add("org.apache.unomi.cxs-geonames-services");
        bundleSymbolicNames.add("org.apache.unomi.cxs-geonames-rest");
        bundleSymbolicNames.add("org.apache.unomi.cxs-privacy-extension-services");
        bundleSymbolicNames.add("org.apache.unomi.cxs-privacy-extension-rest");
        bundleSymbolicNames.add("org.apache.unomi.json-schema-services");
        bundleSymbolicNames.add("org.apache.unomi.json-schema-rest");
        bundleSymbolicNames.add("org.apache.unomi.rest");
        bundleSymbolicNames.add("org.apache.unomi.wab");
        bundleSymbolicNames.add("org.apache.unomi.plugins-base");
        bundleSymbolicNames.add("org.apache.unomi.plugins-request");
        bundleSymbolicNames.add("org.apache.unomi.plugins-mail");
        bundleSymbolicNames.add("org.apache.unomi.plugins-optimization-test");
        bundleSymbolicNames.add("org.apache.unomi.cxs-lists-extension-actions");
        bundleSymbolicNames.add("org.apache.unomi.router-api");
        bundleSymbolicNames.add("org.apache.unomi.router-core");
        bundleSymbolicNames.add("org.apache.unomi.router-service");
        bundleSymbolicNames.add("org.apache.unomi.router-rest");
        bundleSymbolicNames.add("org.apache.unomi.shell-dev-commands");
        bundleSymbolicNames.add("org.apache.unomi.web-tracker-wab");
        bundleSymbolicNames.add("org.apache.unomi.groovy-actions-services");
        bundleSymbolicNames.add("org.apache.unomi.groovy-actions-rest");

        if (reversedBundleSymbolicNames == null || reversedBundleSymbolicNames.isEmpty()) {
            this.reversedBundleSymbolicNames = new ArrayList<>();
            reversedBundleSymbolicNames.addAll(bundleSymbolicNames);
            Collections.reverse(reversedBundleSymbolicNames);
        }
    }
}
