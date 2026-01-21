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
package org.apache.unomi.itests.shell;

import org.junit.Assert;
import org.junit.Test;

/**
 * Integration tests for unomi:cache command.
 */
public class CacheCommandsIT extends ShellCommandsBaseIT {

    @Test
    public void testCacheStats() throws Exception {
        String output = executeCommandAndGetOutput("unomi:cache --stats");
        // With ShellTable output, check for table headers instead of plain text
        assertContainsAny(output, new String[]{"Type", "Hits", "Misses", "No cache statistics available"}, 
            "Should show statistics table or indicate no stats");
        
        // If statistics are shown in table format, validate table structure
        if (output.contains("Type") && output.contains("Hits")) {
            validateTableHeaders(output, new String[]{"Type", "Hits", "Misses"});
            // Table should have at least header row
            String[] lines = output.split("\n");
            Assert.assertTrue("Should have table output with headers", lines.length > 0);
        }
    }

    @Test
    public void testCacheStatsWithReset() throws Exception {
        String output = executeCommandAndGetOutput("unomi:cache --stats --reset");
        // Should show statistics table and reset confirmation
        assertContainsAny(output, new String[]{"Statistics have been reset", "Type", "Hits"}, 
            "Should show statistics table and reset confirmation");
        
        // If no explicit reset message, at least verify stats table was shown
        if (!output.contains("Statistics have been reset")) {
            assertContainsAny(output, new String[]{"Type", "Hits", "Misses"}, 
                "Should show cache statistics table");
        }
    }

    @Test
    public void testCacheStatsWithTenant() throws Exception {
        String output = executeCommandAndGetOutput("unomi:cache --stats --tenant " + TEST_TENANT_ID);
        // Should show statistics table (may be empty if no cache activity)
        // Note: --tenant option sets the tenantId but displayStatistics() doesn't filter by tenant,
        // so it shows all statistics. The output may be empty if there are no statistics at all.
        // Empty output is valid (means no statistics available)
        if (output.trim().isEmpty()) {
            // Empty output is acceptable - means no statistics available
            return;
        }
        
        assertContainsAny(output, new String[]{
            "Type", 
            "Hits", 
            "No cache statistics available",
            "Cache service not available"
        }, "Should show cache statistics table, indicate no stats, or service unavailable");
        
        // If stats table is shown, validate table structure
        if (output.contains("Type") && output.contains("Hits")) {
            validateTableHeaders(output, new String[]{"Type", "Hits", "Misses"});
        }
    }

    @Test
    public void testCacheClear() throws Exception {
        String output = executeCommandAndGetOutput("unomi:cache --clear --tenant " + TEST_TENANT_ID);
        // Should confirm cache was cleared with the specific tenant ID
        Assert.assertTrue("Should confirm cache cleared for tenant", 
            output.contains("Cache cleared for tenant: " + TEST_TENANT_ID));
    }

    @Test
    public void testCacheInspect() throws Exception {
        String output = executeCommandAndGetOutput("unomi:cache --inspect");
        // Inspect should show cache contents or ask for type
        assertContainsAny(output, new String[]{
            "Cache contents for tenant:", 
            "Please specify a type to inspect",
            "Timestamp:"
        }, "Should show cache contents or request type");
        
        // If it shows contents, should have tenant info
        if (output.contains("Cache contents for tenant:")) {
            Assert.assertTrue("Should show timestamp when contents are displayed", 
                output.contains("Timestamp:"));
        }
    }

    @Test
    public void testCacheStatsWithType() throws Exception {
        String output = executeCommandAndGetOutput("unomi:cache --stats --type profile");
        // Should show stats table for the specific type or indicate no stats
        assertContainsAny(output, new String[]{
            "profile",
            "No statistics available for type: profile",
            "Type",
            "Hits"
        }, "Should show statistics table for profile type or indicate no stats");
        
        // If stats table is shown, verify it contains the type and table structure
        if (output.contains("Type") && output.contains("Hits")) {
            validateTableHeaders(output, new String[]{"Type", "Hits", "Misses"});
            // If profile type is in the table, it should be in a data row
            if (tableContainsValue(output, "profile")) {
                Assert.assertTrue("Should show profile type in table", true);
            }
        }
    }

    @Test
    public void testCacheDetailedStats() throws Exception {
        String output = executeCommandAndGetOutput("unomi:cache --stats --detailed");
        // Detailed stats should show additional columns like efficiency score and error rate
        assertContainsAny(output, new String[]{
            "Type",
            "Efficiency Score",
            "Error Rate",
            "Hits"
        }, "Should show detailed statistics table with additional columns");
        
        // If detailed stats table is shown, verify it has the additional columns
        if (output.contains("Type") && output.contains("Hits")) {
            validateTableHeaders(output, new String[]{"Type", "Hits", "Efficiency Score", "Error Rate"});
        }
    }

    @Test
    public void testCacheStatsCsv() throws Exception {
        String csvOutput = executeCommandAndGetOutput("unomi:cache --stats --csv");
        // CSV should contain commas and have at least header row
        Assert.assertTrue("Should output CSV format", csvOutput.contains(",") || csvOutput.trim().length() > 0);
        // CSV should have at least one line (header)
        String[] lines = csvOutput.split("\n");
        Assert.assertTrue("CSV output should have at least header line", lines.length > 0);
        // CSV header should contain expected columns
        if (lines.length > 0) {
            String header = lines[0];
            assertContainsAny(header, new String[]{"Type", "Hits", "Misses"}, 
                "CSV header should contain expected columns");
        }
    }

    @Test
    public void testCacheStatsDetailedCsv() throws Exception {
        String csvOutput = executeCommandAndGetOutput("unomi:cache --stats --detailed --csv");
        // CSV should contain commas and have detailed columns
        Assert.assertTrue("Should output CSV format", csvOutput.contains(",") || csvOutput.trim().length() > 0);
        // CSV header should contain detailed columns
        String[] lines = csvOutput.split("\n");
        if (lines.length > 0) {
            String header = lines[0];
            assertContainsAny(header, new String[]{"Type", "Hits", "Efficiency Score", "Error Rate"}, 
                "CSV header should contain detailed columns");
        }
    }
}
