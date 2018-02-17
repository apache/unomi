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
package org.apache.unomi.metrics;

/**
 * Utility method to run code inside a timer.
 * @param <T> the type to be used as a result type for the method.
 */
public abstract class MetricAdapter<T> {

    private MetricsService metricsService;
    private String timerName;

    public abstract T execute(Object... args) throws Exception;

    public MetricAdapter(MetricsService metricsService, String timerName) {
        this.metricsService = metricsService;
        this.timerName = timerName;
    }

    public T runWithTimer(Object... args) throws Exception {
        long startTime = System.currentTimeMillis();
        try {
            return execute(args);
        } finally {
            if (metricsService != null && metricsService.isActivated()) {
                metricsService.updateTimer(timerName, startTime);
            }
        }
    }
}
