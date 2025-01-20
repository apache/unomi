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
package org.apache.unomi.shell.completers;

import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.console.CommandLine;
import org.apache.karaf.shell.api.console.Completer;
import org.apache.karaf.shell.api.console.Session;
import org.apache.karaf.shell.support.completers.StringsCompleter;
import org.apache.unomi.api.Item;
import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.query.Query;
import org.apache.unomi.api.services.DefinitionsService;
import org.apache.unomi.persistence.spi.PersistenceService;

import java.util.List;

/**
 * Base class for completers
 */
public abstract class BaseCompleter<T extends Item> implements Completer {
    protected static final int DEFAULT_LIMIT = 50;

    @Reference
    protected PersistenceService persistenceService;

    @Reference
    protected DefinitionsService definitionsService;

    protected abstract Class<T> getItemType();

    protected abstract String getSortBy();

    @Override
    public int complete(Session session, CommandLine commandLine, List<String> candidates) {
        StringsCompleter delegate = new StringsCompleter();

        try {
            Query query = new Query();
            query.setLimit(DEFAULT_LIMIT);
            query.setSortby(getSortBy());

            Condition condition = new Condition();
            condition.setConditionType(definitionsService.getConditionType("matchAllCondition"));
            query.setCondition(condition);

            PartialList<T> items = persistenceService.query(query.getCondition(), query.getSortby(), getItemType(), query.getOffset(), query.getLimit());

            for (T item : items.getList()) {
                delegate.getStrings().add(item.getItemId());
            }

            return delegate.complete(session, commandLine, candidates);
        } catch (Exception e) {
            // Log error but don't fail completion
            System.err.println("Error completing items: " + e.getMessage());
            return -1;
        }
    }

    public void setPersistenceService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    public void setDefinitionsService(DefinitionsService definitionsService) {
        this.definitionsService = definitionsService;
    }
}
