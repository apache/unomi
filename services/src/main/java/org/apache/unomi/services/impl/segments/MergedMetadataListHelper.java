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

import org.apache.commons.lang3.StringUtils;
import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.MetadataItem;
import org.apache.unomi.api.PartialList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Sorts and pages merged system+tenant {@link MetadataItem} lists for segment and scoring queries.
 * <p>
 * Before multi-tenancy these lists were sorted and paged by the search engine, so this helper reproduces the
 * persistence semantics that Unomi 2.x and 3.0 clients rely on:
 * <ul>
 *     <li>a size of {@code -1} returns every item, an unset ({@link Integer#MIN_VALUE}) or other negative size
 *     returns the persistence default query limit, and the requested size is echoed back as the page size;</li>
 *     <li>sort criteria use the persistence syntax ({@code field[:asc|:desc]}, comma separated), and criteria
 *     on unknown fields are ignored rather than rejected;</li>
 *     <li>missing values sort last in both directions, and multi-valued fields sort on their smallest value
 *     ascending and largest value descending.</li>
 * </ul>
 * Only a fixed set of fields can be sorted on; values are read through plain getters, never by reflection on a
 * caller-supplied path.
 */
final class MergedMetadataListHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(MergedMetadataListHelper.class);

    private static final Map<String, Function<MetadataItem, Object>> SORT_FIELDS = new HashMap<>();

    static {
        addMetadataField("id", Metadata::getId);
        addMetadataField("name", Metadata::getName);
        addMetadataField("description", Metadata::getDescription);
        addMetadataField("scope", Metadata::getScope);
        addMetadataField("tags", Metadata::getTags);
        addMetadataField("systemTags", Metadata::getSystemTags);
        addMetadataField("enabled", Metadata::isEnabled);
        addMetadataField("missingPlugins", Metadata::isMissingPlugins);
        addMetadataField("hidden", Metadata::isHidden);
        addMetadataField("readOnly", Metadata::isReadOnly);
        SORT_FIELDS.put("itemId", MetadataItem::getItemId);
        SORT_FIELDS.put("tenantId", MetadataItem::getTenantId);
        SORT_FIELDS.put("createdBy", MetadataItem::getCreatedBy);
        SORT_FIELDS.put("lastModifiedBy", MetadataItem::getLastModifiedBy);
        SORT_FIELDS.put("creationDate", MetadataItem::getCreationDate);
        SORT_FIELDS.put("lastModificationDate", MetadataItem::getLastModificationDate);
        SORT_FIELDS.put("lastSyncDate", MetadataItem::getLastSyncDate);
    }

    private static final Comparator<MetadataItem> TIE_BREAKER =
            Comparator.comparing(MetadataItem::getItemId, Comparator.nullsLast(Comparator.naturalOrder()));

    private MergedMetadataListHelper() {
    }

    /**
     * Registers a metadata field under both its persistence path ({@code metadata.name}) and its short alias
     * ({@code name}).
     */
    private static void addMetadataField(String name, Function<Metadata, Object> getter) {
        Function<MetadataItem, Object> extractor = item -> item.getMetadata() != null ? getter.apply(item.getMetadata()) : null;
        SORT_FIELDS.put("metadata." + name, extractor);
        SORT_FIELDS.put(name, extractor);
    }

    /**
     * @param defaultQueryLimit the number of items returned when no size is given, normally
     *                          {@link org.apache.unomi.persistence.spi.PersistenceService#getDefaultQueryLimit()}
     */
    static <T extends MetadataItem> PartialList<Metadata> sortAndPage(Collection<T> mergedItems, int offset, int size, String sortBy,
                                                                    int defaultQueryLimit) {
        List<T> list = new ArrayList<>(mergedItems);
        sort(list, sortBy);

        int totalSize = list.size();
        int fromIndex = Math.max(offset, 0);
        int pageSize = size == -1 ? totalSize : (size < 0 ? defaultQueryLimit : size);
        int toIndex = (int) Math.min((long) fromIndex + pageSize, totalSize);

        List<Metadata> page = new ArrayList<>();
        if (fromIndex < toIndex) {
            for (T item : list.subList(fromIndex, toIndex)) {
                page.add(item.getMetadata());
            }
        }
        return new PartialList<>(page, offset, size, totalSize, PartialList.Relation.EQUAL);
    }

    static void sort(List<? extends MetadataItem> items, String sortBy) {
        Comparator<MetadataItem> comparator = buildComparator(sortBy);
        if (comparator != null) {
            items.sort(comparator.thenComparing(TIE_BREAKER));
        }
    }

    private static Comparator<MetadataItem> buildComparator(String sortBy) {
        if (StringUtils.isBlank(sortBy)) {
            return null;
        }
        Comparator<MetadataItem> comparator = null;
        for (String criterion : sortBy.split(",")) {
            // Same parsing as the persistence services: the field is everything before the last ':' and the
            // order is descending only for a ":desc" suffix.
            String field = StringUtils.substringBeforeLast(criterion, ":").trim();
            Function<MetadataItem, Object> extractor = SORT_FIELDS.get(field);
            if (extractor == null) {
                LOGGER.debug("Ignoring unsupported sort field '{}' in '{}'", field, sortBy);
                continue;
            }
            boolean descending = criterion.trim().endsWith(":desc");
            Comparator<MetadataItem> fieldComparator = (left, right) ->
                    compareValues(sortValue(extractor.apply(left), descending), sortValue(extractor.apply(right), descending), descending);
            comparator = comparator == null ? fieldComparator : comparator.thenComparing(fieldComparator);
        }
        return comparator;
    }

    /**
     * Reduces a multi-valued field to the value the search engine sorts on: the smallest one ascending, the largest
     * one descending. An empty collection counts as a missing value.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object sortValue(Object value, boolean descending) {
        if (!(value instanceof Collection)) {
            return value;
        }
        Collection<Comparable> values = (Collection<Comparable>) value;
        if (values.isEmpty()) {
            return null;
        }
        return descending ? Collections.max(values) : Collections.min(values);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static int compareValues(Object left, Object right, boolean descending) {
        if (left == null || right == null) {
            // Missing values go last whatever the order.
            return left == null ? (right == null ? 0 : 1) : -1;
        }
        int comparison = ((Comparable) left).compareTo(right);
        return descending ? -comparison : comparison;
    }
}
