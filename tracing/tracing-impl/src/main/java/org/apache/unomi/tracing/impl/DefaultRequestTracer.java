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
package org.apache.unomi.tracing.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.unomi.tracing.api.RequestTracer;

import java.util.*;

/**
 * Default implementation of the RequestTracer interface that stores trace information in a tree structure
 */
public class DefaultRequestTracer implements RequestTracer {

    private static final ObjectMapper objectMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private final ThreadLocal<Boolean> enabled = ThreadLocal.withInitial(() -> false);
    private final ThreadLocal<TraceNode> currentNode = new ThreadLocal<>();
    private final ThreadLocal<TraceNode> rootNode = new ThreadLocal<>();
    private final ThreadLocal<Stack<TraceNode>> nodeStack = ThreadLocal.withInitial(Stack::new);

    private static class TraceNode {
        String operationType;
        String description;
        Object context;
        Object result;
        long startTime;
        long endTime;
        List<String> traces;
        List<TraceNode> children;

        TraceNode(String operationType, String description, Object context) {
            this.operationType = operationType;
            this.description = description;
            this.context = context;
            this.startTime = System.currentTimeMillis();
            this.traces = new ArrayList<>();
            this.children = new ArrayList<>();
        }

        Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("operationType", operationType);
            map.put("description", description);
            if (context != null) {
                map.put("context", context);
            }
            if (result != null) {
                map.put("result", result);
            }
            map.put("duration", endTime - startTime);
            if (!traces.isEmpty()) {
                map.put("traces", traces);
            }
            if (!children.isEmpty()) {
                List<Map<String, Object>> childMaps = new ArrayList<>();
                for (TraceNode child : children) {
                    childMaps.add(child.toMap());
                }
                map.put("children", childMaps);
            }
            return map;
        }
    }

    @Override
    public void startOperation(String operationType, String description, Object context) {
        if (!isEnabled()) {
            return;
        }

        TraceNode node = new TraceNode(operationType, description, context);
        
        if (rootNode.get() == null) {
            rootNode.set(node);
            currentNode.set(node);
        } else {
            TraceNode parent = currentNode.get();
            parent.children.add(node);
            nodeStack.get().push(currentNode.get());
            currentNode.set(node);
        }
    }

    @Override
    public void endOperation(Object result, String description) {
        if (!isEnabled()) {
            return;
        }

        TraceNode node = currentNode.get();
        if (node != null) {
            node.result = result;
            node.description = description;
            node.endTime = System.currentTimeMillis();

            if (!nodeStack.get().isEmpty()) {
                currentNode.set(nodeStack.get().pop());
            }
        }
    }

    @Override
    public void trace(String message, Object context) {
        if (!isEnabled()) {
            return;
        }

        TraceNode node = currentNode.get();
        if (node != null) {
            if (context != null) {
                node.traces.add(message + " - Context: " + context);
            } else {
                node.traces.add(message);
            }
        }
    }

    @Override
    public void addValidationInfo(Collection<?> validationMessages, String schemaId) {
        if (!isEnabled()) {
            return;
        }

        TraceNode node = currentNode.get();
        if (node != null) {
            node.traces.add("Validation against schema " + schemaId + ": " + validationMessages);
        }
    }

    @Override
    public String getTraceAsJson() {
        if (!isEnabled() || rootNode.get() == null) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(rootNode.get().toMap());
        } catch (Exception e) {
            return "{\"error\": \"Failed to serialize trace: " + e.getMessage() + "\"}";
        }
    }

    @Override
    public boolean isEnabled() {
        return enabled.get();
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled.set(enabled);
    }

    @Override
    public void reset() {
        rootNode.remove();
        currentNode.remove();
        nodeStack.get().clear();
    }
} 