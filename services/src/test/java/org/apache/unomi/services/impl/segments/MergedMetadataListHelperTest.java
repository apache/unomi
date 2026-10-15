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

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class MergedMetadataListHelperTest {

    @Test
    public void testResolveLimitTreatsNegativeAsAllCapped() {
        assertEquals(MergedMetadataListHelper.DEFAULT_MAX_PAGE_SIZE, MergedMetadataListHelper.resolveLimit(-1));
        assertEquals(MergedMetadataListHelper.DEFAULT_MAX_PAGE_SIZE, MergedMetadataListHelper.resolveLimit(Integer.MIN_VALUE));
        assertEquals(3, MergedMetadataListHelper.resolveLimit(-1, 3));
        assertEquals(0, MergedMetadataListHelper.resolveLimit(0));
        assertEquals(10, MergedMetadataListHelper.resolveLimit(10));
        assertEquals(5, MergedMetadataListHelper.resolveLimit(100, 5));
    }

    @Test
    public void testSortByNameAscendingAndDescending() {
        List<Segment> items = Arrays.asList(
                segment("c", "Charlie", daysAgo(3)),
                segment("a", "Alpha", daysAgo(1)),
                segment("b", "Bravo", daysAgo(2))
        );

        MergedMetadataListHelper.sort(items, "name:asc");
        assertEquals(Arrays.asList("a", "b", "c"), ids(items));

        MergedMetadataListHelper.sort(items, "metadata.name:desc");
        assertEquals(Arrays.asList("c", "b", "a"), ids(items));
    }

    @Test
    public void testSortByCreationDateAscendingAndDescending() {
        List<Segment> items = Arrays.asList(
                segment("c", "Charlie", daysAgo(3)),
                segment("a", "Alpha", daysAgo(1)),
                segment("b", "Bravo", daysAgo(2))
        );

        MergedMetadataListHelper.sort(items, "creationDate:asc");
        assertEquals(Arrays.asList("c", "b", "a"), ids(items));

        MergedMetadataListHelper.sort(items, "creationDate:desc");
        assertEquals(Arrays.asList("a", "b", "c"), ids(items));
    }

    @Test
    public void testUnknownSortFieldIsRefused() {
        List<Segment> items = Arrays.asList(segment("a", "Alpha", daysAgo(1)));
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> MergedMetadataListHelper.sort(items, "unknownField:asc"));
        assertTrue(exception.getMessage().contains("unknownField"));
    }

    @Test
    public void testPagingVariantsAndMaxCap() {
        List<Segment> items = Arrays.asList(
                segment("a", "Alpha", daysAgo(1)),
                segment("b", "Bravo", daysAgo(2)),
                segment("c", "Charlie", daysAgo(3)),
                segment("d", "Delta", daysAgo(4)),
                segment("e", "Echo", daysAgo(5))
        );

        PartialList<Metadata> noLimit = MergedMetadataListHelper.sortAndPage(items, 0, Integer.MIN_VALUE, "name:asc", 3);
        assertEquals(3, noLimit.getList().size());
        assertEquals(5, noLimit.getTotalSize());
        assertEquals(Arrays.asList("a", "b", "c"), metadataIds(noLimit));

        PartialList<Metadata> negativeOne = MergedMetadataListHelper.sortAndPage(items, 0, -1, "name:asc", 4);
        assertEquals(4, negativeOne.getList().size());
        assertEquals(Arrays.asList("a", "b", "c", "d"), metadataIds(negativeOne));

        PartialList<Metadata> zero = MergedMetadataListHelper.sortAndPage(items, 0, 0, "name:asc");
        assertEquals(0, zero.getList().size());
        assertEquals(5, zero.getTotalSize());

        PartialList<Metadata> largerThanTotal = MergedMetadataListHelper.sortAndPage(items, 0, 100, "name:asc");
        assertEquals(5, largerThanTotal.getList().size());

        PartialList<Metadata> pastEnd = MergedMetadataListHelper.sortAndPage(items, 10, 2, "name:asc");
        assertEquals(0, pastEnd.getList().size());
        assertEquals(5, pastEnd.getTotalSize());
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

        List<String> collected = new java.util.ArrayList<>();
        int pageSize = 2;
        for (int offset = 0; offset < items.size(); offset += pageSize) {
            PartialList<Metadata> page = MergedMetadataListHelper.sortAndPage(items, offset, pageSize, "name:asc");
            collected.addAll(metadataIds(page));
        }
        assertEquals(Arrays.asList("a", "b", "c", "d", "e"), collected);
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
