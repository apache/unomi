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

import org.apache.karaf.shell.commands.Argument;
import org.apache.karaf.shell.commands.Command;
import org.apache.karaf.shell.console.OsgiCommandSupport;
import org.apache.unomi.api.rules.Rule;
import org.apache.unomi.api.services.RulesService;
import org.apache.unomi.persistence.spi.CustomObjectMapper;

@Command(scope = "unomi", name = "rule-view", description = "This will allows to view a rule in the Apache Unomi Context Server")
public class RuleViewcommand extends OsgiCommandSupport {

    private RulesService rulesService;

    @Argument(index = 0, name = "rule", description = "The identifier for the rule", required = true, multiValued = false)
    String ruleIdentifier;

    public void setRulesService(RulesService rulesService) {
        this.rulesService = rulesService;
    }

    @Override
    protected Object doExecute() throws Exception {
        Rule rule = rulesService.getRule(ruleIdentifier);
        if (rule == null) {
            System.out.println("Couldn't find a rule with id=" + ruleIdentifier);
            return null;
        }
        String jsonRule = CustomObjectMapper.getObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(rule);
        System.out.println(jsonRule);
        return null;
    }
}
