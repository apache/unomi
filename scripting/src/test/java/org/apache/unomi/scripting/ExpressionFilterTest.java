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
package org.apache.unomi.scripting;

import org.junit.Test;

import java.util.Collections;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ExpressionFilterTest {

    @Test
    public void filter_rejectsEvalEvenWithWhitespaceAndInvisibleChars() {
        ExpressionFilter filter = new ExpressionFilter(null,
                Collections.singleton(Pattern.compile("(?s).*eval\\s*\\(.*")));
        assertNull(filter.filter("org.mvel2.MVEL.eval(\"1+1\")"));
        assertNull(filter.filter("org.mvel2.MVEL.eval ( \"1+1\" )"));
        assertNull(filter.filter("org.mvel2.MVEL.ev\u200Bal(\"1+1\")"));
        assertEquals("1+1", filter.filter("1+1"));
    }

    @Test
    public void filter_doesNotCanonicalizeForAllowList() {
        ExpressionFilter filter = new ExpressionFilter(
                Collections.singleton(Pattern.compile("\\Q1+1\\E")), null);
        assertEquals("1+1", filter.filter("1+1"));
        assertNull(filter.filter("1\u200B+1"));
    }
}
