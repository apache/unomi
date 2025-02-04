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

import org.apache.commons.lang3.StringUtils;
import org.apache.unomi.api.ServerInfo;
import org.osgi.framework.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * This class listens to the global Apache Unomi bundle lifecycle, to provide statistics and state of the overall
 * system. It notably displays messages for successfull or unsuccessfull startups as well as startup times.
 */
public class BundleWatcherImpl implements SynchronousBundleListener, ServiceListener, BundleWatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(BundleWatcherImpl.class.getName());

    private long startupTime;
    private Map<String, Boolean> requiredBundles = new ConcurrentHashMap<>();
    private Map<String, Boolean> requiredBundlesFromFeatures = new ConcurrentHashMap<>();

    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> scheduledFuture;

    private String requiredServices;
    private Set<Filter> requiredServicesFilters = new LinkedHashSet<>();
    private long matchedRequiredServicesCount = 0;

    private BundleContext bundleContext;
    private boolean startupMessageAlreadyDisplayed = false;
    private boolean shutdownMessageAlreadyDisplayed = false;

    private Integer checkStartupStateRefreshInterval = 60;

    private List<ServerInfo> serverInfos = new ArrayList<>();

    public void setRequiredBundles(Map<String, Boolean> requiredBundles) {
        this.requiredBundles = new ConcurrentHashMap<>(requiredBundles);
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
                LOGGER.error("Error creating require service filter {}", requiredService.trim(), e);
            }
        }
    }

    public void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    public void init() {
        scheduler = Executors.newSingleThreadScheduledExecutor();
        checkExistingBundles();
        bundleContext.addBundleListener(this);
        bundleContext.addServiceListener(this);
        startupTime = System.currentTimeMillis();
        System.out.println("Initializing Unomi...");
        LOGGER.info("Bundle watcher initialized.");
    }

    @Override
    public List<ServerInfo> getServerInfos() {
        return serverInfos;
    }

    private boolean allBundleStarted() {
        return getInactiveBundles(requiredBundles).isEmpty();
    }

    @Override
    public boolean allAdditionalBundleStarted() {
        return getInactiveBundles(requiredBundlesFromFeatures).isEmpty();
    }

    private void displayLogsForInactiveBundles(Map<String, Boolean> bundles) {
        getInactiveBundles(bundles).forEach(inactiveBundle -> LOGGER
                .warn("The bundle {} is in not active, some errors could happen when using the application", inactiveBundle));
    }

    private List<String> getInactiveBundles(Map<String, Boolean> bundles) {
        return bundles.entrySet().stream().filter(entry -> !entry.getValue()).map(Map.Entry::getKey).collect(Collectors.toList());
    }

    public void destroy() {
        bundleContext.removeServiceListener(this);
        bundleContext.removeBundleListener(this);
        if (scheduledFuture != null) {
            scheduledFuture.cancel(true);
        }
        LOGGER.info("Bundle watcher shutdown.");
    }

    public void checkExistingBundles() {
        serverInfos.clear();
        serverInfos.add(getBundleServerInfo(bundleContext.getBundle())); // make sure the first server info is the default one
        for (Bundle bundle : bundleContext.getBundles()) {
            checkInBundlesList(bundle, requiredBundles);
            checkInBundlesList(bundle, requiredBundlesFromFeatures);

            if (!bundle.getSymbolicName().equals(bundleContext.getBundle().getSymbolicName())) {
                ServerInfo serverInfo = getBundleServerInfo(bundle);
                if (serverInfo != null) {
                    serverInfos.add(serverInfo);
                }
            }
        }
        checkStartupComplete();
    }

    private void checkInBundlesList(Bundle bundle, Map<String, Boolean> bundles) {
        if (bundle.getSymbolicName().startsWith("org.apache.unomi") && bundles.containsKey(bundle.getSymbolicName())) {
            if (bundle.getState() == Bundle.ACTIVE) {
                bundles.put(bundle.getSymbolicName(), true);
            } else {
                bundles.put(bundle.getSymbolicName(), false);
            }
        }
    }

    private void managedBundleEvent(Bundle bundle, Map<String, Boolean> bundles, Boolean start) {
        if (bundle.getSymbolicName().startsWith("org.apache.unomi") && bundles.containsKey(bundle.getSymbolicName())) {
            LOGGER.info("Bundle {} was {}.", bundle.getSymbolicName(), start ? "started" : "stopped");
            bundles.put(bundle.getSymbolicName(), start);
            if (start) {
                checkStartupComplete();
            }
        }
    }

    @Override
    public void bundleChanged(BundleEvent event) {
        switch (event.getType()) {
            case BundleEvent.STARTING:
                break;
            case BundleEvent.STARTED:
                managedBundleEvent(event.getBundle(), requiredBundles, true);
                managedBundleEvent(event.getBundle(), requiredBundlesFromFeatures, true);
                break;
            case BundleEvent.STOPPING:
                break;
            case BundleEvent.STOPPED:
                managedBundleEvent(event.getBundle(), requiredBundles, false);
                managedBundleEvent(event.getBundle(), requiredBundlesFromFeatures, false);
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
                    LOGGER.info("Apache Unomi no longer available, as required service {} is shutdown !", serviceReference);
                    shutdownMessageAlreadyDisplayed = true;
                }
                startupMessageAlreadyDisplayed = false;
            }
        }
    }

    private void displayLogsForInactiveServices() {
        requiredServicesFilters.forEach(requiredServicesFilter -> {
            ServiceReference[] serviceReference = new ServiceReference[0];
            String filterToString = requiredServicesFilter.toString();
            try {
                serviceReference = bundleContext.getServiceReferences((String) null, filterToString);
            } catch (InvalidSyntaxException e) {
                LOGGER.error("Failed to get the service reference for {}", filterToString, e);
            }
            if (serviceReference == null) {
                LOGGER.warn("No service found for the filter {}, some errors could happen when using the application", filterToString);
            }
        });
    }


    private TimerTask getBundleCheckTask() {
        return new TimerTask() {
            @Override
            public void run() {
                displayLogsForInactiveBundles(requiredBundles);
                displayLogsForInactiveServices();
                checkStartupComplete();
            }
        };
    }

    private TimerTask getAdditionalBundleCheckTask() {
        return new TimerTask() {
            @Override
            public void run() {
                displayLogsForInactiveBundles(requiredBundlesFromFeatures);
                checkStartupComplete();
            }
        };
    }

    private void startScheduler(TimerTask timerTask) {
        if (scheduledFuture == null || scheduledFuture.isCancelled()) {
            scheduledFuture = scheduler
                    .scheduleWithFixedDelay(timerTask, checkStartupStateRefreshInterval, checkStartupStateRefreshInterval,
                            TimeUnit.SECONDS);
        }
    }

    private void destroyScheduler() {
        scheduledFuture.cancel(true);
        scheduledFuture = null;
    }

    private void checkStartupComplete() {
        if (!isStartupComplete()) {
            startScheduler(getBundleCheckTask());
            return;
        }
        if (scheduledFuture != null) {
            destroyScheduler();
        }
        if (!allAdditionalBundleStarted()) {
            startScheduler(getAdditionalBundleCheckTask());
            return;
        }
        if (scheduledFuture != null) {
            destroyScheduler();
        }
        if (!startupMessageAlreadyDisplayed) {
            long totalStartupTime = System.currentTimeMillis() - startupTime;

            List<String> logoLines = serverInfos.get(serverInfos.size() - 1).getLogoLines();
            if (logoLines != null && !logoLines.isEmpty()) {
                logoLines.forEach(System.out::println);
            }
            System.out.println("--------------------------------------------------------------------------------------------");
            serverInfos.forEach(serverInfo -> {
                String versionMessage = MessageFormat.format(" {0} {1} ({2,date,yyyy-MM-dd HH:mm:ssZ} // {3} // {4} // {5}) ",
                        StringUtils.rightPad(serverInfo.getServerIdentifier(), 12, " "), serverInfo.getServerVersion(),
                        serverInfo.getServerBuildDate(), serverInfo.getServerTimestamp(), serverInfo.getServerScmBranch(),
                        serverInfo.getServerBuildNumber());
                System.out.println(versionMessage);
                LOGGER.info(versionMessage);
            });
            System.out.println("--------------------------------------------------------------------------------------------");
            System.out.println("Server successfully started " + requiredBundles.size() + " bundles and " + requiredServicesFilters.size()
                    + " required " + "services in " + totalStartupTime + " ms");
            LOGGER.info("Server successfully started {} bundles and {} required services in {} ms", requiredBundles.size(),
                    requiredServicesFilters.size(), totalStartupTime);
            startupMessageAlreadyDisplayed = true;
            shutdownMessageAlreadyDisplayed = false;
        }
    }

    @Override
    public boolean isStartupComplete() {
        return allBundleStarted() && matchedRequiredServicesCount == requiredServicesFilters.size();
    }

    private List<String> loadLogo(Bundle bundle) {
        URL logoURL = bundle.getResource("unomi-logo.txt");
        if (logoURL != null) {
            List<String> logoLines = new ArrayList<>();
            BufferedReader bufferedReader = null;
            try {
                bufferedReader = new BufferedReader(new InputStreamReader(logoURL.openStream()));
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    if (!line.trim().startsWith("#")) {
                        logoLines.add(line);
                    }
                }
                return logoLines;
            } catch (IOException e) {
                LOGGER.error("Error loading logo lines", e);
            } finally {
                if (bufferedReader != null) {
                    try {
                        bufferedReader.close();
                    } catch (IOException e) {
                    }
                }
            }
        }
        return null;
    }

    public ServerInfo getBundleServerInfo(Bundle bundle) {
        String serverIdentifier = "n/a";
        if (bundle.getHeaders().get("Implementation-ServerIdentifier") != null) {
            serverIdentifier = bundle.getHeaders().get("Implementation-ServerIdentifier");
        } else {
            return null;
        }
        List<String> logoLines = loadLogo(bundle);
        String buildNumber = "n/a";
        if (bundle.getHeaders().get("Implementation-Build") != null) {
            buildNumber = bundle.getHeaders().get("Implementation-Build");
        }
        String timestamp = "n/a";
        Date buildDate = null;
        if (bundle.getHeaders().get("Implementation-TimeStamp") != null) {
            timestamp = bundle.getHeaders().get("Implementation-TimeStamp");
            try {
                buildDate = new Date(Long.parseLong(timestamp));
            } catch (Throwable t) {
                // we simply ignore this exception and keep the timestamp as it is
            }
        }
        String scmBranch = "n/a";
        if (bundle.getHeaders().get("Implementation-ScmBranch") != null) {
            scmBranch = bundle.getHeaders().get("Implementation-ScmBranch");
        }
        if (bundle.getHeaders().get("Implementation-UnomiEventTypes") != null) {
            // to be implemented
        }
        if (bundle.getHeaders().get("Implementation-UnomiCapabilities") != null) {
            // to be implemented
        }
        ServerInfo serverInfo = new ServerInfo();
        serverInfo.setServerIdentifier(serverIdentifier);
        serverInfo.setServerVersion(bundle.getVersion().toString());
        serverInfo.setServerBuildNumber(buildNumber);
        serverInfo.setServerBuildDate(buildDate);
        serverInfo.setServerTimestamp(timestamp);
        serverInfo.setServerScmBranch(scmBranch);
        if (logoLines != null) {
            serverInfo.setLogoLines(logoLines);
        }
        return serverInfo;
    }

    @Override
    public void addRequiredBundle(String bundleName) {
        requiredBundlesFromFeatures.put(bundleName, false);
    }

    @Override
    public boolean removeRequiredBundle(String bundleName) {
        return requiredBundlesFromFeatures.remove(bundleName);
    }
}
