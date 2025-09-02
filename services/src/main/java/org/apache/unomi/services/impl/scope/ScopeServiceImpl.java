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
import org.apache.unomi.api.Scope;
import org.apache.unomi.api.services.SchedulerService;
import org.apache.unomi.api.services.ScopeService;
import org.apache.unomi.api.tasks.ScheduledTask;
import org.apache.unomi.api.tasks.TaskExecutor;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class ScopeServiceImpl implements ScopeService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ScopeServiceImpl.class.getName());

    private PersistenceService persistenceService;
    private SchedulerService schedulerService;
    private Integer scopesRefreshInterval = 1000;
    private ConcurrentMap<String, Scope> scopes = new ConcurrentHashMap<>();

    private static final String REFRESH_SCOPES_TASK_TYPE = "refresh-scopes";
    private String refreshScopesTaskId;

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
        this.initializeTimers();
    }

    public void preDestroy() {
        this.resetTimers();
    }

    @Override
    public List<Scope> getScopes() {
        return new ArrayList<>(scopes.values());
    }

    @Override
    public void save(Scope scope) {
        persistenceService.save(scope);
    }

    @Override
    public boolean delete(String id) {
        return persistenceService.remove(id, Scope.class);
    }

    @Override
    public Scope getScope(String id) {
        return scopes.get(id);
    }

    private void initializeTimers() {
        TaskExecutor refreshScopesTaskExecutor = new TaskExecutor() {
            @Override
            public String getTaskType() {
                return REFRESH_SCOPES_TASK_TYPE;
            }

            @Override
            public void execute(ScheduledTask task, TaskExecutor.TaskStatusCallback callback) {
                try {
                    refreshScopes();
                    callback.complete();
                } catch (Exception e) {
                    LOGGER.error("Error while refreshing scopes", e);
                    callback.fail(e.getMessage());
                }
            }
        };

        schedulerService.registerTaskExecutor(refreshScopesTaskExecutor);

        this.resetTimers();
        this.refreshScopesTaskId = schedulerService.newTask(REFRESH_SCOPES_TASK_TYPE)
                .withPeriod(scopesRefreshInterval, TimeUnit.MILLISECONDS)
                .nonPersistent()
                .schedule().getItemId();
    }

    private void resetTimers() {
        if (refreshScopesTaskId != null) {
            schedulerService.cancelTask(refreshScopesTaskId);
            refreshScopesTaskId = null;
        }
    }

    private void refreshScopes() {
        scopes = persistenceService.getAllItems(Scope.class).stream().collect(Collectors.toConcurrentMap(Item::getItemId, scope -> scope));
    }
}
