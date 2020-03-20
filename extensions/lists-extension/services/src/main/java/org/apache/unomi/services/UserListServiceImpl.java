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
package org.apache.unomi.services;

import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.query.Query;
import org.apache.unomi.api.services.DefinitionsService;
import org.apache.unomi.lists.UserList;
import org.apache.unomi.persistence.spi.PersistenceService;

import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * @author Christophe Laprun
 */
public class UserListServiceImpl implements UserListService {
    private PersistenceService persistenceService;

    private DefinitionsService definitionsService;

    public void setPersistenceService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    public void setDefinitionsService(DefinitionsService definitionsService) {
        this.definitionsService = definitionsService;
    }

    public PartialList<Metadata> getListMetadatas(int offset, int size, String sortBy) {
        PartialList<UserList> userLists = persistenceService.getAllItems(UserList.class, offset, size, sortBy);
        List<Metadata> metadata = new LinkedList<>();
        for (UserList definition : userLists.getList()) {
            metadata.add(definition.getMetadata());
        }
        return new PartialList<>(metadata, userLists.getOffset(), userLists.getPageSize(), userLists.getTotalSize(), userLists.getTotalSizeRelation());
    }

    public PartialList<Metadata> getListMetadatas(Query query) {
        if(query.isForceRefresh()){
            persistenceService.refresh();
        }
        definitionsService.resolveConditionType(query.getCondition());
        PartialList<UserList> userLists = persistenceService.query(query.getCondition(), query.getSortby(), UserList.class, query.getOffset(), query.getLimit());
        List<Metadata> metadata = new LinkedList<>();
        for (UserList definition : userLists.getList()) {
            metadata.add(definition.getMetadata());
        }
        return new PartialList<>(metadata, userLists.getOffset(), userLists.getPageSize(), userLists.getTotalSize(), userLists.getTotalSizeRelation());
    }

    @Override
    public UserList load(String listId) {
        return persistenceService.load(listId, UserList.class);
    }

    @Override
    public void save(UserList list) {
        persistenceService.save(list);
    }

    @Override
    public void delete(String listId) {
        Condition query = new Condition(definitionsService.getConditionType("profilePropertyCondition"));
        query.setParameter("propertyName", "systemProperties.lists");
        query.setParameter("comparisonOperator", "equals");
        query.setParameter("propertyValue", listId);

        List<Profile> profiles = persistenceService.query(query, null, Profile.class);
        Map<String, Object> profileSystemProperties;
        for (Profile p : profiles) {
            profileSystemProperties = p.getSystemProperties();
            if(profileSystemProperties != null && profileSystemProperties.get("lists") != null) {
                int index = ((List) profileSystemProperties.get("lists")).indexOf(listId);
                if(index != -1){
                    ((List) profileSystemProperties.get("lists")).remove(index);
                    profileSystemProperties.put("lastUpdated", new Date());
                    persistenceService.update(p.getItemId(), null, Profile.class, "systemProperties", profileSystemProperties);
                }
            }
        }

        persistenceService.remove(listId, UserList.class);
    }
}
