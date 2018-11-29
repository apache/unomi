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
import org.apache.unomi.api.Event;
import org.apache.unomi.api.rules.Rule;
import org.apache.unomi.api.services.RuleListenerService;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 * This command will list all the rules executions in the shell console.
 */
@Command(scope = "unomi", name = "rule-tail", description = "This will tail all the rules executed in the Apache Unomi Context Server")
public class RuleTailCommand extends TailCommandSupport {

    int[] columnSizes = new int[] { 36, 36, 14, 36, 29, 15, 5 };
    String[] columnHeaders = new String[] {
            "Rule ID",
            "Rule Name",
            "Event Type",
            "Session",
            "Profile",
            "Timestamp",
            "Scope",
    };

    @Override
    public int[] getColumnSizes() {
        return columnSizes;
    }

    @Override
    public String[] getColumnHeaders() {
        return columnHeaders;
    }

    @Override
    public Object getListener() {
        return new TailRuleListener(session.getConsole());
    }

    class TailRuleListener implements RuleListenerService {

        PrintStream out;

        public TailRuleListener(PrintStream out) {
            this.out = out;
        }

        @Override
        public void onEvaluate(Rule rule, Event event) {
            // this method is not used by this command
        }

        @Override
        public void onAlreadyRaised(AlreadyRaisedFor alreadyRaisedFor, Rule rule, Event event) {
            // not displayed using this command, see the rule watch command instead.
        }

        @Override
        public void onExecuteActions(Rule rule, Event event) {
            List<String> ruleExecutionInfo = new ArrayList<>();
            ruleExecutionInfo.add(rule.getItemId());
            ruleExecutionInfo.add(rule.getMetadata().getName());
            ruleExecutionInfo.add(event.getEventType());
            ruleExecutionInfo.add(event.getSessionId());
            ruleExecutionInfo.add(event.getProfileId());
            ruleExecutionInfo.add(event.getTimeStamp().toString());
            ruleExecutionInfo.add(event.getScope());
            outputLine(out, ruleExecutionInfo);
        }
    }
}
