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
package org.apache.unomi.shell.dev.commands.scheduler;

import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.console.Session;
import org.apache.unomi.api.services.SchedulerService;

import java.io.PrintStream;

/**
 * Base class for scheduler-related shell commands that provides common functionality
 * for accessing SchedulerService and Session.
 */
public abstract class BaseSchedulerCommand implements Action {

    @Reference
    protected SchedulerService schedulerService;

    @Reference
    protected Session session;

    /**
     * Get the console PrintStream from the session.
     * 
     * @return the console PrintStream
     */
    protected PrintStream getConsole() {
        return session.getConsole();
    }

    /**
     * Print a message to the console.
     * 
     * @param message the message to print
     */
    protected void println(String message) {
        getConsole().println(message);
    }

    /**
     * Print a formatted message to the console.
     * 
     * @param format the format string
     * @param args the arguments
     */
    protected void printf(String format, Object... args) {
        getConsole().printf(format, args);
    }
}
