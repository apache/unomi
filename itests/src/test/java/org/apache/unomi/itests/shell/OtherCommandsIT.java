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

import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.rules.Rule;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

/**
 * Integration tests for other utility commands.
 */
public class OtherCommandsIT extends ShellCommandsBaseIT {

    @Test
    public void testRuleResetStats() throws Exception {
        String output = executeCommandAndGetOutput("unomi:rule-reset-stats");
        // Should confirm statistics were reset
        Assert.assertTrue("Should confirm rule statistics reset",
            output.contains("Rule statistics successfully reset"));
    }

    @Test
    public void testListInvalidObjects() throws Exception {
        // Seed a genuinely invalid rule (unresolvable condition type) so the command has
        // something to report - otherwise this test only ever exercises the "no invalid
        // objects found" branch and never validates the summary/table rendering.
        String ruleId = "test-invalid-rule-" + System.currentTimeMillis();
        Metadata metadata = new Metadata(ruleId);
        metadata.setName(ruleId + "_name");
        metadata.setScope("systemscope");

        Condition invalidCondition = new Condition();
        invalidCondition.setConditionTypeId("nonExistentConditionType-" + System.currentTimeMillis());

        Rule invalidRule = new Rule(metadata);
        invalidRule.setCondition(invalidCondition);
        invalidRule.setActions(Collections.emptyList());

        try {
            rulesService.setRule(invalidRule);
            keepTrying("Rule should be tracked as invalid",
                    () -> definitionsService.getTypeResolutionService().isInvalid("rules", ruleId),
                    Boolean.TRUE::equals, DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);

            String output = executeCommandAndGetOutput("unomi:list-invalid-objects");
            Assert.assertTrue("Should show invalid objects summary", output.contains("Invalid Objects Summary"));
            Assert.assertTrue("Should report at least one invalid object", output.contains("Total invalid objects:"));
            validateNumericValuesInOutput(output, new String[]{"Total invalid objects:"}, false);
            validateTableHeaders(output, new String[]{"Object Type", "Object ID"});
            Assert.assertTrue("Should list the invalid rule's id", output.contains(ruleId));
        } finally {
            rulesService.removeRule(ruleId);
            definitionsService.getTypeResolutionService().markValid("rules", ruleId);
        }
    }

    @Test
    public void testDeployDefinition() throws Exception {
        validateCommandExists("unomi:deploy-definition", "deploy", "definition");
    }

    @Test
    public void testUndeployDefinition() throws Exception {
        validateCommandExists("unomi:undeploy-definition", "undeploy", "definition");
    }
}
