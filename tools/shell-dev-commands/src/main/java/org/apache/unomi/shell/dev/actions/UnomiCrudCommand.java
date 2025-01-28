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
package org.apache.unomi.shell.dev.actions;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.karaf.shell.api.action.*;
import org.apache.karaf.shell.api.action.lifecycle.Init;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.karaf.shell.support.table.ShellTable;
import org.apache.unomi.shell.dev.completers.IdCompleter;
import org.apache.unomi.shell.dev.completers.OperationCompleter;
import org.apache.unomi.shell.dev.completers.TypeCompleter;
import org.apache.unomi.shell.dev.services.CrudCommand;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Command(scope = "unomi", name = "crud", description = "Perform CRUD operations on Unomi objects")
@Service
public class UnomiCrudCommand implements Action {

    private static final Logger LOGGER = LoggerFactory.getLogger(UnomiCrudCommand.class.getName());

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Reference
    private BundleContext bundleContext;

    @Argument(index = 0, name = "operation", description = "Operation to perform (create/read/update/delete/list/help)", required = true)
    @Completion(OperationCompleter.class)
    private String operation;

    @Argument(index = 1, name = "type", description = "Object type", required = true)
    @Completion(TypeCompleter.class)
    private String type;

    @Argument(index = 2, name = "id", description = "Object ID (for read/update/delete)", required = false)
    @Completion(IdCompleter.class)
    private String id;

    @Option(name = "-f", aliases = "--file", description = "JSON file containing object properties (for create/update)")
    private String file;

    @Option(name = "--csv", description = "Output list in CSV format")
    private boolean csv;

    @Option(name = "-n", aliases = "--max-entries", description = "Maximum number of entries to list")
    private int maxEntries = 100;

    @Init
    public void init() {
        LOGGER.debug("UnomiCrudCommand init");
    }

    @Override
    public Object execute() throws Exception {
        // Get all registered CrudCommand implementations
        List<CrudCommand> commands = new ArrayList<>();
        ServiceReference<?>[] refs = bundleContext.getAllServiceReferences(CrudCommand.class.getName(), null);
        if (refs != null) {
            for (ServiceReference<?> ref : refs) {
                CrudCommand cmd = (CrudCommand) bundleContext.getService(ref);
                if (cmd.getObjectType().equals(type)) {
                    try {
                        switch (operation.toLowerCase()) {
                            case "create":
                                if (file == null) {
                                    System.err.println("--file option is required for create operation");
                                    return null;
                                }
                                Map<String, Object> createProps = OBJECT_MAPPER.readValue(Files.readString(Paths.get(file)), Map.class);
                                String newId = cmd.create(createProps);
                                System.out.println("Created " + type + " with ID: " + newId);
                                break;

                            case "read":
                                if (id == null) {
                                    System.err.println("ID is required for read operation");
                                    return null;
                                }
                                Map<String, Object> obj = cmd.read(id);
                                if (obj != null) {
                                    System.out.println(OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(obj));
                                } else {
                                    System.err.println(type + " not found with ID: " + id);
                                }
                                break;

                            case "update":
                                if (id == null || file == null) {
                                    System.err.println("ID and --file options are required for update operation");
                                    return null;
                                }
                                Map<String, Object> updateProps = OBJECT_MAPPER.readValue(Files.readString(Paths.get(file)), Map.class);
                                cmd.update(id, updateProps);
                                System.out.println("Updated " + type + " with ID: " + id);
                                break;

                            case "delete":
                                if (id == null) {
                                    System.err.println("ID is required for delete operation");
                                    return null;
                                }
                                cmd.delete(id);
                                System.out.println("Deleted " + type + " with ID: " + id);
                                break;

                            case "list":
                                ShellTable table = new ShellTable();
                                if (csv) {
                                    table.noHeaders().separator(",");
                                }
                                for (String header : cmd.getHeaders()) {
                                    table.column(header);
                                }
                                cmd.buildRows(table, maxEntries);
                                table.print(System.out, !csv);
                                break;

                            case "help":
                                System.out.println("Properties for " + type + ":");
                                System.out.println(cmd.getPropertiesHelp());
                                break;

                            default:
                                System.err.println("Unknown operation: " + operation);
                                System.err.println("Available operations: create, read, update, delete, list, help");
                        }
                    } finally {
                        bundleContext.ungetService(ref);
                    }
                    return null;
                }
            }
        }
        System.err.println("No handler found for object type: " + type);
        return null;
    }
}
