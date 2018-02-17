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
package org.apache.unomi.metrics.internal;

import org.apache.unomi.metrics.CalleeCount;
import org.apache.unomi.metrics.Metric;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class MetricImpl implements Metric {

    private String name;
    private long totalCount = 0L;
    private long totalTime = 0L;
    private Map<String,CalleeCount> calleeCounts = new ConcurrentHashMap<String, CalleeCount>();

    public MetricImpl(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public long getTotalCount() {
        return totalCount;
    }

    @Override
    public long incTotalCount() {
        return totalCount++;
    }

    @Override
    public long getTotalTime() {
        return totalTime;
    }

    @Override
    public long addTotalTime(long time) {
        return totalTime += time;
    }

    @Override
    public Map<String, CalleeCount> getCalleeCounts() {
        return calleeCounts;
    }
}
