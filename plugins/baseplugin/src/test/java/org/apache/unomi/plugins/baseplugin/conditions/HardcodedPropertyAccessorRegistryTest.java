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
package org.apache.unomi.plugins.baseplugin.conditions;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class HardcodedPropertyAccessorRegistryTest {

    HardcodedPropertyAccessorRegistry registry = new HardcodedPropertyAccessorRegistry();

    @Test
    public void testGetNextTokens() {
        assertTokens("test", "test", null);
        assertTokens("test.test", "test", ".test");
        assertTokens("test..", "test", "..");
        assertTokens("test...", "test", "...");
        assertTokens(".test", "test", null);
        assertTokens(".test[abc]", "test[abc]", null);
        assertTokens("[abc]", "[abc]", null);
        assertTokens("[\"abc\"]", "abc", null);
        assertTokens(".test[\"abc\"]", "test", "[\"abc\"]");
        assertTokens("..test", "", ".test");
        assertTokens(".[test", "[test", null);
        assertTokens("[\"test\"][\"a\"]", "test", "[\"a\"]");
        assertTokens("test[\"a\"].c", "test", "[\"a\"].c");
        assertTokens("[\"b\"]", "b", null);
        assertTokens("[\"b\"].c", "b", ".c");
        assertTokens("[\"b.c\"].c", "b.c", ".c");
        assertTokens("[\"b\"test\"].c", "b\"test", ".c");
        assertTokens("[\"b\"]test\"].c", "b", "test\"].c");
        assertTokens("[\"b\\.\\\"]c\"].c", "b\\.\\", "c\"].c");
        assertTokens("[]", "[]", null);
    }

    private void assertTokens(String expression, String expectedPropertyName, String expectedLeftoverExpression) {
        HardcodedPropertyAccessorRegistry.NextTokens nextTokens = registry.getNextTokens(expression);
        assertEquals("Property name value was wrong", expectedPropertyName, nextTokens.propertyName);
        assertEquals("Leftover expression value was wrong", expectedLeftoverExpression, nextTokens.leftoverExpression);
    }
}
