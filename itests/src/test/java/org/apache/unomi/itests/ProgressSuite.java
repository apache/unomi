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
package org.apache.unomi.itests;

import org.junit.Test;
import org.junit.runner.Description;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.Suite;
import org.junit.runners.model.InitializationError;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A custom JUnit test suite runner that provides enhanced progress reporting
 * during test execution by integrating with the {@link ProgressListener}.
 * 
 * <p>This suite extends JUnit's standard {@link Suite} runner to automatically
 * count test methods across the entire class hierarchy and provide real-time
 * progress feedback. It features:</p>
 * <ul>
 *   <li>Automatic test method counting across class hierarchies</li>
 *   <li>Integration with {@link ProgressListener} for enhanced progress reporting</li>
 *   <li>Thread-safe progress tracking using atomic counters</li>
 *   <li>Support for nested test classes and inheritance</li>
 * </ul>
 * 
 * <p>The suite automatically counts all methods annotated with {@code @Test}
 * in the specified test classes and their superclasses, providing an accurate
 * total count for progress reporting.</p>
 * 
 * <p>Example usage:</p>
 * <pre>{@code
 * @RunWith(ProgressSuite.class)
 * @Suite.SuiteClasses({
 *     TestClass1.class,
 *     TestClass2.class,
 *     TestClass3.class
 * })
 * public class AllTestsSuite {
 *     // This class serves as a container for the test suite
 * }
 * }</pre>
 * 
 * <p>The suite will automatically:</p>
 * <ul>
 *   <li>Count all test methods in the specified classes and their hierarchies</li>
 *   <li>Create a {@link ProgressListener} with the accurate test count</li>
 *   <li>Display real-time progress with visual elements and timing information</li>
 *   <li>Provide detailed performance statistics at completion</li>
 * </ul>
 * 
 * @author Apache Unomi
 * @since 3.0.0
 * @see org.junit.runners.Suite
 * @see org.apache.unomi.itests.ProgressListener
 * @see org.junit.runner.RunWith
 * @see org.junit.runners.Suite.SuiteClasses
 */
public class ProgressSuite extends Suite {

    /** Total number of test methods across all classes in the suite */
    private final int totalTests;
    /** Thread-safe counter for completed tests, shared with ProgressListener */
    private final AtomicInteger completedTests = new AtomicInteger(0);

    /**
     * Creates a new ProgressSuite instance for the specified test suite class.
     * 
     * <p>The constructor initializes the suite by:</p>
     * <ul>
     *   <li>Extracting test classes from the {@code @Suite.SuiteClasses} annotation</li>
     *   <li>Counting all test methods across the class hierarchies</li>
     *   <li>Initializing the progress tracking infrastructure</li>
     * </ul>
     * 
     * @param klass the test suite class that must be annotated with {@code @Suite.SuiteClasses}
     * @throws InitializationError if the class is not properly annotated or if there are
     *                             issues with the test class configuration
     */
    public ProgressSuite(Class<?> klass) throws InitializationError {
        super(klass, getAnnotatedClasses(klass));
        this.totalTests = countTestMethods(getAnnotatedClasses(klass));
    }

    /**
     * Extracts the test classes from the {@code @Suite.SuiteClasses} annotation.
     * 
     * @param klass the test suite class to examine
     * @return an array of test classes specified in the annotation
     * @throws InitializationError if the class is not annotated with {@code @Suite.SuiteClasses}
     */
    private static Class<?>[] getAnnotatedClasses(Class<?> klass) throws InitializationError {
        Suite.SuiteClasses annotation = klass.getAnnotation(Suite.SuiteClasses.class);
        if (annotation == null) {
            throw new InitializationError(
                    String.format("Class '%s' must have a @Suite.SuiteClasses annotation", klass.getName()));
        }
        return annotation.value();
    }

    /**
     * Counts the total number of test methods across all specified test classes.
     * 
     * @param testClasses array of test classes to count methods in
     * @return the total number of methods annotated with {@code @Test}
     */
    private static int countTestMethods(Class<?>[] testClasses) {
        int count = 0;
        for (Class<?> testClass : testClasses) {
            count += countTestMethodsInClassHierarchy(testClass);
        }
        return count;
    }

    /**
     * Recursively counts test methods in a class and its entire inheritance hierarchy.
     * 
     * <p>This method traverses the class hierarchy upward from the given class,
     * counting all methods annotated with {@code @Test} in each class. It stops
     * at {@code Object.class} to avoid counting system methods.</p>
     * 
     * @param clazz the class to count test methods in (including superclasses)
     * @return the number of test methods found in this class and its hierarchy
     */
    private static int countTestMethodsInClassHierarchy(Class<?> clazz) {
        int count = 0;
        if (clazz == null || clazz == Object.class) {
            return 0; // Stop at the base class
        }
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Test.class)) {
                count++;
            }
        }
        // Recurse into the superclass
        count += countTestMethodsInClassHierarchy(clazz.getSuperclass());
        return count;
    }

    /**
     * Executes the test suite with enhanced progress reporting.
     * 
     * <p>This method overrides the standard suite execution to integrate
     * the {@link ProgressListener} for real-time progress feedback. It:</p>
     * <ul>
     *   <li>Creates a {@link ProgressListener} with the accurate test count</li>
     *   <li>Manually triggers the test run started event (since the listener
     *       is registered after this event would normally be fired)</li>
     *   <li>Registers the listener with the run notifier</li>
     *   <li>Delegates to the parent suite execution</li>
     * </ul>
     * 
     * <p>Note: Two separate {@link ProgressListener} instances are created:
     * one for manual event triggering and another for the notifier. This is
     * necessary because the test run started event is fired before listeners
     * can be registered.</p>
     * 
     * @param notifier the run notifier to use for test execution notifications
     */
    @Override
    public void run(RunNotifier notifier) {
        ProgressListener listener = new ProgressListener(totalTests, completedTests);
        Description suiteDescription = getDescription();
        // We call this manually as we register the listener after this event has already been triggered.
        listener.testRunStarted(suiteDescription);

        notifier.addListener(new ProgressListener(totalTests, completedTests));
        super.run(notifier);
    }

}
