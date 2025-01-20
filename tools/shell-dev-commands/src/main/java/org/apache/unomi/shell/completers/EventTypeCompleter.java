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
package org.apache.unomi.shell.completers;

import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.karaf.shell.api.console.CommandLine;
import org.apache.karaf.shell.api.console.Completer;
import org.apache.karaf.shell.api.console.Session;
import org.apache.karaf.shell.support.completers.StringsCompleter;
import org.apache.unomi.api.services.EventService;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class EventTypeCompleter implements Completer {

    @Reference
    private EventService eventService;

    @Override
    public int complete(Session session, CommandLine commandLine, List<String> candidates) {
        StringsCompleter delegate = new StringsCompleter();
        
        // Add common event types
        Set<String> eventTypes = new HashSet<>();
        eventTypes.add("view");
        eventTypes.add("login");
        eventTypes.add("sessionCreated");
        eventTypes.add("profileUpdated");
        eventTypes.add("sessionReassigned");
        eventTypes.add("updateProperties");
        eventTypes.add("formSubmitted");
        eventTypes.add("click");
        eventTypes.add("download");
        eventTypes.add("search");
        eventTypes.add("videoStarted");
        eventTypes.add("videoCompleted");
        
        // Add event types to completer
        for (String eventType : eventTypes) {
            delegate.getStrings().add(eventType);
        }
        
        return delegate.complete(session, commandLine, candidates);
    }
} 