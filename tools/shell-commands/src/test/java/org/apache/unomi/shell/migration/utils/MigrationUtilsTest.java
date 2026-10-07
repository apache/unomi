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

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
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

    @Test
    public void testGetFileWithoutCommentsMissingResource() {
        when(bundle.getResource("missing.painless")).thenReturn(null);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> MigrationUtils.getFileWithoutComments(bundleContext, "missing.painless"));
        assertEquals("Resource not found: missing.painless", ex.getMessage());
    }

    private static final String MAPPING = "{\"properties\":{\"cpuLoad\":{\"type\":\"double\"}}}";

    private String extractClusterNodeMappingFrom(String path) throws Exception {
        return extractClusterNodeMappingFrom(path, "elasticsearch");
    }

    private String extractClusterNodeMappingFrom(String path, String searchEngine) throws Exception {
        Path mappingFile = Files.createTempFile("clusterNode", ".json");
        try {
            Files.writeString(mappingFile, MAPPING);
            when(bundleContext.getBundles()).thenReturn(new Bundle[]{bundle});
            when(bundle.findEntries(path, "clusterNode.json", true))
                    .thenReturn(Collections.enumeration(Collections.singletonList(mappingFile.toUri().toURL())));
            return MigrationUtils.extractMappingFromBundles(bundleContext, "clusterNode.json", searchEngine);
        } finally {
            Files.deleteIfExists(mappingFile);
        }
    }

    @Test
    public void extractMappingFromBundlesFindsPersistenceMapping() throws Exception {
        assertEquals(MAPPING, extractClusterNodeMappingFrom("META-INF/cxs/mappings"));
    }

    @Test
    public void extractMappingFromBundlesFallsBackToMigrationCopy() throws Exception {
        assertEquals(MAPPING, extractClusterNodeMappingFrom("META-INF/cxs/migration-mappings/elasticsearch"));
        assertEquals(MAPPING, extractClusterNodeMappingFrom("META-INF/cxs/migration-mappings/opensearch", "opensearch"));
    }

    @Test
    public void extractMappingFromBundlesIgnoresTheOtherEngineCopy() {
        when(bundleContext.getBundles()).thenReturn(new Bundle[]{bundle});
        when(bundle.findEntries("META-INF/cxs/migration-mappings/elasticsearch", "event.json", true))
                .thenReturn(Collections.enumeration(Collections.singletonList(getClass().getResource("/"))));

        assertThrows(RuntimeException.class,
                () -> MigrationUtils.extractMappingFromBundles(bundleContext, "event.json", "opensearch"));
    }

    @Test
    public void searchEngineIsDetectedFromTheRootResponse() {
        assertEquals("opensearch", MigrationUtils.searchEngineFromRootResponse(
                "{\"version\":{\"distribution\":\"opensearch\",\"number\":\"3.0.0\"}}"));
        assertEquals("elasticsearch", MigrationUtils.searchEngineFromRootResponse(
                "{\"version\":{\"number\":\"9.1.0\"}}"));
    }

    @Test
    public void extractMappingFromBundlesThrowsWhenMissing() {
        when(bundleContext.getBundles()).thenReturn(new Bundle[]{bundle});

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> MigrationUtils.extractMappingFromBundles(bundleContext, "generic.json"));
        assertEquals("no mapping found in bundles for: generic.json", ex.getMessage());
    }

    @Test
    public void mappingsFromIndexResponseReadsTheIndexMappings() {
        String body = "{\"context-sfdcconfiguration\":{\"mappings\":{\"properties\":{\"itemId\":{\"type\":\"keyword\"}}}}}";
        assertEquals("{\"properties\":{\"itemId\":{\"type\":\"keyword\"}}}", MigrationUtils.mappingsFromIndexResponse(body));
    }

    @Test
    public void mappingsFromIndexResponseRejectsAnythingButOneIndex() {
        assertThrows(IllegalArgumentException.class, () -> MigrationUtils.mappingsFromIndexResponse(""));
        assertThrows(IllegalArgumentException.class, () -> MigrationUtils.mappingsFromIndexResponse("{}"));
        assertThrows(IllegalArgumentException.class,
                () -> MigrationUtils.mappingsFromIndexResponse("{\"a\":{\"mappings\":{}},\"b\":{\"mappings\":{}}}"));
    }

    @Test
    public void resolveItemTypeMatchesKnownTypesIgnoringCaseAndRolloverNumber() {
        Collection<String> types = Arrays.asList("profile", "profileAlias", "event", "clusterNode");
        assertEquals("clusterNode", MigrationUtils.resolveItemType("context-clusternode", "context", types));
        assertEquals("profileAlias", MigrationUtils.resolveItemType("context-profilealias", "context", types));
        assertEquals("event", MigrationUtils.resolveItemType("context-event-000001", "context", types));
    }

    @Test
    public void resolveItemTypeDoesNotMatchOnKnownTypePrefix() {
        Collection<String> types = Arrays.asList("profile", "event");
        assertEquals("profilealias", MigrationUtils.resolveItemType("context-profilealias", "context", types));
        assertEquals("eventfoo", MigrationUtils.resolveItemType("context-eventfoo-000001", "context", types));
        assertEquals("sfdcconfiguration", MigrationUtils.resolveItemType("context-sfdcconfiguration", "context", types));
        assertEquals("generic", MigrationUtils.resolveItemType("other-profile", "context", types));
    }

    private static final String SHIPPED_DEFINITIONS = "requestBody/4.0.0/shipped_system_definitions.json";

    @Test
    public void loadShippedDefinitionIdsGroupsLowerCaseIdsByType() throws Exception {
        stubShippedDefinitions("[\"conditionType:booleanCondition\", \"rule:_abc_myRule.json\", \"rule:other\"]");
        Map<String, Set<String>> shipped = MigrationUtils.loadShippedDefinitionIds(bundleContext);
        assertEquals(Set.of("conditiontype", "rule"), shipped.keySet());
        assertEquals(Set.of("booleancondition"), shipped.get("conditiontype"));
        assertEquals(Set.of("_abc_myrule.json", "other"), shipped.get("rule"));
    }

    @Test
    public void loadShippedDefinitionIdsRejectsEmptyOrMalformedLists() throws Exception {
        for (String content : Arrays.asList("[]", "[\"booleanCondition\"]", "[\":booleanCondition\"]", "[\"rule:\"]", "[42]")) {
            stubShippedDefinitions(content);
            assertThrows(content, IllegalStateException.class, () -> MigrationUtils.loadShippedDefinitionIds(bundleContext));
        }
    }

    /**
     * The shipped list is a snapshot: it must be updated whenever a definition is added to or
     * removed from the bundles, or migrated copies land in the wrong tenant.
     */
    @Test
    public void shippedDefinitionsListMatchesTheDefinitionsInTheSourceTree() throws Exception {
        Path repositoryRoot = Path.of("").toAbsolutePath().getParent().getParent();
        assertTrue("Unexpected source layout: " + repositoryRoot, Files.isDirectory(repositoryRoot.resolve("plugins")));

        Map<String, String> typeByDirectory = Map.of("conditions", "conditiontype", "actions", "actiontype",
                "properties", "propertytype", "rules", "rule", "personas", "persona");
        Map<String, Set<String>> expected = new TreeMap<>();
        Files.walkFileTree(repositoryRoot, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                String name = dir.getFileName().toString();
                boolean skip = name.startsWith(".") || name.equals("target") || name.equals("node_modules") || name.equals("test");
                return skip ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String path = repositoryRoot.relativize(file).toString().replace(File.separatorChar, '/');
                int cxs = path.indexOf("/src/main/resources/META-INF/cxs/");
                if (cxs < 0 || !path.endsWith(".json")) {
                    return FileVisitResult.CONTINUE;
                }
                String directory = path.substring(cxs + "/src/main/resources/META-INF/cxs/".length()).split("/")[0];
                String type = typeByDirectory.get(directory);
                if (type == null) {
                    return FileVisitResult.CONTINUE;
                }
                JSONObject definition = new JSONObject(Files.readString(file));
                if ("persona".equals(type)) {
                    addExpected(expected, type, definition.getJSONObject("persona").getString("itemId"));
                    JSONArray sessions = definition.getJSONArray("sessions");
                    for (int i = 0; i < sessions.length(); i++) {
                        addExpected(expected, "personasession", sessions.getJSONObject(i).getString("itemId"));
                    }
                } else {
                    addExpected(expected, type, definition.getJSONObject("metadata").getString("id"));
                }
                return FileVisitResult.CONTINUE;
            }
        });

        when(bundle.getResource(SHIPPED_DEFINITIONS)).thenReturn(getClass().getResource("/" + SHIPPED_DEFINITIONS));
        assertEquals("Update " + SHIPPED_DEFINITIONS + " to match the definitions shipped under META-INF/cxs",
                expected, MigrationUtils.loadShippedDefinitionIds(bundleContext));
    }

    private static void addExpected(Map<String, Set<String>> expected, String type, String id) {
        expected.computeIfAbsent(type, key -> new TreeSet<>()).add(id.toLowerCase(Locale.ROOT));
    }

    private void stubShippedDefinitions(String content) throws IOException {
        Path file = Files.createTempFile("shipped-definitions", ".json");
        file.toFile().deleteOnExit();
        Files.writeString(file, content);
        when(bundle.getResource(SHIPPED_DEFINITIONS)).thenReturn(file.toUri().toURL());
    }

    @Test
    public void buildItemTypeUpdateRequestMatchesKeywordAndFoldedItemType() {
        JSONObject request = new JSONObject(MigrationUtils.buildItemTypeUpdateRequest("ctx.op = 'noop'", "conditionType"));
        assertEquals("ctx.op = 'noop'", request.getJSONObject("script").getString("source"));
        assertEquals("painless", request.getJSONObject("script").getString("lang"));
        JSONObject bool = request.getJSONObject("query").getJSONObject("bool");
        assertEquals(1, bool.getInt("minimum_should_match"));
        JSONArray should = bool.getJSONArray("should");
        assertEquals(2, should.length());
        assertEquals("conditionType", should.getJSONObject(0).getJSONObject("term").getString("itemType.keyword"));
        assertEquals("conditiontype", should.getJSONObject(1).getJSONObject("term").getString("itemType"));
    }
}
