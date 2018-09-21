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

import org.apache.commons.lang3.StringUtils;
import org.apache.karaf.shell.commands.Argument;
import org.apache.karaf.shell.commands.Command;
import org.apache.karaf.shell.console.OsgiCommandSupport;
import org.apache.unomi.api.Event;
import org.apache.unomi.api.services.EventListenerService;
import org.apache.unomi.api.services.EventService;
import org.osgi.framework.ServiceRegistration;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

@Command(scope = "unomi", name = "event-tail", description = "This will tail all the events coming into the Apache Unomi Context Server")
public class EventTailCommand extends OsgiCommandSupport  {

    @Argument(index = 0, name = "withInternal", description = "The identifier for the event", required = false, multiValued = false)
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
    protected Object doExecute() throws Exception {
        PrintStream out = session.getConsole();
        out.flush();

        TailEventListener tailEventListener = new TailEventListener(out);

        StringBuilder headerLine = new StringBuilder();
        for (int i=0; i < columnSizes.length; i++) {
            headerLine.append(getColumn(columnSizes[i], columnHeaders[i]));
            headerLine.append("|");
        }
        System.out.println(headerLine.toString());
        System.out.println(StringUtils.repeat("-", headerLine.length()));
        ServiceRegistration<EventListenerService> tailServiceRegistration = bundleContext.registerService(EventListenerService.class, tailEventListener, new Hashtable<>());
        try {
            synchronized (this) {
                wait();
            }
            out.println("Stopping tail as log.core bundle was stopped.");
        } catch (InterruptedException e) {
            // Ignore as it will happen if the user breaks the tail using Ctrl-C
        } finally {
            tailServiceRegistration.unregister();
        }
        return null;
    }

    protected String getColumn(int columnSize, String columnContent) {
        if (columnContent == null) {
            columnContent = "null";
        }
        if (columnContent.length() == columnSize) {
            return columnContent;
        }
        if (columnContent.length() < columnSize) {
            return columnContent + StringUtils.repeat(" ", columnSize - columnContent.length());
        }
        return columnContent.substring(0, columnSize);
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
            StringBuilder eventLine = new StringBuilder();
            for (int i=0; i < columnSizes.length; i++) {
                eventLine.append(getColumn(columnSizes[i], eventInfo.get(i)));
                eventLine.append("|");
            }
            System.out.println(eventLine.toString());
            return EventService.NO_CHANGE;
        }
    }
}
