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

import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.Filter;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.SynchronousBundleListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * This class listens to the global Apache Unomi bundle lifecycle, to provide statistics and state of the overall
 * system. It notably displays messages for successfull or unsuccessfull startups as well as startup times.
 */
public class BundleWatcher implements SynchronousBundleListener, ServiceListener {

    private static final Logger logger = LoggerFactory.getLogger(BundleWatcher.class.getName());

    private long startupTime;
    private Map<String, Boolean> requiredBundles;

    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> scheduledFuture;

    private String requiredServices;
    private Set<Filter> requiredServicesFilters = new LinkedHashSet<>();
    private long matchedRequiredServicesCount = 0;

    private BundleContext bundleContext;
    private boolean startupMessageAlreadyDisplayed = false;
    private boolean shutdownMessageAlreadyDisplayed = false;
    private List<String> logoLines = new ArrayList<>();

    private Integer checkStartupStateRefreshInterval = 60;

    public void setRequiredBundles(Map<String, Boolean> requiredBundles) {
        this.requiredBundles = requiredBundles;
    }

    public void setCheckStartupStateRefreshInterval(Integer checkStartupStateRefreshInterval) {
        this.checkStartupStateRefreshInterval = checkStartupStateRefreshInterval;
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
        scheduler = Executors.newSingleThreadScheduledExecutor();
        bundleContext.addBundleListener(this);
        bundleContext.addServiceListener(this);
        loadLogo();
        startupTime = System.currentTimeMillis();
        System.out.println("Initializing Unomi...");
        logger.info("Bundle watcher initialized.");
    }

    private boolean allBundleStarted() {
        return getInactiveBundles().isEmpty();
    }

    private void displayLogsForInactiveBundles() {
        getInactiveBundles().forEach(inactiveBundle -> logger
                .warn("The bundle {} is in not active, some errors could happen when using the application", inactiveBundle));
    }

    private List<String> getInactiveBundles() {
        return requiredBundles.entrySet().stream().filter(entry -> !entry.getValue()).map(Map.Entry::getKey).collect(Collectors.toList());

    }

    public void destroy() {
        bundleContext.removeServiceListener(this);
        bundleContext.removeBundleListener(this);
        if (scheduledFuture != null) {
            scheduledFuture.cancel(true);
        }
        logger.info("Bundle watcher shutdown.");
    }

    @Override
    public void bundleChanged(BundleEvent event) {
        switch (event.getType()) {
            case BundleEvent.STARTING:
                break;
            case BundleEvent.STARTED:
                if (event.getBundle().getSymbolicName().startsWith("org.apache.unomi")) {
                    requiredBundles.put(event.getBundle().getSymbolicName(), true);
                    checkStartupComplete();
                }
                break;
            case BundleEvent.STOPPING:
                break;
            case BundleEvent.STOPPED:
                if (event.getBundle().getSymbolicName().startsWith("org.apache.unomi")) {
                    requiredBundles.put(event.getBundle().getSymbolicName(), false);
                }
                break;
            default:
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
            if (scheduledFuture == null || scheduledFuture.isCancelled()) {
                TimerTask task = new TimerTask() {
                    @Override
                    public void run() {
                        displayLogsForInactiveBundles();
                        checkStartupComplete();
                    }
                };
                scheduledFuture = scheduler.scheduleWithFixedDelay(task, checkStartupStateRefreshInterval, checkStartupStateRefreshInterval, TimeUnit.SECONDS);
            }
            return;
        }
        if (scheduledFuture != null) {
            scheduledFuture.cancel(true);
            scheduledFuture = null;
        }
        if (!startupMessageAlreadyDisplayed) {
            long totalStartupTime = System.currentTimeMillis() - startupTime;
            if (!logoLines.isEmpty()) {
                logoLines.forEach(System.out::println);
            }
            String buildNumber = "n/a";
            if (bundleContext.getBundle().getHeaders().get("Implementation-Build") != null) {
                buildNumber = bundleContext.getBundle().getHeaders().get("Implementation-Build");
            }
            String timestamp = "n/a";
            if (bundleContext.getBundle().getHeaders().get("Implementation-TimeStamp") != null) {
                timestamp = bundleContext.getBundle().getHeaders().get("Implementation-TimeStamp");
            }
            String versionMessage =
                    "  " + bundleContext.getBundle().getVersion().toString() + "  Build:" + buildNumber + "  Timestamp:" + timestamp;
            System.out.println(versionMessage);
            System.out.println("--------------------------------------------------------------------------");
            System.out.println(
                    "Successfully started " + requiredBundles.size() + " bundles and " + requiredServicesFilters.size() + " " + "required "
                            + "services in " + totalStartupTime + " ms");
            logger.info("Apache Unomi version: {}", versionMessage);
            logger.info("Apache Unomi successfully started {} bundles and {} required services in {} ms", requiredBundles.size(),
                    requiredServicesFilters.size(), totalStartupTime);
            startupMessageAlreadyDisplayed = true;
            shutdownMessageAlreadyDisplayed = false;
        }
    }

    public boolean isStartupComplete() {
        return allBundleStarted() && matchedRequiredServicesCount == requiredServicesFilters.size();
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
