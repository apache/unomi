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
        String output = executeCommandAndGetOutput("unomi:list-invalid-objects");
        // Should show summary or indicate no invalid objects
        assertContainsAny(output, new String[]{
            "Invalid Objects Summary",
            "Total invalid objects:",
            "No invalid objects found"
        }, "Should show invalid objects summary or indicate none found");
        
        // If summary is shown, verify it contains a numeric count
        if (output.contains("Total invalid objects:")) {
            validateNumericValuesInOutput(output, new String[]{"Total invalid objects:"}, false);
        }
        
        // If table is shown, verify structure
        if (output.contains("Object Type") && output.contains("Object ID")) {
            validateTableHeaders(output, new String[]{"Object Type", "Object ID"});
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
