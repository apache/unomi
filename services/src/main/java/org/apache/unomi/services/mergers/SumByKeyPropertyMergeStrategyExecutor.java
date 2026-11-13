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

package org.apache.unomi.services.mergers;

import org.apache.unomi.api.Profile;
import org.apache.unomi.api.PropertyMergeStrategyExecutor;
import org.apache.unomi.api.PropertyType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Adds {@code value} for entries that share a {@code key}.
 * Used for {@code properties.interests}, a list of {@code {key, value}} objects.
 * A value that is not that list is left unchanged.
 */
public class SumByKeyPropertyMergeStrategyExecutor implements PropertyMergeStrategyExecutor {

    @Override
    public boolean mergeProperty(String propertyName, PropertyType propertyType, List<Profile> profilesToMerge, Profile targetProfile) {
        Object current = targetProfile.getProperty(propertyName);
        if (current != null && !(current instanceof List)) {
            return false;
        }

        List<Map<String, Object>> merged = entries(current);
        boolean changed = false;
        for (Profile profileToMerge : profilesToMerge) {
            Object incoming = profileToMerge.getProperty(propertyName);
            if (incoming == null) {
                continue;
            }
            if (!(incoming instanceof List)) {
                continue;
            }
            changed |= addInto(merged, entries(incoming));
        }
        if (!changed) {
            return false;
        }
        targetProfile.setProperty(propertyName, merged);
        return true;
    }

    private static boolean addInto(List<Map<String, Object>> target, List<Map<String, Object>> incoming) {
        boolean changed = false;
        for (Map<String, Object> entry : incoming) {
            String key = (String) entry.get("key");
            Map<String, Object> existing = null;
            for (Map<String, Object> candidate : target) {
                if (key.equals(candidate.get("key"))) {
                    existing = candidate;
                    break;
                }
            }
            if (existing == null) {
                target.add(entry);
                changed = true;
            } else {
                Number sum = add((Number) existing.get("value"), (Number) entry.get("value"));
                if (!sum.equals(existing.get("value"))) {
                    existing.put("value", sum);
                    changed = true;
                }
            }
        }
        return changed;
    }

    private static List<Map<String, Object>> entries(Object value) {
        List<Map<String, Object>> entries = new ArrayList<>();
        if (!(value instanceof List)) {
            return entries;
        }
        List<Map<String, Object>> raw = new ArrayList<>();
        for (Object item : (List<?>) value) {
            if (!(item instanceof Map)) {
                continue;
            }
            Map<?, ?> source = (Map<?, ?>) item;
            Object key = source.get("key");
            Number number = toNumber(source.get("value"));
            if (key == null || number == null) {
                continue;
            }
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> field : source.entrySet()) {
                if (field.getKey() != null) {
                    copy.put(field.getKey().toString(), field.getValue());
                }
            }
            copy.put("key", key.toString());
            copy.put("value", number);
            raw.add(copy);
        }
        addInto(entries, raw);
        return entries;
    }

    private static Number toNumber(Object value) {
        if (value instanceof Number) {
            return (Number) value;
        }
        return null;
    }

    private static Number add(Number left, Number right) {
        if (left instanceof Double || left instanceof Float || right instanceof Double || right instanceof Float) {
            return left.doubleValue() + right.doubleValue();
        }
        long sum = left.longValue() + right.longValue();
        if (sum >= Integer.MIN_VALUE && sum <= Integer.MAX_VALUE) {
            return (int) sum;
        }
        return sum;
    }
}
