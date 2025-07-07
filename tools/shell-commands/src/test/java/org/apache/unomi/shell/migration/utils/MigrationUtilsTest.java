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
package org.apache.unomi.shell.migration.utils;

import org.junit.Before;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class MigrationUtilsTest {

    private BundleContext bundleContext;
    private Bundle bundle;
    private URL resourceUrl;

    @Before
    public void setUp() {
        bundleContext = mock(BundleContext.class);
        bundle = mock(Bundle.class);
        resourceUrl = mock(URL.class);

        when(bundleContext.getBundle()).thenReturn(bundle);
        when(bundle.getResource(anyString())).thenReturn(resourceUrl);
    }

    @Test
    public void testSimpleBlockComment() throws Exception {
        String input = "code1\n/* block comment */\ncode2";
        String expected = "code1 code2";
        testCommentHandling(input, expected);
    }

    @Test
    public void testSimpleInlineComment() throws Exception {
        String input = "code1\n// inline comment\ncode2";
        String expected = "code1 code2";
        testCommentHandling(input, expected);
    }

    @Test
    public void testInlineCommentAfterCode() throws Exception {
        String input = "code1 // inline comment\ncode2";
        String expected = "code1 code2";
        testCommentHandling(input, expected);
    }

    @Test
    public void testBlockCommentAfterCode() throws Exception {
        String input = "code1 /* block comment */ code2";
        String expected = "code1 code2";
        testCommentHandling(input, expected);
    }

    @Test
    public void testBlockCommentSpanningLines() throws Exception {
        String input = "code1\n/* block\ncomment\nspanning\nlines */\ncode2";
        String expected = "code1 code2";
        testCommentHandling(input, expected);
    }

    @Test
    public void testCommentInsideString() throws Exception {
        String input = "String s = \"/* not a comment */\";\nString t = \"// not a comment\";";
        String expected = "String s = \"/* not a comment */\"; String t = \"// not a comment\";";
        testCommentHandling(input, expected);
    }

    @Test
    public void testMixedComments() throws Exception {
        String input = "code1\n/* block comment */\ncode2 // inline comment\ncode3";
        String expected = "code1 code2 code3";
        testCommentHandling(input, expected);
    }

    @Test
    public void testMultipleBlockComments() throws Exception {
        String input = "code1\n/* first block */\ncode2\n/* second block */\ncode3";
        String expected = "code1 code2 code3";
        testCommentHandling(input, expected);
    }

    @Test
    public void testMultipleInlineComments() throws Exception {
        String input = "code1\n// first inline\ncode2\n// second inline\ncode3";
        String expected = "code1 code2 code3";
        testCommentHandling(input, expected);
    }

    @Test
    public void testEmptyLines() throws Exception {
        String input = "code1\n\n/* block */\n\n// inline\n\ncode2";
        String expected = "code1 code2";
        testCommentHandling(input, expected);
    }

    @Test
    public void testCommentAtStartOfLine() throws Exception {
        String input = "/* block */ code1\n// inline code2";
        String expected = "code1";
        testCommentHandling(input, expected);
    }

    @Test
    public void testCommentAtEndOfLine() throws Exception {
        String input = "code1 /* block */\ncode2 // inline";
        String expected = "code1 code2";
        testCommentHandling(input, expected);
    }

    @Test
    public void testCommentWithWhitespace() throws Exception {
        String input = "code1\n/*  block  comment  */\ncode2\n//  inline  comment\ncode3";
        String expected = "code1 code2 code3";
        testCommentHandling(input, expected);
    }

    private void testCommentHandling(String input, String expected) throws Exception {
        InputStream inputStream = new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8));
        when(resourceUrl.openStream()).thenReturn(inputStream);

        String result = MigrationUtils.getFileWithoutComments(bundleContext, "test.painless");
        assertEquals(expected, result);
    }

    @Test
    public void testMultipleCommentsOnSameLine() throws Exception {
        String input = "code /* first */ code /* second */ code // inline";
        String expected = "code code code";
        testCommentHandling(input, expected);
    }

    @Test
    public void testEmptyComments() throws Exception {
        String input = "code /**/ code // \ncode /* */ code";
        String expected = "code code code code";
        testCommentHandling(input, expected);
    }

    @Test
    public void testLineEndings() throws Exception {
        String input = "code1 // comment\r\ncode2 /* comment */\r\ncode3";
        String expected = "code1 code2 code3";
        testCommentHandling(input, expected);
    }

    @Test
    public void testSingleQuotesInBlockComment() throws Exception {
        String input = "code1\n/* This is a 'quoted' block comment */\ncode2";
        String expected = "code1 code2";
        testCommentHandling(input, expected);
    }

    @Test
    public void testDoubleQuotesInBlockComment() throws Exception {
        String input = "code1\n/* This is a \"quoted\" block comment */\ncode2";
        String expected = "code1 code2";
        testCommentHandling(input, expected);
    }

    @Test
    public void testSingleQuotesInInlineComment() throws Exception {
        String input = "code1\n// This is a 'quoted' inline comment\ncode2";
        String expected = "code1 code2";
        testCommentHandling(input, expected);
    }

    @Test
    public void testDoubleQuotesInInlineComment() throws Exception {
        String input = "code1\n// This is a \"quoted\" inline comment\ncode2";
        String expected = "code1 code2";
        testCommentHandling(input, expected);
    }

    @Test
    public void testMixedQuotesInComments() throws Exception {
        String input = "code1\n/* Block with 'single' and \"double\" quotes */\ncode2\n// Inline with 'single' and \"double\" quotes\ncode3";
        String expected = "code1 code2 code3";
        testCommentHandling(input, expected);
    }
} 