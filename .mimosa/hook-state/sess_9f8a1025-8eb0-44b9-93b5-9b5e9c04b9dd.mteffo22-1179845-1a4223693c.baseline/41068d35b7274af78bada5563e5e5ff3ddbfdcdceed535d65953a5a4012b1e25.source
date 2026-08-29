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
 * limitations under the License
 */
package org.apache.unomi.extensions.log4j;

import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Core;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.layout.PatternLayout;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Custom Log4j2 appender that captures log events in memory for test log checking.
 * This appender is designed to work with PaxExam/Karaf integration tests.
 * 
 * Note: This appender is included in the log4j-extension fragment bundle, which
 * attaches to the Pax Logging Log4j2 bundle, ensuring it's available early in the
 * startup process. It's only configured in integration tests, not in the default package.
 * 
 * The appender uses a lock-free bounded buffer to prevent memory leaks while minimizing
 * contention. When the buffer exceeds the maximum size, older events are automatically evicted.
 * The default maximum size is 100,000 events, which should be sufficient for most test scenarios.
 * 
 * Performance optimizations:
 * - Lock-free append path using ConcurrentLinkedQueue
 * - Atomic counters for size tracking
 * - Minimal synchronization only for infrequent operations (clear, get all events)
 * - Read operations use lock-free iteration
 */
@Plugin(name = "InMemoryLogAppender", category = Core.CATEGORY_NAME, elementType = Appender.ELEMENT_TYPE, printObject = true)
public class InMemoryLogAppender extends AbstractAppender {

    private static final int DEFAULT_MAX_EVENTS = 100000;
    // Lock-free queue for maximum append performance
    private static final ConcurrentLinkedQueue<LogEvent> capturedEvents = new ConcurrentLinkedQueue<>();
    // Atomic counters for lock-free size tracking
    private static final AtomicInteger currentSize = new AtomicInteger(0);
    private static final AtomicLong totalEventsAdded = new AtomicLong(0);
    private static final AtomicLong totalEventsEvicted = new AtomicLong(0);
    private static volatile boolean enabled = true;
    private static volatile int maxEvents = DEFAULT_MAX_EVENTS;

    protected InMemoryLogAppender(String name, Filter filter, Layout<? extends Serializable> layout, boolean ignoreExceptions, Property[] properties) {
        super(name, filter, layout, ignoreExceptions, properties);
    }

    @PluginFactory
    public static InMemoryLogAppender createAppender(
            @PluginAttribute("name") String name,
            @PluginElement("Filter") Filter filter,
            @PluginElement("Layout") Layout<? extends Serializable> layout,
            @PluginAttribute("ignoreExceptions") boolean ignoreExceptions) {
        if (name == null) {
            LOGGER.error("No name provided for InMemoryLogAppender");
            return null;
        }
        if (layout == null) {
            layout = PatternLayout.createDefaultLayout();
        }
        return new InMemoryLogAppender(name, filter, layout, ignoreExceptions, null);
    }

    @Override
    public void append(LogEvent event) {
        // Fast path: check enabled flag first (volatile read, no lock)
        if (!enabled) {
            return;
        }
        
        // Create a copy of the event to avoid issues with event reuse
        LogEvent immutableEvent = event.toImmutable();
        
        // Lock-free add to queue (always succeeds with ConcurrentLinkedQueue)
        capturedEvents.offer(immutableEvent);
        int newSize = currentSize.incrementAndGet();
        totalEventsAdded.incrementAndGet();
        
        // Evict old events if we exceed the maximum size
        // This is done asynchronously to avoid blocking the append path
        if (newSize > maxEvents) {
            evictOldEvents();
        }
    }
    
    /**
     * Evict old events to maintain the maximum size limit.
     * This method is lock-free and only evicts when necessary.
     */
    private static void evictOldEvents() {
        // Calculate how many events to evict
        int current = currentSize.get();
        int toEvict = current - maxEvents;
        
        if (toEvict <= 0) {
            return;
        }
        
        // Evict oldest events (lock-free)
        int evicted = 0;
        while (evicted < toEvict) {
            LogEvent evictedEvent = capturedEvents.poll();
            if (evictedEvent == null) {
                // Queue is empty (shouldn't happen, but handle gracefully)
                break;
            }
            evicted++;
        }
        
        if (evicted > 0) {
            currentSize.addAndGet(-evicted);
            totalEventsEvicted.addAndGet(evicted);
        }
    }

    /**
     * Get all captured log events
     * Note: This returns events in insertion order, but may not include all events
     * if the buffer was full and events were evicted.
     * This operation uses lock-free iteration for minimal contention.
     */
    public static List<LogEvent> getCapturedEvents() {
        // Lock-free iteration - ConcurrentLinkedQueue.iterator() is thread-safe
        List<LogEvent> events = new ArrayList<>();
        for (LogEvent event : capturedEvents) {
            events.add(event);
        }
        return Collections.unmodifiableList(events);
    }

    /**
     * Clear all captured events
     * Note: This operation requires synchronization to ensure atomicity,
     * but it's infrequent (typically only at test setup/teardown).
     */
    public static void clearEvents() {
        // Synchronize only for clear operation (infrequent)
        synchronized (capturedEvents) {
            capturedEvents.clear();
            currentSize.set(0);
            totalEventsAdded.set(0);
            totalEventsEvicted.set(0);
        }
    }

    /**
     * Get events captured since a specific index
     * Note: The index is relative to the total number of events added, not the current buffer size.
     * If events were evicted and the startIndex is before the oldest available event, 
     * an empty list is returned (checkpoint was lost due to buffer overflow).
     * 
     * This operation uses lock-free iteration for minimal contention.
     * 
     * @param startIndex The index of the first event to return (0-based, relative to total events added)
     * @return List of events since the start index, or empty list if checkpoint was lost
     */
    public static List<LogEvent> getEventsSince(int startIndex) {
        if (startIndex < 0) {
            return Collections.emptyList();
        }
        
        // Lock-free reads of atomic counters
        long currentTotal = totalEventsAdded.get();
        int bufferSize = currentSize.get();
        long oldestAvailableIndex = currentTotal - bufferSize;
        
        // If the startIndex is before the oldest available event, the checkpoint was lost
        if (startIndex < oldestAvailableIndex) {
            // Checkpoint was lost due to buffer overflow
            LOGGER.warn("Checkpoint index {} is before oldest available event {} (buffer overflow detected). " +
                       "Total events: {}, Buffer size: {}, Evicted: {}",
                       startIndex, oldestAvailableIndex, currentTotal, bufferSize, totalEventsEvicted.get());
            return Collections.emptyList();
        }
        
        // Calculate the actual start index in the buffer
        int actualStartIndex = (int) (startIndex - oldestAvailableIndex);
        
        if (actualStartIndex >= bufferSize) {
            // Start index is beyond the available events (shouldn't happen, but handle gracefully)
            return Collections.emptyList();
        }
        
        // Lock-free iteration - ConcurrentLinkedQueue.iterator() is thread-safe
        List<LogEvent> events = new ArrayList<>();
        int index = 0;
        for (LogEvent event : capturedEvents) {
            if (index >= actualStartIndex) {
                events.add(event);
            }
            index++;
        }
        
        return Collections.unmodifiableList(events);
    }

    /**
     * Get the current event count (can be used as a checkpoint)
     * Note: This returns the total number of events added, not the current buffer size.
     * If events were evicted, the buffer size will be less than this count.
     */
    public static int getEventCount() {
        return (int) totalEventsAdded.get();
    }

    /**
     * Get the current buffer size (number of events currently stored)
     * Note: This uses an atomic counter for lock-free reads.
     */
    public static int getBufferSize() {
        return currentSize.get();
    }

    /**
     * Get the total number of events that have been evicted due to buffer being full
     */
    public static long getEvictedEventCount() {
        return totalEventsEvicted.get();
    }

    /**
     * Set the maximum number of events to store in the buffer
     * Note: This is a volatile write, so it's immediately visible to all threads.
     * If the current buffer size exceeds the new max, old events will be evicted
     * on the next append operation.
     * 
     * @param maxEvents Maximum number of events to store
     */
    public static void setMaxEvents(int maxEvents) {
        if (maxEvents <= 0) {
            throw new IllegalArgumentException("maxEvents must be positive");
        }
        // Volatile write - no synchronization needed
        InMemoryLogAppender.maxEvents = maxEvents;
        
        // Evict old events if current size exceeds new max
        if (currentSize.get() > maxEvents) {
            evictOldEvents();
        }
    }

    /**
     * Get the maximum number of events that can be stored in the buffer
     */
    public static int getMaxEvents() {
        return maxEvents;
    }

    /**
     * Enable or disable event capture
     */
    public static void setEnabled(boolean enabled) {
        InMemoryLogAppender.enabled = enabled;
    }

    /**
     * Check if event capture is enabled
     */
    public static boolean isEnabled() {
        return enabled;
    }
}

