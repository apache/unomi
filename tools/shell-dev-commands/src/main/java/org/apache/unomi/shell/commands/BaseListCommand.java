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

import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.support.table.ShellTable;
import org.apache.unomi.api.Item;
import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.query.Query;
import org.apache.unomi.api.services.DefinitionsService;

/**
 * Base class for list commands
 */
public abstract class BaseListCommand<T extends Item> extends BaseCommand {

    @Reference
    protected DefinitionsService definitionsService;

    @Option(name = "--max-entries", description = "Maximum number of entries to display", required = false)
    protected int maxEntries = DEFAULT_ENTRIES;

    @Option(name = "--sort-by", description = "Sort by field name", required = false)
    protected String sortBy;

    protected abstract Class<T> getItemType();

    protected abstract void printItem(ShellTable table, T item);

    @Override
    public Object execute() throws Exception {
        Query query = new Query();
        query.setLimit(maxEntries);
        query.setSortby(sortBy);

        Condition condition = new Condition();
        condition.setConditionType(definitionsService.getConditionType("matchAllCondition"));
        query.setCondition(condition);

        PartialList<T> items = persistenceService.query(query.getCondition(), query.getSortby(), getItemType(), query.getOffset(), query.getLimit());

        ShellTable table = buildTable();
        for (T item : items.getList()) {
            printItem(table, item);
        }
        printTable(table);

        return null;
    }

    public void setDefinitionsService(DefinitionsService definitionsService) {
        this.definitionsService = definitionsService;
    }
}
