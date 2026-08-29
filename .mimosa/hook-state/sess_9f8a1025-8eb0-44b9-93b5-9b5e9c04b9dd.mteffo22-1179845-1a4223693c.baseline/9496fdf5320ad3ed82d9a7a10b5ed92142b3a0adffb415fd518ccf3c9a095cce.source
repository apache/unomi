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

import org.apache.unomi.api.Scope;
import org.apache.unomi.api.services.ScopeService;
import org.apache.unomi.api.services.cache.CacheableTypeConfig;
import org.apache.unomi.services.common.cache.AbstractMultiTypeCachingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

public class ScopeServiceImpl extends AbstractMultiTypeCachingService implements ScopeService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ScopeServiceImpl.class.getName());

    private Integer scopesRefreshInterval = 1000;

    @Override
    public List<Scope> getScopes() {
        return new ArrayList<>(getAllItems(Scope.class, true));
    }

    @Override
    public void save(Scope scope) {
        String currentTenant = contextManager.getCurrentContext().getTenantId();
        if (currentTenant == null) {
            throw new IllegalStateException("Cannot save scope: no tenant specified");
        }
        scope.setTenantId(currentTenant);
        saveItem(scope, Scope::getItemId, Scope.ITEM_TYPE);
    }

    @Override
    public boolean delete(String id) {
        removeItem(id, Scope.class, Scope.ITEM_TYPE);
        return true;
    }

    @Override
    public Scope getScope(String id) {
        return getItem(id, Scope.class);
    }

    public void setScopesRefreshInterval(Integer scopesRefreshInterval) {
        this.scopesRefreshInterval = scopesRefreshInterval;
    }

    @Override
    protected Set<CacheableTypeConfig<?>> getTypeConfigs() {
        Set<CacheableTypeConfig<?>> configs = new HashSet<>();
        configs.add(CacheableTypeConfig.builder(Scope.class, Scope.ITEM_TYPE, null)
            .withPredefinedItems(false)
            .withRequiresRefresh(true)
            .withRefreshInterval(scopesRefreshInterval)
            .withIdExtractor(Scope::getItemId)
            .build());
        return configs;
    }
}
