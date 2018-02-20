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
package org.apache.unomi.shell.commands;

import org.apache.karaf.shell.commands.Command;
import org.apache.karaf.shell.console.OsgiCommandSupport;
import org.apache.unomi.api.services.RulesService;

@Command(scope = "rule", name = "reset-stats", description = "This command will reset the rule statistics")
public class RuleResetStatsCommand extends OsgiCommandSupport {

    private RulesService rulesService;

    public void setRulesService(RulesService rulesService) {
        this.rulesService = rulesService;
    }

    @Override
    protected Object doExecute() throws Exception {
        rulesService.resetAllRuleStatistics();
        System.out.println("Rule statistics successfully reset.");
        return null;
    }
}
