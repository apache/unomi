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
package org.apache.unomi.lifecycle;

import org.osgi.framework.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.*;

/**
 * This class listens to the global Apache Unomi bundle lifecycle, to provide statistics and state of the overall
 * system. It notably displays messages for successfull or unsuccessfull startups as well as startup times.
 */
public class BundleWatcher implements SynchronousBundleListener, ServiceListener {

    private static final Logger logger = LoggerFactory.getLogger(BundleWatcher.class.getName());

    private long startupTime;
    private Map<String,Long> bundleStartupTimes = new LinkedHashMap<>();
    private long unomiStartedBundleCount = 0;
    private long requiredStartedBundleCount;

    private String requiredServices;
    private Set<Filter> requiredServicesFilters = new LinkedHashSet<>();
    private long matchedRequiredServicesCount = 0;

    private BundleContext bundleContext;
    private boolean startupMessageAlreadyDisplayed = false;
    private boolean shutdownMessageAlreadyDisplayed = false;
    private List<String> logoLines = new ArrayList<>();

    public void setRequiredStartedBundleCount(long requiredStartedBundleCount) {
        this.requiredStartedBundleCount = requiredStartedBundleCount;
    }

    public void setRequiredServices(String requiredServices) {
        this.requiredServices = requiredServices;
        String[] requiredServiceArray = requiredServices.split(",");
        requiredServicesFilters.clear();
        for (String requiredService : requiredServiceArray) {
            try {
                requiredServicesFilters.add(bundleContext.createFilter(requiredService.trim()));
            } catch (InvalidSyntaxException e) {
                logger.error("Error creating require service filter {}", requiredService.trim(), e);
            }
        }
    }

    public void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    public void init() {
        bundleContext.addBundleListener(this);
        bundleContext.addServiceListener(this);
        loadLogo();
        startupTime = System.currentTimeMillis();
        System.out.println("Initializing Unomi...");
        logger.info("Bundle watcher initialized.");
    }

    public void destroy() {
        bundleContext.removeServiceListener(this);
        bundleContext.removeBundleListener(this);
        logger.info("Bundle watcher shutdown.");
    }

    @Override
    public void bundleChanged(BundleEvent event) {
        switch (event.getType()) {
            case BundleEvent.STARTING:
                break;
            case BundleEvent.STARTED:
                if (event.getBundle().getSymbolicName().startsWith("org.apache.unomi")) {
                    unomiStartedBundleCount++;
                    checkStartupComplete();
                }
                break;
            case BundleEvent.STOPPING:
                break;
            case BundleEvent.STOPPED:
                if (event.getBundle().getSymbolicName().startsWith("org.apache.unomi")) {
                    unomiStartedBundleCount--;
                }
                break;
        }
    }

    @Override
    public void serviceChanged(ServiceEvent event) {
        ServiceReference serviceReference = event.getServiceReference();
        if (serviceReference == null) {
            return;
        }
        switch (event.getType()) {
            case ServiceEvent.REGISTERED:
                addStartedService(serviceReference);
                checkStartupComplete();
                break;
            case ServiceEvent.MODIFIED:
                break;
            case ServiceEvent.UNREGISTERING:
                removeStartedService(serviceReference);
                break;
        }
    }

    private void addStartedService(ServiceReference serviceReference) {
        for (Filter requiredService : requiredServicesFilters) {
            if (requiredService.match(serviceReference)) {
                matchedRequiredServicesCount++;
            }
        }
    }

    private void removeStartedService(ServiceReference serviceReference) {
        for (Filter requiredService : requiredServicesFilters) {
            if (requiredService.match(serviceReference)) {
                matchedRequiredServicesCount--;
                if (!shutdownMessageAlreadyDisplayed) {
                    System.out.println("Apache Unomi shutting down...");
                    logger.info("Apache Unomi no longer available, as required service {} is shutdown !", serviceReference);
                    shutdownMessageAlreadyDisplayed = true;
                }
                startupMessageAlreadyDisplayed = false;
            }
        }
    }

    private void checkStartupComplete() {
        if (!isStartupComplete()) {
            return;
        }
        if (!startupMessageAlreadyDisplayed) {
            long totalStartupTime = System.currentTimeMillis() - startupTime;
            if (logoLines.size() > 0) {
                for (String logoLine : logoLines) {
                    System.out.println(logoLine);
                }
            }
            String buildNumber = "n/a";
            if (bundleContext.getBundle().getHeaders().get("Implementation-Build") != null) {
                buildNumber = bundleContext.getBundle().getHeaders().get("Implementation-Build");
            }
            String timestamp = "n/a";
            if (bundleContext.getBundle().getHeaders().get("Implementation-TimeStamp") != null) {
                timestamp = bundleContext.getBundle().getHeaders().get("Implementation-TimeStamp");
            }
            String versionMessage = "  " + bundleContext.getBundle().getVersion().toString() + "  Build:" + buildNumber + "  Timestamp:" + timestamp;
            System.out.println(versionMessage);
            System.out.println("--------------------------------------------------------------------------");
            System.out.println("Successfully started " + unomiStartedBundleCount + " bundles and " + matchedRequiredServicesCount + " required services in " + totalStartupTime + " ms");
            logger.info("Apache Unomi version: " + versionMessage);
            logger.info("Apache Unomi successfully started {} bundles and {} required services in {} ms", unomiStartedBundleCount, matchedRequiredServicesCount, totalStartupTime);
            startupMessageAlreadyDisplayed = true;
            shutdownMessageAlreadyDisplayed = false;
        }
    }

    private boolean isStartupComplete() {
        if (unomiStartedBundleCount < requiredStartedBundleCount) {
            return false;
        }
        if (matchedRequiredServicesCount < requiredServicesFilters.size()) {
            return false;
        }
        return true;
    }

    private void loadLogo() {
        URL logoURL = bundleContext.getBundle().getResource("logo.txt");
        if (logoURL != null) {
            BufferedReader bufferedReader = null;
            try {
                bufferedReader = new BufferedReader(new InputStreamReader(logoURL.openStream()));
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    if (!line.trim().startsWith("#")) {
                        logoLines.add(line);
                    }
                }
            } catch (IOException e) {
                logger.error("Error loading logo lines", e);
            } finally {
                if (bufferedReader != null) {
                    try {
                        bufferedReader.close();
                    } catch (IOException e) {
                    }
                }
            }
        }
    }

}
