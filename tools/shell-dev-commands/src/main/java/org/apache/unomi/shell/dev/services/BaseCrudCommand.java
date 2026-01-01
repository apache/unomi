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
package org.apache.unomi.shell.dev.services;

import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.support.table.ShellTable;
import org.apache.unomi.api.Item;
import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.conditions.ConditionType;
import org.apache.unomi.api.query.Query;
import org.apache.unomi.api.services.DefinitionsService;
import org.apache.unomi.api.tenants.Tenant;
import org.apache.unomi.common.DataTable;
import org.apache.unomi.shell.dev.commands.ListCommandSupport;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Base class for CRUD command implementations that provides common functionality
 * for listing objects in a tabular format.
 */
public abstract class BaseCrudCommand extends ListCommandSupport implements CrudCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(BaseCrudCommand.class.getName());

    @Reference
    protected volatile DefinitionsService definitionsService;

    @Argument(index = 0, name = "maxEntries", description = "The maximum number of entries to retrieve (defaults to 100)", required = false, multiValued = false)
    protected int maxEntries = 100;

    @Option(name = "--csv", description = "Output in CSV format", required = false)
    protected boolean csv;

    @Override
    protected DataTable buildDataTable() {
        Query query = new Query();
        query.setLimit(maxEntries);
        Condition matchAllCondition = new Condition(definitionsService.getConditionType("matchAllCondition"));
        query.setCondition(matchAllCondition);
        query.setSortby(getSortBy());

        PartialList<?> items = getItems(query);
        if (items.getList().size() != items.getTotalSize()) {
            System.out.println("WARNING : Only the first " + items.getPageSize() + " items have been retrieved, there are " + items.getTotalSize() + " items registered in total. Use the maxEntries parameter to retrieve more items");
        }

        DataTable dataTable = new DataTable();
        for (Object item : items.getList()) {
            Comparable[] rowData = buildRow(item);

            // Get tenant ID from the item if possible
            String tenantId = getTenantIdFromItem(item);

            // Create a new array with tenantId as the first element
            Comparable[] rowWithTenant = new Comparable[rowData.length + 1];
            rowWithTenant[0] = tenantId;
            System.arraycopy(rowData, 0, rowWithTenant, 1, rowData.length);

            dataTable.addRow(rowWithTenant);
        }

        return dataTable;
    }

    /**
     * Get the sort criteria for the query.
     * Default implementation sorts by last modification date.
     * Override to provide different sorting.
     *
     * @return sort criteria (e.g., "metadata.lastModified:desc")
     */
    protected String getSortBy() {
        return "metadata.lastModified:desc";
    }

    /**
     * Get items using the provided query.
     * Implementations must override this to use their specific service.
     *
     * @param query the query to execute
     * @return partial list of items
     */
    protected abstract PartialList<?> getItems(Query query);

    /**
     * Build a row for the data table from an item.
     * Implementations must override this to extract the relevant properties.
     *
     * @param item the item to convert to a row
     * @return array of values for the row
     */
    protected abstract Comparable[] buildRow(Object item);

    /**
     * Get the column headers for the list output table.
     * This implementation is used by both ListCommandSupport and CrudCommand.
     *
     * @return array of column headers
     */
    @Override
    public abstract String[] getHeaders();

    /**
     * Returns the headers with "TenantId" as the first column.
     * This method should be used by subclasses to ensure tenant ID is always displayed first.
     *
     * @param originalHeaders the original headers from the implementation
     * @return array of column headers with "TenantId" as the first element
     */
    protected String[] prependTenantIdHeader(String[] originalHeaders) {
        String[] headersWithTenant = new String[originalHeaders.length + 1];
        headersWithTenant[0] = "Tenant";
        System.arraycopy(originalHeaders, 0, headersWithTenant, 1, originalHeaders.length);
        return headersWithTenant;
    }

    @Override
    public void buildRows(ShellTable table, int maxEntries) {
        Query query = new Query();
        query.setLimit(maxEntries);
        if (definitionsService == null) {
            System.err.println("No definitions service available, unable to build rows");
            LOGGER.error("Definition service is not available, unable to build rows");
            return;
        }
        ConditionType matchAllConditionType = definitionsService.getConditionType("matchAllCondition");
        if (matchAllConditionType == null) {
            System.err.println("No matchAllCondition available, unable to build rows");
            LOGGER.error("No matchAllCondition available, unable to build rows");
        }
        Condition matchAllCondition = new Condition(matchAllConditionType);
        query.setCondition(matchAllCondition);
        query.setSortby(getSortBy());

        PartialList<?> items = getItems(query);
        if (items.getList().size() != items.getTotalSize()) {
            System.out.println("WARNING : Only the first " + items.getPageSize() + " items have been retrieved, there are " + items.getTotalSize() + " items registered in total. Use the maxEntries parameter to retrieve more items");
        }

        for (Object item : items.getList()) {
            Comparable[] rowData = buildRow(item);

            // Get tenant ID from the item if possible
            String tenantId = getTenantIdFromItem(item);

            // Create a new array with tenantId as the first element
            Comparable[] rowWithTenant = new Comparable[rowData.length + 1];
            rowWithTenant[0] = tenantId;
            System.arraycopy(rowData, 0, rowWithTenant, 1, rowData.length);

            table.addRow().addContent(rowWithTenant);
        }
    }

    /**
     * Extract the tenant ID from an item.
     *
     * @param item the item to extract tenant ID from
     * @return the tenant ID or a default value if it can't be determined
     */
    protected String getTenantIdFromItem(Object item) {

        // Handle tenant-specific objects
        if (item instanceof Tenant) {
            return ((Tenant) item).getItemId();
        }

        // Handle Item subclasses that directly have tenantId
        if (item instanceof Item) {
            String tenantId = ((Item) item).getTenantId();
            return tenantId;
        }

        return "n/a";
    }

    /**
     * Default implementation of ID completion for all CRUD commands.
     * This method fetches a limited number of items and filters their IDs based on the given prefix.
     *
     * @param prefix the prefix to filter IDs by
     * @return a list of matching item IDs
     */
    @Override
    public List<String> completeId(String prefix) {
        // Create a query with increased limit to provide more completions
        Query query = new Query();
        query.setLimit(50); // Higher limit for completions

        if (definitionsService == null) {
            LOGGER.error("Definition service is not available, unable to complete IDs");
            return List.of();
        }

        try {
            Condition matchAllCondition = new Condition(definitionsService.getConditionType("matchAllCondition"));
            query.setCondition(matchAllCondition);
            query.setSortby(getSortBy());

            // Get items using the appropriate service method
            PartialList<?> items = getItems(query);

            // Extract IDs from the items
            List<String> ids = new ArrayList<>();
            for (Object item : items.getList()) {
                String id = extractIdFromItem(item);
                if (id != null && (prefix.isEmpty() || id.startsWith(prefix))) {
                    ids.add(id);
                }
            }

            return ids;
        } catch (Exception e) {
            LOGGER.error("Error completing IDs", e);
            return List.of();
        }
    }

    /**
     * Extract the ID from an item. This method attempts to extract the ID using common patterns.
     * Subclasses can override this method to provide specialized ID extraction for specific item types.
     *
     * @param item the item to extract the ID from
     * @return the extracted ID, or null if it couldn't be extracted
     */
    protected String extractIdFromItem(Object item) {
        // Handle Item subclasses
        if (item instanceof Item) {
            return ((Item) item).getItemId();
        }

        // Handle Metadata objects
        if (item instanceof Metadata) {
            return ((Metadata) item).getId();
        }

        // Try reflection as a fallback
        try {
            // Try common getter method names for ID
            for (String methodName : new String[]{"getId", "getItemId", "getIdentifier", "getKey", "getName"}) {
                try {
                    Method method = item.getClass().getMethod(methodName);
                    Object result = method.invoke(item);
                    if (result != null) {
                        return result.toString();
                    }
                } catch (NoSuchMethodException e) {
                    // Method doesn't exist, try the next one
                }
            }

            // Try direct field access as a last resort
            for (String fieldName : new String[]{"id", "itemId", "identifier", "key", "name"}) {
                try {
                    Field field = item.getClass().getDeclaredField(fieldName);
                    field.setAccessible(true);
                    Object value = field.get(item);
                    if (value != null) {
                        return value.toString();
                    }
                } catch (NoSuchFieldException e) {
                    // Field doesn't exist, try the next one
                }
            }
        } catch (Exception e) {
            // Ignore reflection errors
        }

        // If all else fails, use toString and hope it's meaningful
        return item.toString();
    }
}
