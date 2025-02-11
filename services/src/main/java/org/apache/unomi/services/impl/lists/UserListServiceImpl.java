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

package org.apache.unomi.services.impl.lists;

import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.lists.UserList;
import org.apache.unomi.api.services.DefinitionsService;
import org.apache.unomi.api.services.UserListService;
import org.apache.unomi.services.impl.AbstractContextAwareService;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.SynchronousBundleListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.List;

/**
 * Created by amidani on 24/03/2017.
 */
public class UserListServiceImpl extends AbstractContextAwareService implements UserListService, SynchronousBundleListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserListServiceImpl.class.getName());

    private BundleContext bundleContext;
    private DefinitionsService definitionsService;

    public void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    public void setDefinitionsService(DefinitionsService definitionsService) {
        this.definitionsService = definitionsService;
    }

    public void postConstruct() {
        LOGGER.debug("postConstruct {{}}", bundleContext.getBundle());
        bundleContext.addBundleListener(this);
        LOGGER.info("User list service initialized.");
    }

    public void preDestroy() {
        bundleContext.removeBundleListener(this);
        LOGGER.info("User list service shutdown.");
    }

    public List<UserList> getAllUserLists() {
        return persistenceService.getAllItems(UserList.class);
    }

    @Override
    public PartialList<Metadata> getUserListMetadatas(int offset, int size, String sortBy) {
        return getMetadatas(offset, size, sortBy, UserList.class);
    }

    protected <T extends UserList> PartialList<Metadata> getMetadatas(int offset, int size, String sortBy, Class<T> clazz) {
        String currentTenantId = contextManager.getCurrentContext().getTenantId();
        Condition tenantCondition = new Condition(definitionsService.getConditionType("sessionPropertyCondition"));
        tenantCondition.setParameter("propertyName", "tenantId");
        tenantCondition.setParameter("comparisonOperator", "equals");
        tenantCondition.setParameter("propertyValue", currentTenantId);

        PartialList<T> items = persistenceService.query(tenantCondition, sortBy, clazz, offset, size);
        List<Metadata> details = new LinkedList<>();
        for (T definition : items.getList()) {
            details.add(definition.getMetadata());
        }
        return new PartialList<>(details, items.getOffset(), items.getPageSize(), items.getTotalSize(), items.getTotalSizeRelation());
    }

    @Override
    public void bundleChanged(BundleEvent bundleEvent) { }
}
