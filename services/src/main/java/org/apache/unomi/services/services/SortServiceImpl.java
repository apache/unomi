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

import org.apache.unomi.api.ContextRequest;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.Session;
import org.apache.unomi.api.SortStrategy;
import org.apache.unomi.api.services.SortService;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SortServiceImpl implements SortService {

    private BundleContext bundleContext;

    private Map<String, SortStrategy> sortStrategies = new ConcurrentHashMap<>();

    public void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    public void addSortStrategy(ServiceReference<SortStrategy> sortStrategyRef) {
        SortStrategy sortStrategy = bundleContext.getService(sortStrategyRef);
        sortStrategies.put(sortStrategyRef.getProperty("sortStrategyId").toString(), sortStrategy);
    }

    public void removeSortStrategy(ServiceReference<SortStrategy> sortStrategyRef) {
        if (sortStrategyRef == null) {
            return;
        }
        sortStrategies.remove(sortStrategyRef.getProperty("sortStrategyId").toString());
    }


    @Override
    public List<String> sort(Profile profile, Session session, ContextRequest.SortRequest sortRequest) {
        SortStrategy strategy = sortStrategies.get(sortRequest.getStrategy());

        if (strategy != null) {
            return strategy.sort(profile, session, sortRequest);
        }

        throw new IllegalArgumentException("Unknown strategy : "+sortRequest.getStrategy());
    }
}
