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
package org.apache.unomi.scripting.internal;

import org.apache.unomi.scripting.ExpressionFilter;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ExpressionFilterFactoryImplTest {

    private static final String ALLOW = "org.apache.unomi.scripting.filter.mvel.allow";
    private static final String FORBID = "org.apache.unomi.scripting.filter.mvel.forbid";

    private ExpressionFilter buildFilterWith(String forbidFileContents, String allowFileContents) throws Exception {
        String prevAllow = System.getProperty(ALLOW);
        String prevForbid = System.getProperty(FORBID);
        File forbidFile = null;
        File allowFile = null;
        try {
            if (forbidFileContents != null) {
                forbidFile = File.createTempFile("mvel-forbid", ".json");
                Files.write(forbidFile.toPath(), forbidFileContents.getBytes(StandardCharsets.UTF_8));
                System.setProperty(FORBID, forbidFile.getAbsolutePath());
            } else {
                System.clearProperty(FORBID);
            }
            if (allowFileContents != null) {
                allowFile = File.createTempFile("mvel-allow", ".json");
                Files.write(allowFile.toPath(), allowFileContents.getBytes(StandardCharsets.UTF_8));
                System.setProperty(ALLOW, allowFile.getAbsolutePath());
            } else {
                System.clearProperty(ALLOW);
            }
            ExpressionFilterFactoryImpl factory = new ExpressionFilterFactoryImpl();
            factory.init(); // bundleContext is null: only the configured files are loaded
            return factory.getExpressionFilter("mvel");
        } finally {
            restore(ALLOW, prevAllow);
            restore(FORBID, prevForbid);
            if (forbidFile != null) { forbidFile.delete(); }
            if (allowFile != null) { allowFile.delete(); }
        }
    }

    private static void restore(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }

    @Test
    public void forbidFileThatFailsToParse_failsClosed() throws Exception {
        // malformed JSON in the forbid file must not silently disable the forbid layer
        ExpressionFilter filter = buildFilterWith("{ this is not valid json", null);
        assertNull("a broken forbid file must reject all expressions", filter.filter("1+1"));
    }

    @Test
    public void forbidFileThatParses_doesNotFailClosed() throws Exception {
        // a valid forbid file forbids only what it lists and leaves everything else runnable
        ExpressionFilter filter = buildFilterWith("[ \".*Runtime.*\" ]", null);
        assertEquals("1+1", filter.filter("1+1"));
        assertNull(filter.filter("something.Runtime.something"));
    }
}
