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

package org.apache.unomi.persistence.elasticsearch;

import org.apache.unomi.persistence.spi.QueryBuilderAvailabilityTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class ElasticSearchQueryBuilderAvailabilityTracker implements QueryBuilderAvailabilityTracker {
    private static final Logger LOGGER = LoggerFactory.getLogger(ElasticSearchQueryBuilderAvailabilityTracker.class.getName());

    private final Set<String> availableQueryBuilders = ConcurrentHashMap.newKeySet();
    private final Set<String> requiredQueryBuilders = ConcurrentHashMap.newKeySet();
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition allBuildersAvailable = lock.newCondition();

    public ElasticSearchQueryBuilderAvailabilityTracker() {
        // Initialize required query builders
        requiredQueryBuilders.add("propertyConditionESQueryBuilder");
        requiredQueryBuilders.add("booleanConditionESQueryBuilder");
        requiredQueryBuilders.add("notConditionESQueryBuilder");
        requiredQueryBuilders.add("matchAllConditionESQueryBuilder");
        requiredQueryBuilders.add("idsConditionESQueryBuilder");
        requiredQueryBuilders.add("geoLocationByPointSessionConditionESQueryBuilder");
        requiredQueryBuilders.add("sourceEventPropertyConditionESQueryBuilder");
        requiredQueryBuilders.add("pastEventConditionESQueryBuilder");
        requiredQueryBuilders.add("nestedConditionESQueryBuilder");
    }

    public void bindQueryBuilder(ConditionESQueryBuilder queryBuilder, java.util.Map<String, Object> properties) {
        String queryBuilderId = (String) properties.get("queryBuilderId");
        if (queryBuilderId != null) {
            LOGGER.info("Registering query builder: {}", queryBuilderId);
            availableQueryBuilders.add(queryBuilderId);
            checkAndSignalAvailability();
        }
    }

    public void unbindQueryBuilder(ConditionESQueryBuilder queryBuilder, java.util.Map<String, Object> properties) {
        String queryBuilderId = (String) properties.get("queryBuilderId");
        if (queryBuilderId != null) {
            LOGGER.info("Unregistering query builder: {}", queryBuilderId);
            availableQueryBuilders.remove(queryBuilderId);
        }
    }

    @Override
    public boolean areAllQueryBuildersAvailable() {
        return availableQueryBuilders.containsAll(requiredQueryBuilders);
    }

    @Override
    public Set<String> getAvailableQueryBuilderIds() {
        return Set.copyOf(availableQueryBuilders);
    }

    @Override
    public Set<String> getMissingQueryBuilderIds() {
        Set<String> missing = ConcurrentHashMap.newKeySet();
        missing.addAll(requiredQueryBuilders);
        missing.removeAll(availableQueryBuilders);
        return missing;
    }

    @Override
    public boolean waitForQueryBuilders(long timeout) throws InterruptedException {
        lock.lock();
        try {
            long startTime = System.currentTimeMillis();
            while (!areAllQueryBuildersAvailable()) {
                long remainingTime = timeout - (System.currentTimeMillis() - startTime);
                if (remainingTime <= 0) {
                    return false;
                }
                if (!allBuildersAvailable.await(remainingTime, TimeUnit.MILLISECONDS)) {
                    return false;
                }
            }
            return true;
        } finally {
            lock.unlock();
        }
    }

    private void checkAndSignalAvailability() {
        if (areAllQueryBuildersAvailable()) {
            lock.lock();
            try {
                allBuildersAvailable.signalAll();
            } finally {
                lock.unlock();
            }
        }
    }
} 