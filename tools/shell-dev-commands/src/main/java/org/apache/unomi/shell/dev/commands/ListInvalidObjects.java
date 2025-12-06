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

import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.karaf.shell.support.table.ShellTable;
import org.apache.unomi.api.services.InvalidObjectInfo;
import org.apache.unomi.api.services.ResolverService;

import java.util.*;

/**
 * Karaf shell command to list all invalid objects across all services.
 * Invalid objects are those that have unresolved condition types, action types, or other validation issues.
 */
@Command(scope = "unomi", name = "list-invalid-objects", description = "Lists all invalid objects (rules, segments, goals, campaigns, scoring) that have unresolved condition types, action types, or other validation issues")
@Service
public class ListInvalidObjects implements Action {

    @Reference
    ResolverService resolverService;

    public Object execute() throws Exception {
        if (resolverService == null) {
            System.out.println("ResolverService is not available.");
            return null;
        }

        Map<String, Map<String, InvalidObjectInfo>> allInvalidObjects = resolverService.getAllInvalidObjects();
        int totalCount = resolverService.getTotalInvalidObjectCount();

        if (totalCount == 0) {
            System.out.println("No invalid objects found.");
            return null;
        }

        System.out.println("Invalid Objects Summary:");
        System.out.println("Total invalid objects: " + totalCount);
        System.out.println();

        // Create a table to display invalid objects with reasons
        ShellTable table = new ShellTable();
        table.column("Object Type");
        table.column("Object ID");
        table.column("Reason");

        // Sort object types for consistent output
        List<String> sortedTypes = new ArrayList<>(allInvalidObjects.keySet());
        Collections.sort(sortedTypes);

        for (String objectType : sortedTypes) {
            Map<String, InvalidObjectInfo> invalidObjectsMap = allInvalidObjects.get(objectType);
            List<String> sortedIds = new ArrayList<>(invalidObjectsMap.keySet());
            Collections.sort(sortedIds);

            for (String objectId : sortedIds) {
                InvalidObjectInfo info = invalidObjectsMap.get(objectId);
                String reason = info.getReason();
                // Truncate long reasons for display
                if (reason != null && reason.length() > 80) {
                    reason = reason.substring(0, 77) + "...";
                }
                table.addRow().addContent(objectType, objectId, reason != null ? reason : "Unknown reason");
            }
        }

        table.print(System.out);
        System.out.println();
        System.out.println("Object type counts:");
        for (String objectType : sortedTypes) {
            System.out.println("  " + objectType + ": " + allInvalidObjects.get(objectType).size());
        }

        return null;
    }
}

