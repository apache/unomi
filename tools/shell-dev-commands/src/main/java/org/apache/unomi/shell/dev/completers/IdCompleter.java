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
package org.apache.unomi.shell.dev.completers;

import org.apache.karaf.shell.api.action.lifecycle.Init;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.karaf.shell.api.console.CommandLine;
import org.apache.karaf.shell.api.console.Completer;
import org.apache.karaf.shell.api.console.Session;
import org.apache.karaf.shell.support.completers.StringsCompleter;
import org.apache.unomi.shell.dev.services.CrudCommand;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@Service
public class IdCompleter implements Completer {

    private static final Logger LOGGER = LoggerFactory.getLogger(IdCompleter.class.getName());

    @Reference
    private BundleContext bundleContext;

    @Init
    public void init() {
        LOGGER.debug("IdCompleter initialized");
    }

    @Override
    public int complete(Session session, CommandLine commandLine, List<String> candidates) {
        // Get the type argument from the command line
        String type = null;
        if (commandLine.getArguments().length > 1) {
            type = commandLine.getArguments()[1];
        }
        if (type == null) {
            return -1;
        }

        // Find the CrudCommand for this type
        try {
            ServiceReference<?>[] refs = bundleContext.getAllServiceReferences(CrudCommand.class.getName(), null);
            if (refs != null) {
                for (ServiceReference<?> ref : refs) {
                    CrudCommand cmd = (CrudCommand) bundleContext.getService(ref);
                    try {
                        if (cmd.getObjectType().equals(type)) {
                            // Get completions from the command
                            String prefix = commandLine.getArguments().length > 2 ? commandLine.getArguments()[2] : "";
                            List<String> completions = cmd.completeId(prefix);

                            StringsCompleter delegate = new StringsCompleter();
                            delegate.getStrings().addAll(completions);
                            return delegate.complete(session, commandLine, candidates);
                        }
                    } finally {
                        bundleContext.ungetService(ref);
                    }
                }
            }
        } catch (Exception e) {
            // Log error but continue
            System.err.println("Error getting completions: " + e.getMessage());
        }

        return -1;
    }
}
