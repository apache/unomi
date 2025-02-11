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
package org.apache.unomi.shell.dev.commands;

import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.unomi.api.services.ExecutionContextManager;
import org.apache.unomi.api.services.cache.MultiTypeCacheService;
import org.apache.unomi.api.services.cache.MultiTypeCacheService.CacheStatistics;
import org.apache.unomi.api.services.cache.MultiTypeCacheService.CacheStatistics.TypeStatistics;
import org.apache.unomi.api.tenants.TenantService;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@Command(scope = "unomi", name = "cache", description = "Cache management commands")
public class CacheCommands implements Action {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    @Reference
    private MultiTypeCacheService cacheService;

    @Reference
    private TenantService tenantService;

    @Reference
    private ExecutionContextManager executionContextManager;

    @Option(name = "--stats", description = "Display cache statistics", required = false)
    private boolean showStats = false;

    @Option(name = "--reset", description = "Reset statistics after displaying them", required = false)
    private boolean reset = false;

    @Option(name = "--type", description = "Filter by type", required = false)
    private String type;

    @Option(name = "--tenant", description = "Filter by tenant ID", required = false)
    private String tenantId;

    @Option(name = "--clear", description = "Clear cache for specified tenant", required = false)
    private boolean clear = false;

    @Option(name = "--inspect", description = "Inspect cache contents", required = false)
    private boolean inspect = false;

    @Option(name = "--detailed", description = "Show detailed statistics", required = false)
    private boolean detailed = false;

    @Option(name = "--watch", description = "Watch cache statistics (refresh interval in seconds)", required = false)
    private int watchInterval = 0;

    @Option(name = "--id", description = "Specific entry ID to view or remove", required = false)
    private String entryId;

    @Option(name = "--view", description = "View a specific cache entry", required = false)
    private boolean view = false;

    @Option(name = "--remove", description = "Remove a specific cache entry", required = false)
    private boolean remove = false;

    @Override
    public Object execute() throws Exception {
        if (cacheService == null) {
            System.out.println("Cache service not available");
            return null;
        }

        // Set default tenant if not specified
        if (tenantId == null) {
            tenantId = executionContextManager.getCurrentContext().getTenantId();
        }

        if (view && entryId != null) {
            viewCacheEntry();
            return null;
        }

        if (remove && entryId != null) {
            removeCacheEntry();
            return null;
        }

        if (clear) {
            clearCache();
            return null;
        }

        if (inspect) {
            inspectCache();
            return null;
        }

        if (watchInterval > 0) {
            watchStatistics();
            return null;
        }

        if (showStats || (!clear && !inspect && !view && !remove)) {
            displayStatistics();
        }

        return null;
    }

    private void viewCacheEntry() {
        if (type == null) {
            System.out.println("Please specify a type to view the entry");
            return;
        }

        try {
            Class<? extends Serializable> typeClass = (Class<? extends Serializable>) Class.forName(type);
            Map<String, ? extends Serializable> typeCache = cacheService.getTenantCache(tenantId, typeClass);

            Serializable entry = typeCache.get(entryId);
            if (entry != null) {
                System.out.println("Cache entry found:");
                System.out.println("  Tenant: " + tenantId);
                System.out.println("  Type: " + type);
                System.out.println("  ID: " + entryId);
                System.out.println("  Value: " + entry);
                // Add any additional entry details you want to display
            } else {
                System.out.println("No cache entry found for ID: " + entryId);
            }
        } catch (ClassNotFoundException e) {
            System.out.println("Invalid type specified: " + type);
        }
    }

    private void removeCacheEntry() {
        if (type == null) {
            System.out.println("Please specify a type to remove the entry");
            return;
        }

        try {
            Class<? extends Serializable> typeClass = (Class<? extends Serializable>) Class.forName(type);

            // First check if the entry exists
            Map<String, ? extends Serializable> typeCache = cacheService.getTenantCache(tenantId, typeClass);
            if (typeCache.containsKey(entryId)) {
                cacheService.remove(type, entryId, tenantId, typeClass);
                System.out.println("Successfully removed cache entry:");
                System.out.println("  Tenant: " + tenantId);
                System.out.println("  Type: " + type);
                System.out.println("  ID: " + entryId);
            } else {
                System.out.println("No cache entry found for ID: " + entryId);
            }
        } catch (ClassNotFoundException e) {
            System.out.println("Invalid type specified: " + type);
        }
    }

    private void clearCache() {
        if (tenantId != null) {
            cacheService.clear(tenantId);
            System.out.println("Cache cleared for tenant: " + tenantId);
        } else {
            System.out.println("Please specify a tenant ID to clear cache");
        }
    }

    private void inspectCache() {
        System.out.println("Cache contents for tenant: " + tenantId);
        System.out.println("Timestamp: " + DATE_FORMAT.format(new Date()));
        System.out.println("---");

        if (type != null) {
            try {
                // This is a simplified example - you would need proper type resolution
                Class<? extends Serializable> typeClass = (Class<? extends Serializable>) Class.forName(type);
                Map<String, ? extends Serializable> typeCache = cacheService.getTenantCache(tenantId, typeClass);
                System.out.println("Entries for type " + type + ": " + typeCache.size());
                if (detailed && !typeCache.isEmpty()) {
                    typeCache.forEach((key, value) -> System.out.println("  " + key + " -> " + value));
                }
            } catch (ClassNotFoundException e) {
                System.out.println("Invalid type specified: " + type);
            }
        } else {
            System.out.println("Please specify a type to inspect");
        }
    }

    private void watchStatistics() {
        System.out.println("Watching cache statistics (refresh every " + watchInterval + " seconds)");
        System.out.println("Press Ctrl+C to stop");

        while (true) {
            try {
                clearScreen();
                System.out.println("Cache Statistics - " + DATE_FORMAT.format(new Date()));
                displayStatistics();
                TimeUnit.SECONDS.sleep(watchInterval);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void displayStatistics() {
        CacheStatistics stats = cacheService.getStatistics();
        Map<String, TypeStatistics> allStats = stats.getAllStats();

        if (type != null) {
            TypeStatistics typeStats = allStats.get(type);
            if (typeStats == null) {
                System.out.println("No statistics available for type: " + type);
                return;
            }
            printTypeStats(type, typeStats);
        } else {
            for (Map.Entry<String, TypeStatistics> entry : allStats.entrySet()) {
                printTypeStats(entry.getKey(), entry.getValue());
                System.out.println("---");
            }
        }

        if (reset) {
            stats.reset();
            System.out.println("Statistics have been reset");
        }
    }

    private void printTypeStats(String type, TypeStatistics stats) {
        System.out.println("Statistics for type: " + type);
        System.out.println("  Hits: " + stats.getHits());
        System.out.println("  Misses: " + stats.getMisses());
        System.out.println("  Updates: " + stats.getUpdates());
        System.out.println("  Validation Failures: " + stats.getValidationFailures());
        System.out.println("  Indexing Errors: " + stats.getIndexingErrors());

        long total = stats.getHits() + stats.getMisses();
        if (total > 0) {
            double hitRatio = (double) stats.getHits() / total * 100;
            double missRatio = (double) stats.getMisses() / total * 100;
            System.out.printf("  Hit Ratio: %.2f%%\n", hitRatio);
            System.out.printf("  Miss Ratio: %.2f%%\n", missRatio);

            if (detailed) {
                System.out.printf("  Efficiency Score: %.2f\n", calculateEfficiencyScore(stats));
                System.out.printf("  Error Rate: %.2f%%\n",
                    (double)(stats.getValidationFailures() + stats.getIndexingErrors()) / total * 100);
            }
        }
    }

    private double calculateEfficiencyScore(TypeStatistics stats) {
        long total = stats.getHits() + stats.getMisses();
        if (total == 0) return 0.0;

        double hitRatio = (double) stats.getHits() / total;
        double errorRatio = (double) (stats.getValidationFailures() + stats.getIndexingErrors()) / total;

        // Score formula: (hit ratio * 100) - (error ratio * 50)
        // This gives more weight to hits while still penalizing errors
        return (hitRatio * 100) - (errorRatio * 50);
    }

    private void clearScreen() {
        System.out.print("\033[H\033[2J");
        System.out.flush();
    }
}
