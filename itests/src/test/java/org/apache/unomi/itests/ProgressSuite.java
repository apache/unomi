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

public class ProgressSuite extends Suite {

    private final int totalTests;
    private final AtomicInteger completedTests = new AtomicInteger(0);

    public ProgressSuite(Class<?> klass) throws InitializationError {
        super(klass, getAnnotatedClasses(klass));
        this.totalTests = countTestMethods(getAnnotatedClasses(klass));
    }

    private static Class<?>[] getAnnotatedClasses(Class<?> klass) throws InitializationError {
        Suite.SuiteClasses annotation = klass.getAnnotation(Suite.SuiteClasses.class);
        if (annotation == null) {
            throw new InitializationError(
                    String.format("Class '%s' must have a @Suite.SuiteClasses annotation", klass.getName()));
        }
        return annotation.value();
    }

    private static int countTestMethods(Class<?>[] testClasses) {
        int count = 0;
        for (Class<?> testClass : testClasses) {
            count += countTestMethodsInClassHierarchy(testClass);
        }
        return count;
    }

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
