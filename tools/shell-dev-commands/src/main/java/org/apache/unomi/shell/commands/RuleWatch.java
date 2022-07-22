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

import org.apache.commons.lang3.ArrayUtils;
import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.unomi.api.Event;
import org.apache.unomi.api.rules.Rule;
import org.apache.unomi.api.services.RuleListenerService;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 * This command can watch the evaluations of the conditions as well as the executions of a specified number of rules.
 */
@Command(scope = "unomi", name = "rule-watch", description = "This will watch the specified rules evaluating and executing in the Apache Unomi Context Server")
@Service
public class RuleWatch extends TailCommandSupport {

    @Argument(index = 0, name = "ruleIds", description = "Identifier(s) of rule(s) to watch", required = true, multiValued = true)
    String[] ruleIds;

    int[] columnSizes = new int[] { 10, 36, 36, 14, 36, 29, 15, 5 };
    String[] columnHeaders = new String[] {
            "Status",
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
        return new RuleWatchListener(session.getConsole());
    }

    class RuleWatchListener implements RuleListenerService {

        PrintStream out;

        public RuleWatchListener(PrintStream out) {
            this.out = out;
        }

        @Override
        public void onEvaluate(Rule rule, Event event) {
            populateRuleInfo(rule, event, "EVALUATE");
        }


        @Override
        public void onAlreadyRaised(AlreadyRaisedFor alreadyRaisedFor, Rule rule, Event event) {
            populateRuleInfo(rule, event, "AR " + alreadyRaisedFor.toString());
        }

        @Override
        public void onExecuteActions(Rule rule, Event event) {
            populateRuleInfo(rule, event, "EXECUTE");
        }

        public void populateRuleInfo(Rule rule, Event event, String status) {
            if (!ArrayUtils.contains(ruleIds, rule.getItemId())) {
                return;
            }
            List<String> ruleExecutionInfo = new ArrayList<>();
            ruleExecutionInfo.add(status);
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
