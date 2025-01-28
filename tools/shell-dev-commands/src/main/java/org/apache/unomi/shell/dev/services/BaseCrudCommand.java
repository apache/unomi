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
import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.conditions.ConditionType;
import org.apache.unomi.api.query.Query;
import org.apache.unomi.api.services.DefinitionsService;
import org.apache.unomi.common.DataTable;
import org.apache.unomi.shell.dev.commands.ListCommandSupport;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for CRUD command implementations that provides common functionality
 * for listing objects in a tabular format.
 */
public abstract class BaseCrudCommand extends ListCommandSupport implements CrudCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(BaseCrudCommand.class.getName());

    @Reference(
        cardinality = ReferenceCardinality.MANDATORY,
        policy = ReferencePolicy.DYNAMIC,
        policyOption = ReferencePolicyOption.GREEDY
    )
    protected volatile DefinitionsService definitionsService;

    @Argument(index = 0, name = "maxEntries", description = "The maximum number of entries to retrieve (defaults to 100)", required = false, multiValued = false)
    protected int maxEntries = 100;

    @Option(name = "--csv", description = "Output in CSV format", required = false)
    protected boolean csv;

    protected void bindDefinitionsService(DefinitionsService definitionsService) {
        this.definitionsService = definitionsService;
    }

    protected void unbindDefinitionsService(DefinitionsService definitionsService) {
        if (this.definitionsService == definitionsService) {
            this.definitionsService = null;
        }
    }

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
            dataTable.addRow(buildRow(item));
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
            table.addRow().addContent(buildRow(item));
        }
    }
}
