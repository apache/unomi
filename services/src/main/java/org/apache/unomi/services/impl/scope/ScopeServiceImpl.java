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
package org.apache.unomi.services.impl.scope;

import org.apache.unomi.api.Item;
import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.Scope;
import org.apache.unomi.api.services.SchedulerService;
import org.apache.unomi.api.services.ScopeService;
import org.apache.unomi.persistence.spi.PersistenceService;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TimerTask;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class ScopeServiceImpl implements ScopeService {

    private PersistenceService persistenceService;

    private SchedulerService schedulerService;

    private Integer scopesRefreshInterval = 1000;

    private Map<String, Scope> scopes = new HashMap<>();

    private ScheduledFuture<?> scheduledFuture;

    public void setPersistenceService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    public void setSchedulerService(SchedulerService schedulerService) {
        this.schedulerService = schedulerService;
    }

    public void setScopesRefreshInterval(Integer scopesRefreshInterval) {
        this.scopesRefreshInterval = scopesRefreshInterval;
    }

    public void postConstruct() {
        initializeTimers();
    }

    public void preDestroy() {
        scheduledFuture.cancel(true);
    }

    @Override
    public PartialList<Metadata> getScopesMetadatas(int offset, int size, String sortBy) {
        PartialList<Scope> items = persistenceService.getAllItems(Scope.class, offset, size, sortBy);
        List<Metadata> details = new LinkedList<>();
        for (Scope definition : items.getList()) {
            details.add(definition.getMetadata());
        }
        return new PartialList<>(details, items.getOffset(), items.getPageSize(), items.getTotalSize(), items.getTotalSizeRelation());
    }

    @Override
    public void save(Scope scope) {
        if (persistenceService.save(scope)) {
            scopes.put(scope.getItemId(), scope);
        }
    }

    @Override
    public boolean delete(String id) {
        if (persistenceService.remove(id, Scope.class)) {
            scopes.remove(id);
            return true;
        }
        return false;
    }

    @Override
    public Scope getScope(String id) {
        return persistenceService.load(id, Scope.class);
    }

    private void initializeTimers() {
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                refreshScopes();
            }
        };
        scheduledFuture = schedulerService.getScheduleExecutorService()
                .scheduleWithFixedDelay(task, 0, scopesRefreshInterval, TimeUnit.MILLISECONDS);
    }

    private void refreshScopes() {
        scopes = persistenceService.getAllItems(Scope.class).stream().collect(Collectors.toMap(Item::getItemId, scope -> scope));
    }
}
