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

import org.apache.unomi.api.Event;
import org.apache.unomi.api.rules.Rule;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for common tail command functionality.
 */
public final class TailCommandUtils {

    private TailCommandUtils() {
        // Utility class - prevent instantiation
    }

    /**
     * Extract event information into a list of strings for display.
     * 
     * @param event the event to extract information from
     * @return list of event information strings
     */
    public static List<String> extractEventInfo(Event event) {
        List<String> eventInfo = new ArrayList<>();
        eventInfo.add(event.getItemId());
        eventInfo.add(event.getEventType());
        eventInfo.add(event.getSessionId());
        eventInfo.add(event.getProfileId());
        eventInfo.add(event.getTimeStamp().toString());
        eventInfo.add(event.getScope());
        eventInfo.add(Boolean.toString(event.isPersistent()));
        return eventInfo;
    }

    /**
     * Extract rule execution information into a list of strings for display.
     * 
     * @param rule the rule to extract information from
     * @param event the event associated with the rule execution
     * @return list of rule execution information strings
     */
    public static List<String> extractRuleExecutionInfo(Rule rule, Event event) {
        List<String> ruleExecutionInfo = new ArrayList<>();
        ruleExecutionInfo.add(rule.getItemId());
        ruleExecutionInfo.add(rule.getMetadata().getName());
        ruleExecutionInfo.add(event.getEventType());
        ruleExecutionInfo.add(event.getSessionId());
        ruleExecutionInfo.add(event.getProfileId());
        ruleExecutionInfo.add(event.getTimeStamp().toString());
        ruleExecutionInfo.add(event.getScope());
        return ruleExecutionInfo;
    }

    /**
     * Extract rule execution information with status into a list of strings for display.
     * 
     * @param rule the rule to extract information from
     * @param event the event associated with the rule execution
     * @param status the status of the rule execution (e.g., "EVALUATE", "EXECUTE", "AR ...")
     * @return list of rule execution information strings with status as first element
     */
    public static List<String> extractRuleExecutionInfoWithStatus(Rule rule, Event event, String status) {
        List<String> ruleExecutionInfo = new ArrayList<>();
        ruleExecutionInfo.add(status);
        ruleExecutionInfo.add(rule.getItemId());
        ruleExecutionInfo.add(rule.getMetadata().getName());
        ruleExecutionInfo.add(event.getEventType());
        ruleExecutionInfo.add(event.getSessionId());
        ruleExecutionInfo.add(event.getProfileId());
        ruleExecutionInfo.add(event.getTimeStamp().toString());
        ruleExecutionInfo.add(event.getScope());
        return ruleExecutionInfo;
    }
}
