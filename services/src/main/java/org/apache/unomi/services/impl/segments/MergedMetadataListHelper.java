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

import org.apache.commons.beanutils.PropertyUtils;
import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.MetadataItem;
import org.apache.unomi.api.PartialList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sorts and pages merged system+tenant {@link MetadataItem} lists for segment and scoring queries.
 * Only a fixed allowlist of sort fields is accepted. A missing or negative limit means "all",
 * capped by {@link #DEFAULT_MAX_PAGE_SIZE}.
 */
final class MergedMetadataListHelper {

    static final int DEFAULT_MAX_PAGE_SIZE = 5000;

    private static final Map<String, String> ALLOWED_SORT_FIELDS = new HashMap<>();

    static {
        ALLOWED_SORT_FIELDS.put("id", "metadata.id");
        ALLOWED_SORT_FIELDS.put("metadata.id", "metadata.id");
        ALLOWED_SORT_FIELDS.put("itemId", "itemId");
        ALLOWED_SORT_FIELDS.put("name", "metadata.name");
        ALLOWED_SORT_FIELDS.put("metadata.name", "metadata.name");
        ALLOWED_SORT_FIELDS.put("scope", "metadata.scope");
        ALLOWED_SORT_FIELDS.put("metadata.scope", "metadata.scope");
        ALLOWED_SORT_FIELDS.put("description", "metadata.description");
        ALLOWED_SORT_FIELDS.put("metadata.description", "metadata.description");
        ALLOWED_SORT_FIELDS.put("enabled", "metadata.enabled");
        ALLOWED_SORT_FIELDS.put("metadata.enabled", "metadata.enabled");
        ALLOWED_SORT_FIELDS.put("creationDate", "creationDate");
        ALLOWED_SORT_FIELDS.put("lastModificationDate", "lastModificationDate");
    }

    private MergedMetadataListHelper() {
    }

    static int resolveLimit(int limit) {
        return resolveLimit(limit, DEFAULT_MAX_PAGE_SIZE);
    }

    static int resolveLimit(int limit, int maxPageSize) {
        if (limit < 0) {
            return maxPageSize;
        }
        return Math.min(limit, maxPageSize);
    }

    static <T extends MetadataItem> PartialList<Metadata> sortAndPage(
            Collection<T> mergedItems, int offset, int limit, String sortBy) {
        return sortAndPage(mergedItems, offset, limit, sortBy, DEFAULT_MAX_PAGE_SIZE);
    }

    static <T extends MetadataItem> PartialList<Metadata> sortAndPage(
            Collection<T> mergedItems, int offset, int limit, String sortBy, int maxPageSize) {
        List<T> list = new ArrayList<>(mergedItems);
        sort(list, sortBy);

        int pageSize = resolveLimit(limit, maxPageSize);
        int totalSize = list.size();
        int fromIndex = offset;
        if (fromIndex >= totalSize || pageSize == 0) {
            return new PartialList<>(new ArrayList<>(), offset, pageSize, totalSize, PartialList.Relation.EQUAL);
        }
        int toIndex = fromIndex + pageSize;
        if (toIndex > totalSize) {
            toIndex = totalSize;
        }

        List<Metadata> page = new ArrayList<>(toIndex - fromIndex);
        for (T item : list.subList(fromIndex, toIndex)) {
            page.add(item.getMetadata());
        }
        return new PartialList<>(page, offset, pageSize, totalSize, PartialList.Relation.EQUAL);
    }

    static void sort(List<? extends MetadataItem> items, String sortBy) {
        if (sortBy == null || sortBy.trim().isEmpty()) {
            return;
        }

        String[] criteria = sortBy.split(",");
        String[] propertyPaths = new String[criteria.length];
        boolean[] descending = new boolean[criteria.length];

        for (int i = 0; i < criteria.length; i++) {
            String criterion = criteria[i].trim();
            if (criterion.isEmpty()) {
                throw new IllegalArgumentException("Empty sort field in: " + sortBy);
            }
            String[] parts = criterion.split(":");
            String field = parts[0].trim();
            String propertyPath = ALLOWED_SORT_FIELDS.get(field);
            if (propertyPath == null) {
                throw new IllegalArgumentException("Unsupported sort field: " + field);
            }
            propertyPaths[i] = propertyPath;
            descending[i] = parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim());
        }

        items.sort((left, right) -> {
            for (int i = 0; i < propertyPaths.length; i++) {
                int comparison = compareProperties(left, right, propertyPaths[i]);
                if (comparison != 0) {
                    return descending[i] ? -comparison : comparison;
                }
            }
            return 0;
        });
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static int compareProperties(MetadataItem left, MetadataItem right, String propertyPath) {
        Object leftValue = getProperty(left, propertyPath);
        Object rightValue = getProperty(right, propertyPath);
        if (leftValue == null && rightValue == null) {
            return 0;
        }
        if (leftValue == null) {
            return -1;
        }
        if (rightValue == null) {
            return 1;
        }
        if (!(leftValue instanceof Comparable) || !(rightValue instanceof Comparable)) {
            return leftValue.toString().compareTo(rightValue.toString());
        }
        return ((Comparable) leftValue).compareTo(rightValue);
    }

    private static Object getProperty(MetadataItem item, String propertyPath) {
        try {
            return PropertyUtils.getProperty(item, propertyPath);
        } catch (Exception e) {
            return null;
        }
    }
}
