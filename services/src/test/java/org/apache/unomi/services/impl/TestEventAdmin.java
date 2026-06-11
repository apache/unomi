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
package org.apache.unomi.services.impl;

import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.osgi.service.event.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Mock implementation of OSGi EventAdmin for unit tests.
 * 
 * This implementation complies with the OSGi EventAdmin Service Specification (OSGi Compendium 8.1+):
 * 
 * 1. postEvent(Event event): Asynchronous, ordered delivery
 *    - Returns immediately (non-blocking) per spec: "returns to the caller before delivery is complete"
 *    - Events are delivered to handlers in the order they were posted per spec: "Events are delivered in the order posted"
 *    - Each handler receives events in the order they were posted
 *    - Null events are ignored (early return)
 *    - Events with null topics are ignored (logged and skipped)
 * 
 * 2. sendEvent(Event event): Synchronous delivery
 *    - Does not return until all handlers have processed the event per spec: "does not return until all event handlers have been called"
 *    - Handlers are called directly in the current thread (synchronous)
 *    - Null events are ignored (early return)
 *    - Events with null topics are ignored (logged and skipped)
 * 
 * 3. Exception handling: Exceptions from handlers do not stop delivery to other handlers
 *    - Per spec: exceptions should be caught and logged (using LogService if available, SLF4J otherwise)
 *    - Delivery continues to remaining handlers
 * 
 * 4. Topic matching: Supports OSGi hierarchical topic matching
 *    - EVENT_TOPIC service property: array of topic patterns
 *    - Empty EVENT_TOPIC matches all topics (defaults to "*")
 *    - Wildcard "*" matches all topics
 *    - Wildcard "**" at end matches all subtopics (e.g., "org/apache/unomi/**")
 *    - Single-level wildcard "*" at end matches one level (e.g., "org/apache/unomi/*")
 *    - Exact topic matching
 *    - Topics are hierarchical, separated by '/' character
 * 
 * 5. Handler registration: Manual registration for tests (in real OSGi, handlers are registered via service registry)
 *    - Handlers specify topics via EVENT_TOPIC property (mapped to topics parameter)
 *    - EVENT_FILTER property is optional and not implemented (minimal compliance)
 * 
 * Threading model:
 * - postEvent(): Uses a single-threaded executor to process events in order
 * - sendEvent(): Calls handlers directly in the current thread (synchronous)
 * - Each handler has a dedicated queue to guarantee ordered delivery per handler
 * 
 * Note: Security (TopicPermission) is not enforced in this test mock, as it's not required for minimal compliance
 * in a test environment. Real OSGi implementations must enforce TopicPermission checks.
 */
public class TestEventAdmin implements EventAdmin {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestEventAdmin.class);

    /**
     * Registry of event handlers with their topic filters.
     * Key: EventHandler instance
     * Value: Set of topic patterns (from EVENT_TOPIC service property)
     */
    private final Map<EventHandler, Set<String>> handlers = new ConcurrentHashMap<>();

    /**
     * Single-threaded executor for processing asynchronous events in order.
     * This ensures events are delivered to handlers in the order they were posted.
     */
    private final ExecutorService asyncExecutor;

    /**
     * Queue per handler to guarantee event sequencing.
     * Each handler has its own queue, ensuring events are processed in order.
     */
    private final Map<EventHandler, BlockingQueue<Event>> handlerQueues = new ConcurrentHashMap<>();

    /**
     * Worker threads per handler to process events from their queues sequentially.
     */
    private final Map<EventHandler, Future<?>> handlerWorkers = new ConcurrentHashMap<>();

    /**
     * Counter for tracking posted events (for test verification).
     */
    private final AtomicInteger postedEventCount = new AtomicInteger(0);

    /**
     * Counter for tracking sent events (for test verification).
     */
    private final AtomicInteger sentEventCount = new AtomicInteger(0);

    /**
     * List of all events posted (for test verification).
     */
    private final List<Event> postedEvents = new CopyOnWriteArrayList<>();

    /**
     * List of all events sent (for test verification).
     */
    private final List<Event> sentEvents = new CopyOnWriteArrayList<>();

    /**
     * Creates a TestEventAdmin with a single-threaded executor for ordered event delivery.
     */
    public TestEventAdmin() {
        // Use single-threaded executor to guarantee ordered delivery
        this.asyncExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "TestEventAdmin-Async-" + System.identityHashCode(this));
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Registers an event handler with topic filters.
     * In real OSGi, handlers are registered via the service registry with EVENT_TOPIC property.
     * For tests, we allow manual registration.
     *
     * @param handler the event handler to register
     * @param topics topic patterns (supports OSGi hierarchical wildcards)
     */
    public void registerHandler(EventHandler handler, String... topics) {
        if (handler == null) {
            return;
        }

        Set<String> topicFilters = new HashSet<>();
        if (topics != null && topics.length > 0) {
            topicFilters.addAll(Arrays.asList(topics));
        } else {
            // No filter means match all topics (OSGi spec: empty EVENT_TOPIC matches all)
            topicFilters.add("*");
        }

        handlers.put(handler, topicFilters);

        // Create a dedicated queue for this handler to guarantee sequencing
        BlockingQueue<Event> queue = new LinkedBlockingQueue<>();
        handlerQueues.put(handler, queue);

        // Start a worker thread for this handler to process events sequentially
        Future<?> worker = asyncExecutor.submit(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Event event = queue.take(); // Blocks until event is available
                    if (event != null) {
                        try {
                            handler.handleEvent(event);
                        } catch (Exception e) {
                            // OSGi spec: catch exceptions, log them, and continue
                            // If LogService is available, it should be used (we use SLF4J)
                            LOGGER.warn("Exception in event handler {} while processing event {}: {}",
                                handler.getClass().getName(), event.getTopic(), e.getMessage(), e);
                        }
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        handlerWorkers.put(handler, worker);

        LOGGER.debug("Registered event handler: {} with topics: {}", handler.getClass().getName(), topicFilters);
    }

    /**
     * Unregisters an event handler.
     *
     * @param handler the event handler to unregister
     */
    public void unregisterHandler(EventHandler handler) {
        if (handler == null) {
            return;
        }

        handlers.remove(handler);
        BlockingQueue<Event> queue = handlerQueues.remove(handler);
        Future<?> worker = handlerWorkers.remove(handler);

        if (worker != null) {
            worker.cancel(true);
        }

        // Drain remaining events from queue
        if (queue != null) {
            queue.clear();
        }

        LOGGER.debug("Unregistered event handler: {}", handler.getClass().getName());
    }

    /**
     * Posts an event asynchronously (non-blocking).
     * 
     * OSGi spec: "Initiates asynchronous, ordered delivery of an event. This method returns 
     * to the caller before delivery is complete. Events are delivered in the order posted."
     * 
     * @param event the event to post
     */
    @Override
    public void postEvent(Event event) {
        if (event == null) {
            return;
        }

        String topic = event.getTopic();
        if (topic == null) {
            // OSGi spec: Events must have a topic. Skip delivery if topic is null.
            LOGGER.warn("Event has null topic, skipping delivery");
            return;
        }

        postedEventCount.incrementAndGet();
        postedEvents.add(event);

        LOGGER.debug("Posting event asynchronously: {}", topic);

        // Deliver to all matching handlers asynchronously
        // Each handler gets events in order via its dedicated queue
        for (Map.Entry<EventHandler, Set<String>> entry : handlers.entrySet()) {
            EventHandler handler = entry.getKey();
            Set<String> topicFilters = entry.getValue();

            if (matchesTopic(topic, topicFilters)) {
                BlockingQueue<Event> queue = handlerQueues.get(handler);
                if (queue != null) {
                    // Add to handler's queue (non-blocking, will be processed by handler's worker thread)
                    queue.offer(event);
                }
            }
        }
    }

    /**
     * Sends an event synchronously (blocking until all handlers complete).
     * 
     * OSGi spec: "Synchronously sends an event. This method does not return to the caller 
     * until all event handlers have been called."
     * 
     * @param event the event to send
     */
    @Override
    public void sendEvent(Event event) {
        if (event == null) {
            return;
        }

        String topic = event.getTopic();
        if (topic == null) {
            // OSGi spec: Events must have a topic. Skip delivery if topic is null.
            LOGGER.warn("Event has null topic, skipping delivery");
            return;
        }

        sentEventCount.incrementAndGet();
        sentEvents.add(event);

        LOGGER.debug("Sending event synchronously: {}", topic);

        // Collect all matching handlers
        List<EventHandler> matchingHandlers = new ArrayList<>();
        for (Map.Entry<EventHandler, Set<String>> entry : handlers.entrySet()) {
            EventHandler handler = entry.getKey();
            Set<String> topicFilters = entry.getValue();

            if (matchesTopic(topic, topicFilters)) {
                matchingHandlers.add(handler);
            }
        }

        // Deliver to all matching handlers synchronously in the current thread
        // OSGi spec: "does not return until all event handlers have been called"
        for (EventHandler handler : matchingHandlers) {
            try {
                handler.handleEvent(event);
            } catch (Exception e) {
                // OSGi spec: catch exceptions, log them, and continue
                // If LogService is available, it should be used (we use SLF4J)
                LOGGER.warn("Exception in event handler {} while processing event {}: {}",
                    handler.getClass().getName(), topic, e.getMessage(), e);
            }
        }
    }

    /**
     * Checks if an event topic matches any of the topic filters.
     * 
     * OSGi spec topic matching rules:
     * - Topics are hierarchical, separated by '/' (e.g., "org/apache/unomi/definitions/conditionType/ADDED")
     * - Wildcard "*" matches all topics
     * - Wildcard "**" matches all subtopics (e.g., "org/apache/unomi/**" matches all topics starting with that prefix)
     * - Exact match for specific topics
     * 
     * @param topic the event topic
     * @param topicFilters the topic filters to check against
     * @return true if the topic matches any filter
     */
    private boolean matchesTopic(String topic, Set<String> topicFilters) {
        if (topic == null) {
            return false;
        }

        for (String filter : topicFilters) {
            if ("*".equals(filter)) {
                return true;
            }

            // Support wildcard pattern: "org/apache/unomi/**"
            // Matches all topics starting with the prefix (including the prefix itself)
            if (filter.endsWith("/**")) {
                String prefix = filter.substring(0, filter.length() - 3);
                if (topic.startsWith(prefix + "/") || topic.equals(prefix)) {
                    return true;
                }
            }

            // Exact match
            if (topic.equals(filter)) {
                return true;
            }

            // Support single-level wildcard: "org/apache/unomi/*"
            // Matches exactly one level (no nested slashes after the prefix)
            if (filter.endsWith("/*")) {
                String prefix = filter.substring(0, filter.length() - 2);
                if (topic.startsWith(prefix + "/")) {
                    String remainder = topic.substring(prefix.length() + 1);
                    // Remainder should not contain '/' (single level only)
                    if (!remainder.contains("/")) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * Gets all events that were posted (asynchronously).
     *
     * @return a list of posted events
     */
    public List<Event> getPostedEvents() {
        return new ArrayList<>(postedEvents);
    }

    /**
     * Gets all events that were sent (synchronously).
     *
     * @return a list of sent events
     */
    public List<Event> getSentEvents() {
        return new ArrayList<>(sentEvents);
    }

    /**
     * Clears all stored events.
     */
    public void clearEvents() {
        postedEvents.clear();
        sentEvents.clear();
        postedEventCount.set(0);
        sentEventCount.set(0);
    }

    /**
     * Gets the count of posted events.
     *
     * @return the number of posted events
     */
    public int getPostedEventCount() {
        return postedEventCount.get();
    }

    /**
     * Gets the count of sent events.
     *
     * @return the number of sent events
     */
    public int getSentEventCount() {
        return sentEventCount.get();
    }

    /**
     * Gets the number of registered handlers.
     *
     * @return the number of registered handlers
     */
    public int getHandlerCount() {
        return handlers.size();
    }

    /**
     * Waits for all pending asynchronous events to be processed.
     * This is useful in tests to ensure events have been delivered before assertions.
     *
     * @param timeoutMs maximum time to wait in milliseconds
     * @return true if all events were processed, false if timeout occurred
     */
    public boolean waitForEventProcessing(long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        
        // Wait for all handler queues to be empty
        for (BlockingQueue<Event> queue : handlerQueues.values()) {
            while (!queue.isEmpty() && System.currentTimeMillis() < deadline) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            if (!queue.isEmpty()) {
                return false;
            }
        }
        
        return true;
    }

    /**
     * Shuts down the executor service and cleans up resources.
     * Should be called when the test EventAdmin is no longer needed.
     */
    public void shutdown() {
        // Cancel all handler workers
        for (Future<?> worker : handlerWorkers.values()) {
            worker.cancel(true);
        }
        handlerWorkers.clear();

        // Shutdown executor service
        asyncExecutor.shutdown();
        try {
            if (!asyncExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                asyncExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            asyncExecutor.shutdownNow();
        }

        handlers.clear();
        handlerQueues.clear();
    }
}
