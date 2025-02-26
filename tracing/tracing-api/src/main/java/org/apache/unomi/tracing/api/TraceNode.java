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
package org.apache.unomi.tracing.api;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a node in the request tracing tree structure.
 * Each node contains information about an operation, its timing, and any child operations.
 */
public class TraceNode implements Serializable {
    private String operationType;
    private String description;
    private Object context;
    private Object result;
    private long startTime;
    private long endTime;
    private List<String> traces;
    private List<TraceNode> children;

    public TraceNode() {
        this.traces = new ArrayList<>();
        this.children = new ArrayList<>();
    }

    public String getOperationType() {
        return operationType;
    }

    public void setOperationType(String operationType) {
        this.operationType = operationType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Object getContext() {
        return context;
    }

    public void setContext(Object context) {
        this.context = context;
    }

    public Object getResult() {
        return result;
    }

    public void setResult(Object result) {
        this.result = result;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    public long getDuration() {
        return endTime - startTime;
    }

    public List<String> getTraces() {
        return traces;
    }

    public void setTraces(List<String> traces) {
        this.traces = traces;
    }

    public List<TraceNode> getChildren() {
        return children;
    }

    public void setChildren(List<TraceNode> children) {
        this.children = children;
    }
} 