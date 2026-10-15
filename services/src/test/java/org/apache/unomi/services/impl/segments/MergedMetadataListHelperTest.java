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
package org.apache.unomi.services.impl.segments;

import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.segments.Segment;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class MergedMetadataListHelperTest {

    private static final int DEFAULT_QUERY_LIMIT = 10;

    @Test
    public void testSortByNameAscendingAndDescending() {
        List<Segment> items = threeSegments();

        MergedMetadataListHelper.sort(items, "name:asc");
        assertEquals(Arrays.asList("a", "b", "c"), ids(items));

        MergedMetadataListHelper.sort(items, "metadata.name:desc");
        assertEquals(Arrays.asList("c", "b", "a"), ids(items));

        MergedMetadataListHelper.sort(items, "metadata.name");
        assertEquals(Arrays.asList("a", "b", "c"), ids(items));
    }

    @Test
    public void testSortByCreationDateAscendingAndDescending() {
        List<Segment> items = threeSegments();

        MergedMetadataListHelper.sort(items, "creationDate:asc");
        assertEquals(Arrays.asList("c", "b", "a"), ids(items));

        MergedMetadataListHelper.sort(items, "creationDate:desc");
        assertEquals(Arrays.asList("a", "b", "c"), ids(items));
    }

    @Test
    public void testMultipleCriteriaAndBooleanField() {
        Segment disabledB = segment("b", "Same", daysAgo(1));
        disabledB.getMetadata().setEnabled(false);
        Segment enabledC = segment("c", "Same", daysAgo(2));
        Segment enabledA = segment("a", "Other", daysAgo(3));
        List<Segment> items = new ArrayList<>(Arrays.asList(disabledB, enabledC, enabledA));

        MergedMetadataListHelper.sort(items, "metadata.enabled:desc,metadata.name:asc");
        assertEquals(Arrays.asList("a", "c", "b"), ids(items));
    }

    @Test
    public void testFieldsSortableInPreviousVersionsAreSupported() {
        Segment readOnly = segment("a", "Alpha", daysAgo(1));
        readOnly.getMetadata().setReadOnly(true);
        List<Segment> items = new ArrayList<>(Arrays.asList(readOnly, segment("b", "Bravo", daysAgo(2))));

        MergedMetadataListHelper.sort(items, "metadata.readOnly:asc");
        assertEquals(Arrays.asList("b", "a"), ids(items));

        for (String field : Arrays.asList("metadata.hidden", "metadata.missingPlugins", "metadata.tags",
                "metadata.systemTags", "metadata.description", "metadata.scope", "metadata.id", "itemId",
                "tenantId", "createdBy", "lastModifiedBy", "lastModificationDate", "lastSyncDate")) {
            assertDoesNotThrow(() -> MergedMetadataListHelper.sort(items, field + ":desc"), field);
        }
    }

    @Test
    public void testMultiValuedFieldSortsOnSmallestAscendingAndLargestDescending() {
        Segment a = segment("a", "Alpha", daysAgo(1));
        a.getMetadata().setTags(new LinkedHashSet<>(Arrays.asList("m", "z")));
        Segment b = segment("b", "Bravo", daysAgo(2));
        b.getMetadata().setTags(new LinkedHashSet<>(Arrays.asList("b", "n")));
        Segment untagged = segment("c", "Charlie", daysAgo(3));
        List<Segment> items = new ArrayList<>(Arrays.asList(untagged, a, b));

        MergedMetadataListHelper.sort(items, "metadata.tags:asc");
        assertEquals(Arrays.asList("b", "a", "c"), ids(items));

        MergedMetadataListHelper.sort(items, "metadata.tags:desc");
        assertEquals(Arrays.asList("a", "b", "c"), ids(items));
    }

    @Test
    public void testMissingValuesSortLastInBothDirections() {
        Segment noDate = segment("x", "Xray", null);
        List<Segment> items = new ArrayList<>(Arrays.asList(noDate, segment("a", "Alpha", daysAgo(1)), segment("b", "Bravo", daysAgo(2))));

        MergedMetadataListHelper.sort(items, "creationDate:asc");
        assertEquals(Arrays.asList("b", "a", "x"), ids(items));

        MergedMetadataListHelper.sort(items, "creationDate:desc");
        assertEquals(Arrays.asList("a", "b", "x"), ids(items));
    }

    @Test
    public void testTiesAreBrokenByItemId() {
        List<Segment> items = new ArrayList<>(Arrays.asList(
                segment("c", "Same", daysAgo(1)),
                segment("a", "Same", daysAgo(2)),
                segment("b", "Same", daysAgo(3))
        ));

        MergedMetadataListHelper.sort(items, "name:desc");
        assertEquals(Arrays.asList("a", "b", "c"), ids(items));
    }

    @Test
    public void testUnknownOrMalformedCriteriaAreIgnored() {
        List<Segment> items = threeSegments();
        List<String> original = ids(items);

        MergedMetadataListHelper.sort(items, "unknownField:asc");
        assertEquals(original, ids(items));

        MergedMetadataListHelper.sort(items, ",unknownField:desc,");
        assertEquals(original, ids(items));

        MergedMetadataListHelper.sort(items, "unknownField:desc,name:asc");
        assertEquals(Arrays.asList("a", "b", "c"), ids(items));
    }

    @Test
    public void testOnlyExactDescSuffixSortsDescending() {
        List<Segment> items = threeSegments();

        MergedMetadataListHelper.sort(items, "name:descending");
        assertEquals(Arrays.asList("a", "b", "c"), ids(items));

        MergedMetadataListHelper.sort(items, "name:DESC");
        assertEquals(Arrays.asList("a", "b", "c"), ids(items));
    }

    @Test
    public void testNoSortKeepsMergedOrder() {
        List<Segment> items = threeSegments();
        List<String> original = ids(items);

        MergedMetadataListHelper.sort(items, null);
        assertEquals(original, ids(items));

        PartialList<Metadata> page = MergedMetadataListHelper.sortAndPage(items, 0, -1, "  ", DEFAULT_QUERY_LIMIT);
        assertEquals(original, metadataIds(page));
    }

    @Test
    public void testLimitSemanticsMatchPersistence() {
        List<Segment> items = new ArrayList<>();
        for (int i = 0; i < DEFAULT_QUERY_LIMIT + 5; i++) {
            items.add(segment(String.format("s%02d", i), String.format("Segment %02d", i), daysAgo(i)));
        }
        int total = items.size();

        PartialList<Metadata> unset = MergedMetadataListHelper.sortAndPage(items, 0, Integer.MIN_VALUE, "name:asc", DEFAULT_QUERY_LIMIT);
        assertEquals(DEFAULT_QUERY_LIMIT, unset.getList().size());
        assertEquals(total, unset.getTotalSize());
        assertEquals(Integer.MIN_VALUE, unset.getPageSize());

        PartialList<Metadata> otherNegative = MergedMetadataListHelper.sortAndPage(items, 0, -5, "name:asc", DEFAULT_QUERY_LIMIT);
        assertEquals(DEFAULT_QUERY_LIMIT, otherNegative.getList().size());

        PartialList<Metadata> all = MergedMetadataListHelper.sortAndPage(items, 0, -1, "name:asc", DEFAULT_QUERY_LIMIT);
        assertEquals(total, all.getList().size());
        assertEquals(-1, all.getPageSize());

        PartialList<Metadata> allFromOffset = MergedMetadataListHelper.sortAndPage(items, 10, -1, "name:asc", DEFAULT_QUERY_LIMIT);
        assertEquals(total - 10, allFromOffset.getList().size());

        PartialList<Metadata> zero = MergedMetadataListHelper.sortAndPage(items, 0, 0, "name:asc", DEFAULT_QUERY_LIMIT);
        assertEquals(0, zero.getList().size());
        assertEquals(total, zero.getTotalSize());

        PartialList<Metadata> largerThanTotal = MergedMetadataListHelper.sortAndPage(items, 0, Integer.MAX_VALUE, "name:asc", DEFAULT_QUERY_LIMIT);
        assertEquals(total, largerThanTotal.getList().size());

        PartialList<Metadata> pastEnd = MergedMetadataListHelper.sortAndPage(items, 100, 2, "name:asc", DEFAULT_QUERY_LIMIT);
        assertEquals(0, pastEnd.getList().size());
        assertEquals(total, pastEnd.getTotalSize());

        PartialList<Metadata> secondPage = MergedMetadataListHelper.sortAndPage(items, 2, 3, "name:asc", DEFAULT_QUERY_LIMIT);
        assertEquals(Arrays.asList("s02", "s03", "s04"), metadataIds(secondPage));
        assertEquals(2, secondPage.getOffset());
        assertEquals(3, secondPage.getPageSize());
        assertEquals(total, secondPage.getTotalSize());
        assertEquals(PartialList.Relation.EQUAL, secondPage.getTotalSizeRelation());

        PartialList<Metadata> unsetFromOffset = MergedMetadataListHelper.sortAndPage(items, 12, Integer.MIN_VALUE, "name:asc", DEFAULT_QUERY_LIMIT);
        assertEquals(Arrays.asList("s12", "s13", "s14"), metadataIds(unsetFromOffset));
        assertEquals(12, unsetFromOffset.getOffset());

        PartialList<Metadata> negativeOffset =MergedMetadataListHelper.sortAndPage(items, -3, 2, "name:asc", DEFAULT_QUERY_LIMIT);
        assertEquals(Arrays.asList("s00", "s01"), metadataIds(negativeOffset));
    }

    @Test
    public void testConfiguredDefaultQueryLimitIsApplied() {
        List<Segment> items = threeSegments();

        PartialList<Metadata> unset = MergedMetadataListHelper.sortAndPage(items, 0, Integer.MIN_VALUE, "name:asc", 2);
        assertEquals(Arrays.asList("a", "b"), metadataIds(unset));
        assertEquals(3, unset.getTotalSize());

        PartialList<Metadata> all = MergedMetadataListHelper.sortAndPage(items, 0, -1, "name:asc", 2);
        assertEquals(Arrays.asList("a", "b", "c"), metadataIds(all));
    }

    @Test
    public void testPagingThroughAllPagesReturnsEachItemOnce() {
        List<Segment> items = Arrays.asList(
                segment("a", "Alpha", daysAgo(1)),
                segment("b", "Bravo", daysAgo(2)),
                segment("c", "Charlie", daysAgo(3)),
                segment("d", "Delta", daysAgo(4)),
                segment("e", "Echo", daysAgo(5))
        );

        List<String> collected = new ArrayList<>();
        int pageSize = 2;
        for (int offset = 0; offset < items.size(); offset += pageSize) {
            PartialList<Metadata> page = MergedMetadataListHelper.sortAndPage(items, offset, pageSize, "name:asc", DEFAULT_QUERY_LIMIT);
            collected.addAll(metadataIds(page));
        }
        assertEquals(Arrays.asList("a", "b", "c", "d", "e"), collected);
    }

    private static List<Segment> threeSegments() {
        return new ArrayList<>(Arrays.asList(
                segment("c", "Charlie", daysAgo(3)),
                segment("a", "Alpha", daysAgo(1)),
                segment("b", "Bravo", daysAgo(2))
        ));
    }

    private static Segment segment(String id, String name, Date creationDate) {
        Segment segment = new Segment();
        segment.setItemId(id);
        Metadata metadata = new Metadata();
        metadata.setId(id);
        metadata.setName(name);
        metadata.setScope("systemscope");
        segment.setMetadata(metadata);
        segment.setCreationDate(creationDate);
        segment.setLastModificationDate(creationDate);
        return segment;
    }

    private static Date daysAgo(int days) {
        return new Date(System.currentTimeMillis() - days * 24L * 60L * 60L * 1000L);
    }

    private static List<String> ids(List<Segment> items) {
        return items.stream().map(Segment::getItemId).collect(Collectors.toList());
    }

    private static List<String> metadataIds(PartialList<Metadata> page) {
        return page.getList().stream().map(Metadata::getId).collect(Collectors.toList());
    }
}
