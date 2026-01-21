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
        // Get the operation and type from the command line (index 0 = operation, index 1 = type)
        String operation = null;
        String type = null;
        String[] args = commandLine.getArguments();
        if (args.length > 0) {
            operation = args[0];
        }
        if (args.length > 1) {
            type = args[1];
        }
        if (type == null) {
            return -1;
        }

        // Determine if ID completion is appropriate based on operation
        // ID completion is only appropriate for: read, delete, and update (first argument)
        if (operation != null) {
            String operationLower = operation.toLowerCase();
            if (!"read".equals(operationLower) && !"delete".equals(operationLower) && !"update".equals(operationLower)) {
                // For create, list, help - ID completion is not appropriate
                return -1;
            }
        }

        // For update operation, check if we're completing the first argument (ID) or second (JSON)
        // remaining[0] = ID, remaining[1] = JSON
        // If args.length > 2, we're completing remaining[1] (JSON), so don't complete IDs
        if (operation != null && "update".equals(operation.toLowerCase()) && args.length > 2) {
            // We're past the ID argument, so we're completing JSON - don't complete IDs
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
                            // Get the prefix from what the user has typed so far
                            // StringsCompleter will handle the final matching, but we need prefix for server-side filtering
                            String prefix = "";
                            String buffer = commandLine.getBuffer();
                            
                            if (buffer != null && !buffer.trim().isEmpty()) {
                                // Get the last word from the buffer (the current value being typed)
                                String trimmed = buffer.trim();
                                int lastSpace = trimmed.lastIndexOf(' ');
                                if (lastSpace >= 0 && lastSpace < trimmed.length() - 1) {
                                    prefix = trimmed.substring(lastSpace + 1);
                                    // Skip if it looks like an option
                                    if (prefix.startsWith("-")) {
                                        prefix = "";
                                    }
                                }
                            }
                            
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
            // Note: Printing during completion can interfere with completion, but using console for consistency
            // Only log if it's a serious error - avoid logging during normal completion
            LOGGER.debug("Error getting completions", e);
        }

        return -1;
    }
}
