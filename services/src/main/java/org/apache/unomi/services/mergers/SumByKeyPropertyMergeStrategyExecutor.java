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
 * A {@code value} may be a number or a numeric string. Entries that cannot be summed are kept as they are.
 * If the target already holds something other than a list, it is left unchanged.
 * If the target has no value and no list is merged in, the first other non-null value is copied.
 */
public class SumByKeyPropertyMergeStrategyExecutor implements PropertyMergeStrategyExecutor {

    @Override
    public boolean mergeProperty(String propertyName, PropertyType propertyType, List<Profile> profilesToMerge, Profile targetProfile) {
        Object current = targetProfile.getProperty(propertyName);
        if (current != null && !(current instanceof List)) {
            return false;
        }

        List<Object> merged = entries(current);
        Object fallback = null;
        boolean changed = false;
        for (Profile profileToMerge : profilesToMerge) {
            Object incoming = profileToMerge.getProperty(propertyName);
            if (incoming == null) {
                continue;
            }
            if (!(incoming instanceof List)) {
                if (fallback == null) {
                    fallback = incoming;
                }
                continue;
            }
            changed |= addInto(merged, (List<?>) incoming);
        }
        if (!changed) {
            if (current == null && fallback != null) {
                targetProfile.setProperty(propertyName, fallback);
                return true;
            }
            return false;
        }
        targetProfile.setProperty(propertyName, merged);
        return true;
    }

    private static List<Object> entries(Object value) {
        List<Object> entries = new ArrayList<>();
        if (value instanceof List) {
            addInto(entries, (List<?>) value);
        }
        return entries;
    }

    private static boolean addInto(List<Object> target, List<?> incoming) {
        boolean changed = false;
        for (Object item : incoming) {
            Map<String, Object> entry = summable(item);
            if (entry == null) {
                if (item != null && !target.contains(item)) {
                    target.add(item);
                    changed = true;
                }
                continue;
            }
            Map<String, Object> existing = find(target, entry.get("key"));
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

    @SuppressWarnings("unchecked")
    private static Map<String, Object> find(List<Object> target, Object key) {
        for (Object candidate : target) {
            if (candidate instanceof Map && key.equals(((Map<String, Object>) candidate).get("key"))
                    && ((Map<String, Object>) candidate).get("value") instanceof Number) {
                return (Map<String, Object>) candidate;
            }
        }
        return null;
    }

    /**
     * Returns a copy of {@code item} with a string key and a numeric value, or null if it cannot be summed.
     */
    private static Map<String, Object> summable(Object item) {
        if (!(item instanceof Map)) {
            return null;
        }
        Map<?, ?> source = (Map<?, ?>) item;
        Object key = source.get("key");
        Number number = toNumber(source.get("value"));
        if (key == null || number == null) {
            return null;
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<?, ?> field : source.entrySet()) {
            if (field.getKey() != null) {
                copy.put(field.getKey().toString(), field.getValue());
            }
        }
        copy.put("key", key.toString());
        copy.put("value", number);
        return copy;
    }

    private static Number toNumber(Object value) {
        if (value instanceof Number) {
            return (Number) value;
        }
        if (!(value instanceof String)) {
            return null;
        }
        String text = ((String) value).trim();
        try {
            long parsed = Long.parseLong(text);
            return parsed >= Integer.MIN_VALUE && parsed <= Integer.MAX_VALUE ? (Number) (int) parsed : (Number) parsed;
        } catch (NumberFormatException e) {
            // not an integer, try a decimal below
        }
        try {
            double parsed = Double.parseDouble(text);
            return Double.isFinite(parsed) ? parsed : null;
        } catch (NumberFormatException e) {
            return null;
        }
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
