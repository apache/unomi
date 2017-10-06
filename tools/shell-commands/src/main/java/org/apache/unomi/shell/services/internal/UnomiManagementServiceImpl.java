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
import org.apache.unomi.shell.services.UnomiManagementService;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author dgaillard
 */
public class UnomiManagementServiceImpl implements UnomiManagementService {

    private BundleContext bundleContext;
    private List<String> bundleSymbolicNames;
    private List<String> reversedBundleSymbolicNames;

    public void init() throws BundleException {
        initReversedBundleSymbolicNames();
        if (StringUtils.isNotBlank(bundleContext.getProperty("unomi.autoStart")) && bundleContext.getProperty("unomi.autoStart").equals("true")) {
            startUnomi();
        }
    }

    @Override
    public void startUnomi() throws BundleException {
        for (String bundleSymbolicName : bundleSymbolicNames) {
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

    public void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    public void setBundleSymbolicNames(List<String> bundleSymbolicNames) {
        this.bundleSymbolicNames = bundleSymbolicNames;
    }

    public void initReversedBundleSymbolicNames() {
        if (reversedBundleSymbolicNames == null || reversedBundleSymbolicNames.isEmpty()) {
            this.reversedBundleSymbolicNames = new ArrayList<>();
            reversedBundleSymbolicNames.addAll(bundleSymbolicNames);
            Collections.reverse(reversedBundleSymbolicNames);
        }
    }
}
