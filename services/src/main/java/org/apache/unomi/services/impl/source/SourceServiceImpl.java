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
package org.apache.unomi.services.impl.source;

import org.apache.unomi.api.SourceItem;
import org.apache.unomi.api.services.SourceService;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.SynchronousBundleListener;

import java.util.List;

public class SourceServiceImpl implements SourceService, SynchronousBundleListener {

    private PersistenceService persistenceService;

    private BundleContext bundleContext;

    @Override
    public SourceItem load(String sourceId) {
        return persistenceService.load(sourceId, SourceItem.class);
    }

    @Override
    public SourceItem save(SourceItem source) {
        if (persistenceService.save(source)) {
            persistenceService.refreshIndex(SourceItem.class, null);

            return source;
        }

        return null;
    }

    @Override
    public List<SourceItem> getAll() {
        return persistenceService.getAllItems(SourceItem.class);
    }

    @Override
    public boolean delete(String sourceId) {
        return persistenceService.remove(sourceId, SourceItem.class);
    }

    @Override
    public void bundleChanged(BundleEvent bundleEvent) {
        // do nothing
    }

    public void setPersistenceService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    public void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    public void postConstruct() {
        bundleContext.addBundleListener(this);
    }

    public void preDestroy() {
        bundleContext.removeBundleListener(this);
    }

}
