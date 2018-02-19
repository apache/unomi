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

import org.apache.unomi.metrics.CallerCount;
import org.apache.unomi.metrics.Metric;
import org.apache.unomi.metrics.MetricsService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MetricsServiceImpl implements MetricsService {

    boolean activated = false;
    Map<String,Metric> metrics = new ConcurrentHashMap<String,Metric>();
    Map<String,Boolean> callersStatus = new ConcurrentHashMap<>();

    public void setActivated(boolean activated) {
        this.activated = activated;
        if (!activated) {
            metrics.clear();
        }
    }

    @Override
    public boolean isActivated() {
        return activated;
    }

    @Override
    public Map<String, Metric> getMetrics() {
        return metrics;
    }

    @Override
    public void resetMetrics() {
        metrics.clear();
    }

    public void updateTimer(String timerName, long startTime) {
        if (!activated) {
            return;
        }
        long totalTime = System.currentTimeMillis() - startTime;
        Metric metric = metrics.get(timerName);
        if (metric == null) {
            metric = new MetricImpl(timerName);
            metrics.put(timerName, metric);
        }
        metric.incTotalCount();
        metric.addTotalTime(totalTime);
        if (isCallerActivated(timerName)) {
            StackTraceElement[] stackTraceElements = new Throwable().getStackTrace();
            List<String> stackTraces = new ArrayList<String>();
            if (stackTraceElements != null && stackTraceElements.length > 2) {
                // we start at index 2 to remove the internal
                for (int i = 2; i < stackTraceElements.length; i++) {
                    stackTraces.add(String.valueOf(stackTraceElements[i]));
                }
                String stackTraceHash = Integer.toString(stackTraces.hashCode());
                CallerCount callerCount = metric.getCallerCounts().get(stackTraceHash);
                if (callerCount == null) {
                    callerCount = new CallerCountImpl(stackTraceHash, stackTraces);
                    callerCount.incCount();
                    callerCount.addTime(totalTime);
                    metric.getCallerCounts().put(stackTraceHash, callerCount);
                } else {
                    callerCount.incCount();
                    callerCount.addTime(totalTime);
                }
            }
        }
    }

    @Override
    public Map<String, Boolean> getCallersStatus() {
        return callersStatus;
    }

    @Override
    public void setCallerActivated(String timerName, boolean activated) {
        if (!activated) {
            if (callersStatus.containsKey(timerName)) {
                callersStatus.remove(timerName);
            }
        } else {
            callersStatus.put(timerName, true);
        }
    }

    @Override
    public boolean isCallerActivated(String timerName) {
        if (callersStatus.containsKey(timerName)) {
            return callersStatus.get(timerName);
        }
        if (callersStatus.containsKey("*")) {
            return callersStatus.get("*");
        }
        return false;
    }

}
