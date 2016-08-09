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
package org.apache.unomi.persistence.elasticsearch;

import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * A unit test class for the FieldDotEscaper utility methods
 */
public class FieldDotEscapeTest {

    @Test
    public void testTrickyJson() {
        String result = FieldDotEscaper.escapeJson("{\"tricking_the_parser\" : \"this.should.not\\\": match.either\"}");
        Assert.assertTrue("Found escaped pattern instead of untouched one", result.contains("this.should.not\\\": match.either"));
    }

    @Test
    public void testJustDot() {
        String result = FieldDotEscaper.escapeJson("{\".\" : \"this.should.not\\\": match.either\"}");
        Assert.assertTrue("Didn't find expected escaped pattern", result.contains("\"__DOT__\""));
        Assert.assertTrue("Found escaped pattern instead of untouched one", result.contains("this.should.not\\\": match.either"));
    }

    @Test
    public void testComplexJson() throws IOException {
        InputStream complexJsonInputStream = this.getClass().getClassLoader().getResourceAsStream("complex.json");
        String input = IOUtils.toString(complexJsonInputStream);
        System.out.println("Original JSON:");
        System.out.println("=================");
        System.out.println(input);
        Set<String> modifiedNames = new LinkedHashSet<>();
        String result = FieldDotEscaper.escapeJson(input, modifiedNames);
        System.out.println("Modified names:");
        System.out.println("=================");
        for (String modifiedName : modifiedNames) {
            System.out.println(modifiedName);
        }
        System.out.println("Transformed JSON:");
        System.out.println("=================");
        System.out.println(result);
        Assert.assertTrue("Didn't find expected escaped pattern", result.contains("src_terms[0]__DOT__fields__DOT__siteContent"));
        Assert.assertTrue("Didn't find expected escaped pattern", result.contains("newline__DOT__test"));
        Assert.assertTrue("Found escaped pattern instead of untouched one", result.contains("this.should.never:match"));
        Assert.assertTrue("Found escaped pattern instead of untouched one", result.contains("this.should.not\\\": match.either"));
        result = FieldDotEscaper.unescapeJson(result);
        Assert.assertEquals("Round trip of escaping then unescaping should be identical", input, result);
    }

    @Test
    public void testString() {
        String input = "this.is.a..test";
        String result = FieldDotEscaper.unescapeString(FieldDotEscaper.escapeString(input));
        Assert.assertEquals("Strings should be exactly the same", input, result);
    }

    @Test
    public void testMap() {
        Map<String,Object> input = new HashMap<>();
        input.put("this.is.a..test", "1");
        input.put("another.test", "2");
        Map<? extends String,?> result = FieldDotEscaper.unescapeMap(FieldDotEscaper.escapeMap(input));
        Assert.assertEquals("Maps should be identical", input, result);
    }

    @Test
    public void testProperties() {
        Properties input = new Properties();
        input.put("this.is.a..test", "1");
        input.put("another.test", "2");
        Properties result = FieldDotEscaper.unescapeProperties(FieldDotEscaper.escapeProperties(input));
        Assert.assertEquals("Properties should be identical", input, result);
    }

}
