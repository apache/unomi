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
package org.apache.unomi.services.impl.topics;

import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.Topic;
import org.apache.unomi.api.query.Query;
import org.apache.unomi.api.services.DefinitionsService;
import org.apache.unomi.api.services.TopicService;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.SynchronousBundleListener;

public class TopicServiceImpl implements TopicService, SynchronousBundleListener {

    private PersistenceService persistenceService;

    private DefinitionsService definitionsService;

    private BundleContext bundleContext;

    public void setPersistenceService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    public void setDefinitionsService(DefinitionsService definitionsService) {
        this.definitionsService = definitionsService;
    }

    public void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    @Override
    public Topic load(final String topicId) {
        return persistenceService.load(topicId, Topic.class);
    }

    @Override
    public Topic save(final Topic topic) {
        if (persistenceService.save(topic)) {
            persistenceService.refreshIndex(Topic.class, null);

            return topic;
        }

        return null;
    }

    @Override
    public PartialList<Topic> search(final Query query) {
        return persistenceService.query(query.getCondition(), query.getSortby(), Topic.class, query.getOffset(), query.getLimit());
    }

    @Override
    public void bundleChanged(BundleEvent bundleEvent) {
        // do nothing
    }

    public void postConstruct() {
        bundleContext.addBundleListener(this);
    }

    public void preDestroy() {
        bundleContext.removeBundleListener(this);
    }

}
