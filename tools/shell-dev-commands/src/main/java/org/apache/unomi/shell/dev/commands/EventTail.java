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

import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.unomi.api.Event;
import org.apache.unomi.api.services.EventListenerService;
import org.apache.unomi.api.services.EventService;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

@Command(scope = "unomi", name = "event-tail", description = "This will tail all the events coming into the Apache Unomi Context Server")
@Service
public class EventTail extends TailCommandSupport  {

    @Argument(index = 0, name = "withInternal", description = "Whether to also monitor internal events (such as profileUpdated)", required = false, multiValued = false)
    boolean withInternal = false;

    int[] columnSizes = new int[] { 36, 14, 36, 36, 29, 15, 5 };
    String[] columnHeaders = new String[] {
            "ID",
            "Type",
            "Session",
            "Profile",
            "Timestamp",
            "Scope",
            "Persistent"
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
        return new TailEventListener(session.getConsole());
    }

    class TailEventListener implements EventListenerService {

        PrintStream out;

        public TailEventListener(PrintStream out) {
            this.out = out;
        }

        @Override
        public boolean canHandle(Event event) {
            return true;
        }

        @Override
        public int onEvent(Event event) {
            if (!event.isPersistent() && !withInternal) {
                return EventService.NO_CHANGE;
            }
            List<String> eventInfo = new ArrayList<>();
            eventInfo.add(event.getItemId());
            eventInfo.add(event.getEventType());
            eventInfo.add(event.getSessionId());
            eventInfo.add(event.getProfileId());
            eventInfo.add(event.getTimeStamp().toString());
            eventInfo.add(event.getScope());
            eventInfo.add(Boolean.toString(event.isPersistent()));
            outputLine(out, eventInfo);
            return EventService.NO_CHANGE;
        }

    }
}
