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

import org.apache.commons.lang3.StringUtils;
import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.console.Session;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

public abstract class TailCommandSupport implements Action {

    public abstract int[] getColumnSizes();

    public abstract String[] getColumnHeaders();

    @Reference
    Session session;

    @Reference
    BundleContext bundleContext;

    public void outputHeaders(PrintStream out) {
        StringBuilder headerLine = new StringBuilder();
        int[] columnSizes = getColumnSizes();
        String[] columnHeaders = getColumnHeaders();
        for (int i=0; i < columnSizes.length; i++) {
            headerLine.append(getColumn(columnSizes[i], columnHeaders[i]));
            headerLine.append("|");
        }
        out.println(headerLine.toString());
        out.println(StringUtils.repeat("-", headerLine.length()));
    }

    public String getColumn(int columnSize, String columnContent) {
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

    public void outputLine(PrintStream out, List<String> eventInfo) {
        StringBuilder eventLine = new StringBuilder();
        int[] columnSizes = getColumnSizes();
        for (int i=0; i < columnSizes.length; i++) {
            eventLine.append(getColumn(columnSizes[i], eventInfo.get(i)));
            eventLine.append("|");
        }
        out.println(eventLine.toString());
    }

    public abstract Object getListener();

    public Object execute() throws Exception {
        // Do not use System.out as it may write to the wrong console depending on the thread that calls our log handler
        PrintStream out = session.getConsole();
        out.flush();
        outputHeaders(out);

        Object listener = getListener();

        List<String> interfaces = new ArrayList<>();
        for (Class<?> listenerInterface : listener.getClass().getInterfaces()) {
            interfaces.add(listenerInterface.getName());
        }

        ServiceRegistration<?> tailServiceRegistration = bundleContext.registerService(interfaces.toArray(new String[interfaces.size()]), listener, new Hashtable<>());
        try {
            synchronized (this) {
                wait();
            }
        } catch (InterruptedException e) {
            // Ignore as it will happen if the user breaks the tail using Ctrl-C
        } finally {
            tailServiceRegistration.unregister();
        }
        return null;
    }

}
