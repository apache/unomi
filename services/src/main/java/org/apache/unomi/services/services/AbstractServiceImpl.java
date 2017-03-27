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

package org.apache.unomi.services.services;

import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.MetadataItem;
import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.query.Query;
import org.apache.unomi.api.services.DefinitionsService;
import org.apache.unomi.persistence.spi.PersistenceService;

import java.util.LinkedList;
import java.util.List;

/**
 * Created by amidani on 24/03/2017.
 */
public abstract class AbstractServiceImpl {

    PersistenceService persistenceService;

    DefinitionsService definitionsService;

    public void setPersistenceService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    public void setDefinitionsService(DefinitionsService definitionsService) {
        this.definitionsService = definitionsService;
    }

    <T extends MetadataItem> PartialList<Metadata> getMetadatas(int offset, int size, String sortBy, Class<T> clazz) {
        PartialList<T> items = persistenceService.getAllItems(clazz, offset, size, sortBy);
        List<Metadata> details = new LinkedList<>();
        for (T definition : items.getList()) {
            details.add(definition.getMetadata());
        }
        return new PartialList<>(details, items.getOffset(), items.getPageSize(), items.getTotalSize());
    }

    <T extends MetadataItem> PartialList<Metadata> getMetadatas(Query query, Class<T> clazz) {
        if (query.isForceRefresh()) {
            persistenceService.refresh();
        }
        definitionsService.resolveConditionType(query.getCondition());
        PartialList<T> items = persistenceService.query(query.getCondition(), query.getSortby(), clazz, query.getOffset(), query.getLimit());
        List<Metadata> details = new LinkedList<>();
        for (T definition : items.getList()) {
            details.add(definition.getMetadata());
        }
        return new PartialList<>(details, items.getOffset(), items.getPageSize(), items.getTotalSize());
    }
}
