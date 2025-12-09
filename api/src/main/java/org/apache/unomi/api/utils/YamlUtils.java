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

package org.apache.unomi.api.utils;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * YAML utilities using SnakeYaml with fluent API wrapper.
 * Provides utilities for building YAML structures and formatting them via SnakeYaml.
 */
public class YamlUtils {
    // SnakeYaml instance with configured options
    private static final Yaml YAML_INSTANCE;
    
    static {
        DumperOptions options = new DumperOptions();
        options.setIndent(2);
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setDefaultScalarStyle(DumperOptions.ScalarStyle.PLAIN);
        YAML_INSTANCE = new Yaml(options);
    }

    /**
     * Interface for objects that can convert themselves to YAML Map structures.
     */
    public interface YamlConvertible {
        /**
         * Converts this object to a Map structure for YAML output.
         *
         * @return a Map representation of this object
         */
        Map<String, Object> toYaml();
    }

    /**
     * Fluent builder for creating YAML Map structures.
     * Provides chaining methods to avoid repeating the map variable.
     */
    public static class YamlMapBuilder {
        private final Map<String, Object> map;

        private YamlMapBuilder() {
            this.map = new LinkedHashMap<>();
        }

        /**
         * Creates a new builder instance.
         *
         * @return a new YamlMapBuilder
         */
        public static YamlMapBuilder create() {
            return new YamlMapBuilder();
        }

        /**
         * Adds a field if the value is not null.
         *
         * @param key the key (must not be null)
         * @param value the value (only added if not null)
         * @return this builder for chaining
         * @throws NullPointerException if key is null
         */
        public YamlMapBuilder putIfNotNull(String key, Object value) {
            if (key == null) {
                throw new NullPointerException("Key must not be null");
            }
            if (value != null) {
                map.put(key, value);
            }
            return this;
        }

        /**
         * Adds a field if the condition is true.
         *
         * @param key the key (must not be null)
         * @param value the value (only added if condition is true)
         * @param condition the condition
         * @return this builder for chaining
         * @throws NullPointerException if key is null
         */
        public YamlMapBuilder putIf(String key, Object value, boolean condition) {
            if (key == null) {
                throw new NullPointerException("Key must not be null");
            }
            if (condition) {
                map.put(key, value);
            }
            return this;
        }

        /**
         * Adds a field unconditionally.
         *
         * @param key the key (must not be null)
         * @param value the value
         * @return this builder for chaining
         * @throws NullPointerException if key is null
         */
        public YamlMapBuilder put(String key, Object value) {
            if (key == null) {
                throw new NullPointerException("Key must not be null");
            }
            map.put(key, value);
            return this;
        }

        /**
         * Adds a field if the collection is not null and not empty.
         *
         * @param key the key (must not be null)
         * @param collection the collection (only added if not null and not empty)
         * @return this builder for chaining
         * @throws NullPointerException if key is null
         */
        public YamlMapBuilder putIfNotEmpty(String key, java.util.Collection<?> collection) {
            if (key == null) {
                throw new NullPointerException("Key must not be null");
            }
            if (collection != null && !collection.isEmpty()) {
                map.put(key, collection);
            }
            return this;
        }

        /**
         * Builds and returns a defensive copy of the map.
         *
         * @return a new LinkedHashMap containing the built entries
         */
        public Map<String, Object> build() {
            return new LinkedHashMap<>(map);
        }
    }

    /**
     * Converts a Set to a sorted List for YAML output.
     *
     * @param set the set to convert
     * @return a sorted list, or null if the set is null or empty
     */
    public static <T extends Comparable<T>> List<T> setToSortedList(Set<T> set) {
        if (set == null || set.isEmpty()) {
            return null;
        }
        return set.stream().sorted().collect(Collectors.toList());
    }

    /**
     * Converts a Set to a sorted List using a mapper function.
     *
     * @param set the set to convert
     * @param mapper the mapper function (must not be null)
     * @return a sorted list, or null if the set is null or empty
     * @throws NullPointerException if mapper is null
     */
    public static <T, R extends Comparable<R>> List<R> setToSortedList(Set<T> set, Function<T, R> mapper) {
        if (mapper == null) {
            throw new NullPointerException("Mapper function must not be null");
        }
        if (set == null || set.isEmpty()) {
            return null;
        }
        return set.stream().map(mapper).sorted().collect(Collectors.toList());
    }

    /**
     * Converts a value to YAML-compatible format, handling nested structures.
     * Note: This method does not perform circular reference detection for generic objects.
     * For objects that implement YamlConvertible, circular reference detection should be
     * handled in their toYaml() implementation.
     *
     * @param value the value to convert
     * @param visited set of visited objects (currently unused, reserved for future circular reference detection)
     * @return the converted value
     */
    public static Object toYamlValue(Object value, Set<Object> visited) {
        if (value == null) {
            return null;
        }
        if (value instanceof YamlConvertible) {
            return ((YamlConvertible) value).toYaml();
        }
        if (value instanceof List) {
            return ((List<?>) value).stream()
                .map(item -> toYamlValue(item, visited))
                .collect(Collectors.toList());
        }
        if (value instanceof Map) {
            Map<String, Object> result = new LinkedHashMap<>();
            ((Map<?, ?>) value).forEach((key, val) ->
                result.put(String.valueOf(key), toYamlValue(val, visited)));
            return result;
        }
        return value;
    }

    /**
     * Formats a value as YAML using SnakeYaml.
     * This is a convenience method that delegates to SnakeYaml.
     *
     * @param value the value to format
     * @return YAML string representation
     */
    public static String format(Object value) {
        return YAML_INSTANCE.dump(value);
    }

    /**
     * Creates a circular reference marker map.
     *
     * @return a map indicating a circular reference
     */
    public static Map<String, Object> circularRef() {
        return YamlMapBuilder.create()
            .put("$ref", "circular")
            .build();
    }
}
