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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link DefaultTracerService}
 */
public class DefaultTracerServiceTest {

    private DefaultTracerService tracerService;
    private static final int THREAD_COUNT = 10;
    private static final int TIMEOUT_SECONDS = 10;

    @BeforeEach
    public void setUp() {
        tracerService = new DefaultTracerService();
    }

    @AfterEach
    public void tearDown() {
        tracerService.cleanup();
    }

    @Test
    public void testGetCurrentTracer() {
        RequestTracer tracer = tracerService.getCurrentTracer();
        assertNotNull(tracer, "Current tracer should not be null");
        assertTrue(tracer instanceof DefaultRequestTracer, "Current tracer should be an instance of DefaultRequestTracer");
        
        // Test that we get the same tracer instance for the same thread
        RequestTracer secondTracer = tracerService.getCurrentTracer();
        assertSame(tracer, secondTracer, "Should get same tracer instance within same thread");
    }

    @Test
    public void testEnableDisableTracing() {
        assertFalse(tracerService.isTracingEnabled(), "Tracing should be disabled by default");

        tracerService.enableTracing();
        assertTrue(tracerService.isTracingEnabled(), "Tracing should be enabled after enableTracing()");

        tracerService.disableTracing();
        assertFalse(tracerService.isTracingEnabled(), "Tracing should be disabled after disableTracing()");
        
        // Verify that enable/disable resets the trace
        tracerService.enableTracing();
        RequestTracer tracer = tracerService.getCurrentTracer();
        tracer.startOperation("test", "description", null);
        assertNotNull(tracerService.getTraceNode(), "Should have trace node after operation");
        
        tracerService.disableTracing();
        assertNull(tracerService.getTraceNode(), "Trace node should be reset after disable");
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
        assertNotNull(rootNode, "Root node should not be null");
        assertEquals("test", rootNode.getOperationType(), "Root operation type should match");
        assertEquals("Root completed", rootNode.getDescription(), "Root description should match");
        assertEquals("root-result", rootNode.getResult(), "Root result should match");
        assertEquals(2, rootNode.getTraces().size(), "Root should have 2 traces");
        assertEquals(1, rootNode.getChildren().size(), "Root should have 1 child");
        assertTrue(rootNode.getStartTime() >= startTime, "Root start time should be valid");
        assertTrue(rootNode.getEndTime() <= endTime, "Root end time should be valid");

        TraceNode childNode = rootNode.getChildren().get(0);
        assertEquals("child", childNode.getOperationType(), "Child operation type should match");
        assertEquals("Child completed", childNode.getDescription(), "Child description should match");
        assertEquals("child-context", childNode.getContext(), "Child context should match");
        assertEquals("child-result", childNode.getResult(), "Child result should match");
        assertEquals(1, childNode.getTraces().size(), "Child should have 1 trace");
        assertTrue(childNode.getStartTime() >= rootNode.getStartTime(), "Child start time should be after root");
        assertTrue(childNode.getEndTime() <= rootNode.getEndTime(), "Child end time should be before root end");
    }

    @Test
    public void testValidationInfo() {
        tracerService.enableTracing();
        RequestTracer tracer = tracerService.getCurrentTracer();

        tracer.startOperation("validation", "Validation test", null);
        tracer.addValidationInfo(Arrays.asList("error1", "error2"), "test-schema");
        tracer.endOperation(false, "Validation failed");

        TraceNode node = tracerService.getTraceNode();
        assertNotNull(node, "Node should not be null");
        assertEquals(1, node.getTraces().size(), "Should have 1 validation trace");
        String validationTrace = node.getTraces().get(0);
        assertTrue(validationTrace.contains("test-schema"), "Validation trace should contain schema id");
        assertTrue(validationTrace.contains("error1"), "Validation trace should contain first error");
        assertTrue(validationTrace.contains("error2"), "Validation trace should contain second error");
    }

    @Test
    public void testTracingDisabled() {
        RequestTracer tracer = tracerService.getCurrentTracer();

        // Operations should not create any nodes when tracing is disabled
        tracer.startOperation("test", "Test operation", null);
        tracer.trace("Test message", null);
        tracer.addValidationInfo(Arrays.asList("error"), "schema");
        tracer.endOperation("result", "Completed");

        assertNull(tracerService.getTraceNode(), "No trace node should be created when tracing is disabled");
    }

    @Test
    public void testReset() {
        tracerService.enableTracing();
        RequestTracer tracer = tracerService.getCurrentTracer();

        tracer.startOperation("test", "Test operation", null);
        tracer.trace("Test message", null);
        assertNotNull(tracerService.getTraceNode(), "Should have trace node before reset");

        tracer.reset();
        assertNull(tracerService.getTraceNode(), "Should not have trace node after reset");
        
        // Verify that tracing is still enabled after reset
        assertTrue(tracerService.isTracingEnabled(), "Tracing should still be enabled after reset");
    }

    @Test
    public void testCleanup() {
        tracerService.enableTracing();
        RequestTracer tracer = tracerService.getCurrentTracer();

        tracer.startOperation("test", "Test operation", null);
        RequestTracer originalTracer = tracerService.getCurrentTracer();

        tracerService.cleanup();
        RequestTracer newTracer = tracerService.getCurrentTracer();

        assertNotSame(originalTracer, newTracer, "Should get new tracer instance after cleanup");
        assertFalse(newTracer.isEnabled(), "New tracer should be disabled");
        assertNull(tracerService.getTraceNode(), "New tracer should have no trace node");
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
                        assertNotNull(node, "Thread " + threadId + " should have a trace node");
                        assertEquals("thread-" + threadId, node.getOperationType(),
                                "Thread " + threadId + " should have correct operation type");
                        
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
            assertTrue(completionLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS),
                    "All threads should complete within timeout");
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
        assertNotNull(node, "Node should exist even with null values");
        assertNull(node.getOperationType(), "Operation type should be null");
        assertNull(node.getDescription(), "Description should be null");
        assertNull(node.getContext(), "Context should be null");
        assertNull(node.getResult(), "Result should be null");
    }

    @Test
    public void testTraceShouldNotFailWhenContextToStringOverflowsStack() {
        tracerService.enableTracing();
        RequestTracer tracer = tracerService.getCurrentTracer();

        tracer.startOperation("test", "Root operation", null);
        Object badContext = new Object() {
            @Override
            public String toString() {
                return toString();
            }
        };

        assertDoesNotThrow(() -> tracer.trace("Test with bad context", badContext),
                "Tracer.trace should not throw even if context.toString overflows the stack");

        TraceNode rootNode = tracerService.getTraceNode();
        assertNotNull(rootNode, "Root node should be created");
        assertEquals(1, rootNode.getTraces().size(), "Trace should be recorded even when context rendering fails");
        assertTrue(rootNode.getTraces().get(0).contains("StackOverflowError"),
                "Trace should contain a StackOverflowError marker when context rendering overflows");
    }
} 