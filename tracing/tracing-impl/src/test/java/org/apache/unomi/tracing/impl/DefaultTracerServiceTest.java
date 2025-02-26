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
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link DefaultTracerService}
 */
public class DefaultTracerServiceTest {

    private DefaultTracerService tracerService;
    private static final int THREAD_COUNT = 10;
    private static final int TIMEOUT_SECONDS = 10;

    @Before
    public void setUp() {
        tracerService = new DefaultTracerService();
    }

    @After
    public void tearDown() {
        tracerService.cleanup();
    }

    @Test
    public void testGetCurrentTracer() {
        RequestTracer tracer = tracerService.getCurrentTracer();
        assertNotNull("Current tracer should not be null", tracer);
        assertTrue("Current tracer should be an instance of DefaultRequestTracer", tracer instanceof DefaultRequestTracer);
        
        // Test that we get the same tracer instance for the same thread
        RequestTracer secondTracer = tracerService.getCurrentTracer();
        assertSame("Should get same tracer instance within same thread", tracer, secondTracer);
    }

    @Test
    public void testEnableDisableTracing() {
        assertFalse("Tracing should be disabled by default", tracerService.isTracingEnabled());

        tracerService.enableTracing();
        assertTrue("Tracing should be enabled after enableTracing()", tracerService.isTracingEnabled());

        tracerService.disableTracing();
        assertFalse("Tracing should be disabled after disableTracing()", tracerService.isTracingEnabled());
        
        // Verify that enable/disable resets the trace
        tracerService.enableTracing();
        RequestTracer tracer = tracerService.getCurrentTracer();
        tracer.startOperation("test", "description", null);
        assertNotNull("Should have trace node after operation", tracerService.getTraceNode());
        
        tracerService.disableTracing();
        assertNull("Trace node should be reset after disable", tracerService.getTraceNode());
    }

    @Test
    public void testTracingOperations() {
        tracerService.enableTracing();
        RequestTracer tracer = tracerService.getCurrentTracer();

        // Start a root operation
        tracer.startOperation("test", "Root operation", null);
        long startTime = System.currentTimeMillis();
        
        // Add some traces
        tracer.trace("Test message", null);
        tracer.trace("Test with context", "context-data");

        // Start a child operation
        tracer.startOperation("child", "Child operation", "child-context");
        tracer.trace("Child trace", null);
        tracer.endOperation("child-result", "Child completed");

        // End root operation
        tracer.endOperation("root-result", "Root completed");
        long endTime = System.currentTimeMillis();

        // Get and verify the trace tree
        TraceNode rootNode = tracerService.getTraceNode();
        assertNotNull("Root node should not be null", rootNode);
        assertEquals("Root operation type should match", "test", rootNode.getOperationType());
        assertEquals("Root description should match", "Root completed", rootNode.getDescription());
        assertEquals("Root result should match", "root-result", rootNode.getResult());
        assertEquals("Root should have 2 traces", 2, rootNode.getTraces().size());
        assertEquals("Root should have 1 child", 1, rootNode.getChildren().size());
        assertTrue("Root start time should be valid", rootNode.getStartTime() >= startTime);
        assertTrue("Root end time should be valid", rootNode.getEndTime() <= endTime);

        TraceNode childNode = rootNode.getChildren().get(0);
        assertEquals("Child operation type should match", "child", childNode.getOperationType());
        assertEquals("Child description should match", "Child completed", childNode.getDescription());
        assertEquals("Child context should match", "child-context", childNode.getContext());
        assertEquals("Child result should match", "child-result", childNode.getResult());
        assertEquals("Child should have 1 trace", 1, childNode.getTraces().size());
        assertTrue("Child start time should be after root", childNode.getStartTime() >= rootNode.getStartTime());
        assertTrue("Child end time should be before root end", childNode.getEndTime() <= rootNode.getEndTime());
    }

    @Test
    public void testValidationInfo() {
        tracerService.enableTracing();
        RequestTracer tracer = tracerService.getCurrentTracer();

        tracer.startOperation("validation", "Validation test", null);
        tracer.addValidationInfo(Arrays.asList("error1", "error2"), "test-schema");
        tracer.endOperation(false, "Validation failed");

        TraceNode node = tracerService.getTraceNode();
        assertNotNull("Node should not be null", node);
        assertEquals("Should have 1 validation trace", 1, node.getTraces().size());
        String validationTrace = node.getTraces().get(0);
        assertTrue("Validation trace should contain schema id", validationTrace.contains("test-schema"));
        assertTrue("Validation trace should contain first error", validationTrace.contains("error1"));
        assertTrue("Validation trace should contain second error", validationTrace.contains("error2"));
    }

    @Test
    public void testTracingDisabled() {
        RequestTracer tracer = tracerService.getCurrentTracer();

        // Operations should not create any nodes when tracing is disabled
        tracer.startOperation("test", "Test operation", null);
        tracer.trace("Test message", null);
        tracer.addValidationInfo(Arrays.asList("error"), "schema");
        tracer.endOperation("result", "Completed");

        assertNull("No trace node should be created when tracing is disabled", tracerService.getTraceNode());
    }

    @Test
    public void testReset() {
        tracerService.enableTracing();
        RequestTracer tracer = tracerService.getCurrentTracer();

        tracer.startOperation("test", "Test operation", null);
        tracer.trace("Test message", null);
        assertNotNull("Should have trace node before reset", tracerService.getTraceNode());

        tracer.reset();
        assertNull("Should not have trace node after reset", tracerService.getTraceNode());
        
        // Verify that tracing is still enabled after reset
        assertTrue("Tracing should still be enabled after reset", tracerService.isTracingEnabled());
    }

    @Test
    public void testCleanup() {
        tracerService.enableTracing();
        RequestTracer tracer = tracerService.getCurrentTracer();

        tracer.startOperation("test", "Test operation", null);
        RequestTracer originalTracer = tracerService.getCurrentTracer();

        tracerService.cleanup();
        RequestTracer newTracer = tracerService.getCurrentTracer();

        assertNotSame("Should get new tracer instance after cleanup", originalTracer, newTracer);
        assertFalse("New tracer should be disabled", newTracer.isEnabled());
        assertNull("New tracer should have no trace node", tracerService.getTraceNode());
    }

    @Test
    public void testThreadSafety() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(THREAD_COUNT);

        try {
            // Create tasks that will all start simultaneously
            for (int i = 0; i < THREAD_COUNT; i++) {
                final int threadId = i;
                executor.submit(() -> {
                    try {
                        startLatch.await(); // Wait for all threads to be ready
                        
                        RequestTracer tracer = tracerService.getCurrentTracer();
                        tracer.setEnabled(true);
                        
                        // Perform operations specific to this thread
                        tracer.startOperation("thread-" + threadId, "Thread operation", null);
                        tracer.trace("Thread message", "Thread-" + threadId);
                        tracer.endOperation("success", "Thread completed");
                        
                        // Verify this thread's trace
                        TraceNode node = tracer.getTraceNode();
                        assertNotNull("Thread " + threadId + " should have a trace node", node);
                        assertEquals("Thread " + threadId + " should have correct operation type", 
                                "thread-" + threadId, node.getOperationType());
                        
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Thread interrupted during test", e);
                    } finally {
                        completionLatch.countDown();
                    }
                });
            }

            // Start all threads simultaneously
            startLatch.countDown();
            
            // Wait for all threads to complete
            assertTrue("All threads should complete within timeout", 
                    completionLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        } finally {
            executor.shutdown();
            if (!executor.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        }
    }

    @Test
    public void testNullHandling() {
        tracerService.enableTracing();
        RequestTracer tracer = tracerService.getCurrentTracer();

        // Test null handling in various operations
        tracer.startOperation(null, null, null);
        tracer.trace(null, null);
        tracer.addValidationInfo(null, null);
        tracer.endOperation(null, null);

        TraceNode node = tracerService.getTraceNode();
        assertNotNull("Node should exist even with null values", node);
        assertNull("Operation type should be null", node.getOperationType());
        assertNull("Description should be null", node.getDescription());
        assertNull("Context should be null", node.getContext());
        assertNull("Result should be null", node.getResult());
    }
} 