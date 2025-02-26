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

import org.apache.unomi.tracing.api.RequestTracer;
import org.apache.unomi.tracing.api.TraceNode;

import java.util.*;

/**
 * Default implementation of the RequestTracer interface that stores trace information in a tree structure
 */
public class DefaultRequestTracer implements RequestTracer {

    private final ThreadLocal<Boolean> enabled = ThreadLocal.withInitial(() -> false);
    private final ThreadLocal<TraceNode> currentNode = new ThreadLocal<>();
    private final ThreadLocal<TraceNode> rootNode = new ThreadLocal<>();
    private final ThreadLocal<Stack<TraceNode>> nodeStack = ThreadLocal.withInitial(Stack::new);

    @Override
    public void startOperation(String operationType, String description, Object context) {
        if (!isEnabled()) {
            return;
        }

        TraceNode node = new TraceNode();
        node.setOperationType(operationType);
        node.setDescription(description);
        node.setContext(context);
        node.setStartTime(System.currentTimeMillis());
        
        if (rootNode.get() == null) {
            rootNode.set(node);
            currentNode.set(node);
        } else {
            TraceNode parent = currentNode.get();
            parent.getChildren().add(node);
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
            node.setResult(result);
            node.setDescription(description);
            node.setEndTime(System.currentTimeMillis());

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
                node.getTraces().add(message + " - Context: " + context);
            } else {
                node.getTraces().add(message);
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
            node.getTraces().add("Validation against schema " + schemaId + ": " + validationMessages);
        }
    }

    @Override
    public TraceNode getTraceNode() {
        if (!isEnabled() || rootNode.get() == null) {
            return null;
        }
        return rootNode.get();
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