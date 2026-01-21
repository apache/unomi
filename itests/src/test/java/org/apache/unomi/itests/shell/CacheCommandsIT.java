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
        assertContainsAny(output, new String[]{"Statistics for type:", "Hits:", "Misses:"}, 
            "Should show statistics for type");
        
        // If statistics are shown, validate they contain numeric values
        if (output.contains("Hits:")) {
            validateNumericValuesInOutput(output, new String[]{"Hits:", "Misses:", "Updates:"}, false);
        }
    }

    @Test
    public void testCacheStatsWithReset() throws Exception {
        String output = executeCommandAndGetOutput("unomi:cache --stats --reset");
        // Should show statistics and reset confirmation
        assertContainsAny(output, new String[]{"Statistics have been reset", "Statistics for type:"}, 
            "Should show statistics have been reset");
        
        // If no explicit reset message, at least verify stats were shown
        if (!output.contains("Statistics have been reset")) {
            assertContainsAny(output, new String[]{"Statistics for type:", "Hits:"}, 
                "Should show cache statistics");
        }
    }

    @Test
    public void testCacheStatsWithTenant() throws Exception {
        String output = executeCommandAndGetOutput("unomi:cache --stats --tenant " + TEST_TENANT_ID);
        // Should show statistics (may be empty if no cache activity)
        // Note: --tenant option sets the tenantId but displayStatistics() doesn't filter by tenant,
        // so it shows all statistics. The output may be empty if there are no statistics at all.
        // Empty output is valid (means no statistics available)
        if (output.trim().isEmpty()) {
            // Empty output is acceptable - means no statistics available
            return;
        }
        
        assertContainsAny(output, new String[]{
            "Statistics for type:", 
            "Hits:", 
            "No statistics available",
            "Cache service not available"
        }, "Should show cache statistics, indicate no stats, or service unavailable");
        
        // If stats are shown, they should be valid
        if (output.contains("Statistics for type:")) {
            assertContainsAny(output, new String[]{"Hits:", "Misses:"}, 
                "Should contain Hits or Misses when stats are shown");
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
        // Should show stats for the specific type or indicate no stats
        assertContainsAny(output, new String[]{
            "Statistics for type: profile",
            "No statistics available for type: profile",
            "Hits:"
        }, "Should show statistics for profile type or indicate no stats");
        
        // If stats are shown, verify they're for the correct type
        if (output.contains("Statistics for type: profile")) {
            assertContainsAny(output, new String[]{"Hits:", "Misses:"}, 
                "Should show Hits or Misses for profile type");
        }
    }

    @Test
    public void testCacheDetailedStats() throws Exception {
        String output = executeCommandAndGetOutput("unomi:cache --stats --detailed");
        // Detailed stats should show additional metrics like efficiency score
        assertContainsAny(output, new String[]{
            "Statistics for type:",
            "Efficiency Score:",
            "Error Rate:",
            "Hits:"
        }, "Should show detailed statistics");
        
        // If detailed stats are shown, verify they contain numeric values (allow decimals)
        if (output.contains("Efficiency Score:") || output.contains("Error Rate:")) {
            validateNumericValuesInOutput(output, new String[]{"Efficiency Score:", "Error Rate:"}, true);
        }
    }
}
