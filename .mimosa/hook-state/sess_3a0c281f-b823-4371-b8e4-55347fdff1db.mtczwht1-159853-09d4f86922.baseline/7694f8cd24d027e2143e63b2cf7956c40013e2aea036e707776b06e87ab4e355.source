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
package org.apache.unomi.services.impl.scheduler;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.CyclicBufferAppender;
import org.apache.unomi.api.tasks.ScheduledTask;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Makes an intermittent scheduler test failure diagnosable from the CI log it failed in.
 * <p>
 * Scheduler failures are almost always about state and ordering: which node held a lock, when it
 * was renewed, who decided it had expired, what the store actually contained. The scheduler already
 * logs all of that as {@code LOCK-DIAG} lines, but only at DEBUG, and CI does not run at DEBUG --
 * so every intermittent failure historically arrived as a bare assertion message with the evidence
 * discarded. Re-running with {@code -DTEST_LOG_LEVEL=DEBUG} rarely helps, because an intermittent
 * failure usually does not recur on demand.
 * <p>
 * This extension therefore captures DEBUG for the scheduler packages into a bounded in-memory ring
 * buffer that costs nothing on a passing test, and dumps it -- together with a snapshot of every
 * task document in the store -- at the moment a test fails. The snapshot is taken from
 * {@link TestExecutionExceptionHandler}, which runs before the test's own {@code @AfterEach}
 * teardown, so the store is still alive and holds the state that caused the failure rather than
 * whatever cleanup left behind.
 * <p>
 * When {@code -DTEST_LOG_LEVEL} is set explicitly, the extension stays out of the way and leaves
 * logback's configured behaviour alone: an explicit request for console output should get console
 * output.
 */
public class SchedulerDiagnosticsExtension
        implements BeforeEachCallback, AfterEachCallback, TestExecutionExceptionHandler {

    /** Enough lines to cover several checker ticks across a handful of nodes. */
    private static final int BUFFER_SIZE = 4000;

    /** Packages whose DEBUG output explains scheduler behaviour. */
    private static final String[] CAPTURED_LOGGERS = {
        "org.apache.unomi.services.impl.scheduler",
        "org.apache.unomi.services.impl.cluster"
    };

    private static final String APPENDER_NAME = "scheduler-diagnostics-ring-buffer";
    private static final ExtensionContext.Namespace NAMESPACE =
        ExtensionContext.Namespace.create(SchedulerDiagnosticsExtension.class);

    private static boolean explicitLogLevelRequested() {
        String requested = System.getProperty("TEST_LOG_LEVEL");
        return requested != null && !requested.trim().isEmpty();
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        if (explicitLogLevelRequested()) {
            return;
        }
        if (!(LoggerFactory.getILoggerFactory() instanceof LoggerContext)) {
            return; // not logback (shaded/OSGi runs); nothing to attach to
        }
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();

        CyclicBufferAppender<ILoggingEvent> buffer = new CyclicBufferAppender<>();
        buffer.setContext(loggerContext);
        buffer.setName(APPENDER_NAME);
        buffer.setMaxSize(BUFFER_SIZE);
        buffer.start();

        for (String name : CAPTURED_LOGGERS) {
            ch.qos.logback.classic.Logger logger = loggerContext.getLogger(name);
            // additive=false keeps the captured DEBUG out of the console on passing runs; the
            // buffer dump below is the only consumer, and it only fires on failure.
            logger.setLevel(Level.DEBUG);
            logger.setAdditive(false);
            logger.addAppender(buffer);
        }
        context.getStore(NAMESPACE).put(APPENDER_NAME, buffer);
    }

    @Override
    public void afterEach(ExtensionContext context) {
        @SuppressWarnings("unchecked")
        CyclicBufferAppender<ILoggingEvent> buffer =
            context.getStore(NAMESPACE).remove(APPENDER_NAME, CyclicBufferAppender.class);
        if (buffer == null || !(LoggerFactory.getILoggerFactory() instanceof LoggerContext)) {
            return;
        }
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        for (String name : CAPTURED_LOGGERS) {
            ch.qos.logback.classic.Logger logger = loggerContext.getLogger(name);
            logger.detachAppender(buffer);
            logger.setAdditive(true);
            logger.setLevel(null); // inherit from root again
        }
        buffer.stop();
    }

    @Override
    public void handleTestExecutionException(ExtensionContext context, Throwable throwable)
            throws Throwable {
        StringBuilder report = new StringBuilder();
        report.append("\n================ SCHEDULER DIAGNOSTICS for ")
            .append(context.getRequiredTestClass().getSimpleName()).append('.')
            .append(context.getRequiredTestMethod().getName())
            .append(" ================\n")
            .append("Failure: ").append(throwable).append('\n');

        appendTaskSnapshot(report, context);
        appendBufferedLog(report, context);

        report.append("================ END SCHEDULER DIAGNOSTICS ================\n");
        // stdout, not a logger: this must survive whatever logging configuration is in force, and
        // Surefire captures stdout into the report the CI log shows.
        System.out.println(report);

        throw throwable;
    }

    /**
     * Dumps every task document the test's persistence service can see. Taken before teardown, so
     * this is the state that produced the failure.
     */
    private void appendTaskSnapshot(StringBuilder report, ExtensionContext context) {
        report.append("\n-- task documents in the store at failure time --\n");
        PersistenceService persistenceService = findPersistenceService(context);
        if (persistenceService == null) {
            report.append("  (no PersistenceService field found on the test instance)\n");
            return;
        }
        try {
            // getAllItems is search-based, and both the in-memory harness and a real cluster hold a
            // refresh interval behind the store. Force visibility first: the test has already
            // failed, so there is no state left worth preserving, and a snapshot that silently
            // reports "(none)" because of refresh lag is worse than useless.
            try {
                persistenceService.refreshIndex(ScheduledTask.class);
                persistenceService.refresh();
            } catch (Exception ignored) {
                report.append("  (refresh before snapshot failed; list may lag the store)\n");
            }
            List<ScheduledTask> tasks =
                persistenceService.getAllItems(ScheduledTask.class, 0, -1, null).getList();
            if (tasks.isEmpty()) {
                report.append("  (none)\n");
                return;
            }
            SimpleDateFormat fmt = new SimpleDateFormat("HH:mm:ss.SSS");
            for (ScheduledTask task : tasks) {
                report.append("  ").append(task.getItemId())
                    .append(" type=").append(task.getTaskType())
                    .append(" status=").append(task.getStatus())
                    .append(" enabled=").append(task.isEnabled())
                    .append(" execNode=").append(task.getExecutingNodeId())
                    .append(" lockOwner=").append(task.getLockOwner())
                    .append(" lockDate=")
                    .append(task.getLockDate() == null ? "null" : fmt.format(task.getLockDate()))
                    .append(" lease=").append(task.getLockLeaseMillis()).append("ms")
                    .append(" success=").append(task.getSuccessCount())
                    .append(" failure=").append(task.getFailureCount())
                    .append(" nextExec=")
                    .append(task.getNextScheduledExecution() == null
                        ? "null" : fmt.format(task.getNextScheduledExecution()))
                    .append(" history=").append(historySize(task))
                    .append(" lastError=").append(task.getLastError())
                    .append('\n');
            }
        } catch (Exception e) {
            report.append("  (failed to read tasks: ").append(e).append(")\n");
        }
    }

    private static int historySize(ScheduledTask task) {
        Map<String, Object> details = task.getStatusDetails();
        if (details == null) {
            return 0;
        }
        Object history = details.get("executionHistory");
        return history instanceof Collection ? ((Collection<?>) history).size() : 0;
    }

    /**
     * Finds a {@link PersistenceService} on the test instance. Reflection rather than an interface
     * the tests must implement: the point is that adding this extension to a test class costs one
     * annotation and no other change.
     */
    private PersistenceService findPersistenceService(ExtensionContext context) {
        Object testInstance = context.getTestInstance().orElse(null);
        if (testInstance == null) {
            return null;
        }
        for (Class<?> type = testInstance.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (!PersistenceService.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    PersistenceService value = (PersistenceService) field.get(testInstance);
                    if (value != null) {
                        return value;
                    }
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // Not readable; keep looking.
                }
            }
        }
        return null;
    }

    private void appendBufferedLog(StringBuilder report, ExtensionContext context) {
        @SuppressWarnings("unchecked")
        CyclicBufferAppender<ILoggingEvent> buffer =
            context.getStore(NAMESPACE).get(APPENDER_NAME, CyclicBufferAppender.class);
        if (buffer == null) {
            report.append("\n-- captured scheduler DEBUG log --\n")
                .append("  (not captured; -DTEST_LOG_LEVEL was set, so the log went to the console)\n");
            return;
        }
        int count = buffer.getLength();
        report.append("\n-- captured scheduler DEBUG log (last ").append(count)
            .append(" events, newest last) --\n");
        if (count == 0) {
            report.append("  (empty)\n");
            return;
        }
        SimpleDateFormat fmt = new SimpleDateFormat("HH:mm:ss.SSS");
        for (int i = 0; i < count; i++) {
            ILoggingEvent event = buffer.get(i);
            if (event == null) {
                continue;
            }
            report.append("  ").append(fmt.format(new Date(event.getTimeStamp())))
                .append(" [").append(event.getThreadName()).append("] ")
                .append(event.getLevel()).append(' ')
                .append(shortLoggerName(event.getLoggerName())).append(" - ")
                .append(event.getFormattedMessage()).append('\n');
        }
    }

    private static String shortLoggerName(String loggerName) {
        int lastDot = loggerName.lastIndexOf('.');
        return lastDot < 0 ? loggerName : loggerName.substring(lastDot + 1);
    }
}
