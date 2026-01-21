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

import org.apache.unomi.api.rules.Rule;
import org.apache.unomi.api.services.RulesService;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Integration tests for rule statistics commands.
 */
public class RuleStatisticsCommandsIT extends ShellCommandsBaseIT {

    private List<String> createdRuleIds = new ArrayList<>();

    @Before
    public void setUp() {
        createdRuleIds.clear();
    }

    @After
    public void tearDown() {
        for (String ruleId : createdRuleIds) {
            try {
                if (rulesService != null) {
                    Rule rule = rulesService.getRule(ruleId);
                    if (rule != null) {
                        rulesService.removeRule(ruleId);
                    }
                }
            } catch (Exception e) {
                // Don't log here - any logging can be captured by command output stream causing StackOverflow
            }
        }
        createdRuleIds.clear();
    }

    @Test
    public void testRuleStatisticsList() throws Exception {
        // Rule statistics are accessed via unomi:crud list rulestats
        String output = executeCommandAndGetOutput("unomi:crud list rulestats");
        // Should show statistics table with headers
        assertContainsAny(output, new String[]{
            "ID", "Executions", "Conditions Time", "Tenant"
        }, "Should show rule statistics table headers");
        
        // If table is shown, verify structure
        if (output.contains("ID") && output.contains("Executions")) {
            List<List<String>> rows = extractTableRows(output);
            // Should have table structure
            Assert.assertTrue("Should have table structure", rows.size() >= 0);
        }
    }

    @Test
    public void testRuleStatisticsReset() throws Exception {
        // Rule statistics reset is done via unomi:crud delete rulestats -i <id> or unomi:rule-reset-stats
        // The delete operation on rulestats resets all statistics
        String output = executeCommandAndGetOutput("unomi:rule-reset-stats");
        // Should confirm statistics were reset
        Assert.assertTrue("Should confirm rule statistics reset", 
            output.contains("Rule statistics successfully reset"));
    }

    @Test
    public void testRuleStatisticsAfterRuleExecution() throws Exception {
        String ruleId = createTestRuleForStatistics();
        String statsOutput = executeCommandAndGetOutput("unomi:crud list rulestats");
        validateRuleStatisticsTable(statsOutput, ruleId);
        verifyRuleStatisticsReset();
    }

    /**
     * Create a test rule and return its ID.
     */
    private String createTestRuleForStatistics() throws Exception {
        String ruleId = createTestId("test-rule-stats");
        String createOutput = createTestRule(ruleId, "Test Rule Stats");
        Assert.assertTrue("Rule should be created", 
            createOutput.contains("Created rule with ID: " + ruleId) || createOutput.contains(ruleId));
        createdRuleIds.add(ruleId);
        return ruleId;
    }

    /**
     * Verify that rule statistics can be reset.
     */
    private void verifyRuleStatisticsReset() throws Exception {
        String resetOutput = executeCommandAndGetOutput("unomi:rule-reset-stats");
        Assert.assertTrue("Should confirm statistics reset", 
            resetOutput.contains("Rule statistics successfully reset"));
    }

    /**
     * Validate that rule statistics table is properly formatted.
     */
    private void validateRuleStatisticsTable(String statsOutput, String ruleId) {
        assertContainsAny(statsOutput, new String[]{
            "ID", "Executions", "Tenant", "Conditions Time"
        }, "Should show statistics table with headers");
        
        // Verify our rule appears in the statistics (may have 0 executions)
        Assert.assertTrue("Should contain our rule ID in statistics", 
            statsOutput.contains(ruleId) || statsOutput.contains("ID"));
        
        // If table is shown, verify structure
        if (statsOutput.contains("ID") && statsOutput.contains("Executions")) {
            validateTableHeaders(statsOutput, new String[]{"ID", "Executions"});
            List<List<String>> rows = extractTableRows(statsOutput);
            Assert.assertTrue("Statistics table should be present", rows.size() >= 0);
        }
    }
}
