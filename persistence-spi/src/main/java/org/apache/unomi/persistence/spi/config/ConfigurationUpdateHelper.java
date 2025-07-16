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
package org.apache.unomi.persistence.spi.config;

import org.slf4j.Logger;

import java.util.Dictionary;
import java.util.function.Consumer;
import java.util.Map;

/**
 * Utility class to handle configuration updates in ManagedService implementations.
 * This class provides generic methods to process configuration property changes
 * without hardcoding specific property names.
 */
public class ConfigurationUpdateHelper {

    /**
     * Processes configuration updates using a property mapping.
     *
     * @param properties The configuration properties dictionary
     * @param logger The logger to use for debug messages
     * @param serviceName The name of the service for logging purposes
     * @param propertyMappings Map of property names to their setters and types
     */
    public static void processConfigurationUpdates(Dictionary<String, ?> properties, Logger logger,
                                                 String serviceName,
                                                 Map<String, PropertyMapping> propertyMappings) {
        if (properties == null) {
            return;
        }

        logger.info("{} configuration updated, applying changes without restart", serviceName);

        try {
            for (Map.Entry<String, PropertyMapping> entry : propertyMappings.entrySet()) {
                String propertyName = entry.getKey();
                PropertyMapping mapping = entry.getValue();

                Object value = properties.get(propertyName);
                if (value != null) {
                    try {
                        mapping.apply(value, logger);
                        logger.debug("Updated {} to: {}", propertyName, value);
                    } catch (Exception e) {
                        logger.warn("Error setting property {}: {}", propertyName, e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error applying configuration updates", e);
        }
    }

    /**
     * Creates a boolean property mapping.
     *
     * @param setter The setter function to call with the boolean value
     * @return PropertyMapping for boolean properties
     */
    public static PropertyMapping booleanProperty(Consumer<Boolean> setter) {
        return (value, logger) -> {
            boolean boolValue = Boolean.parseBoolean(value.toString());
            setter.accept(boolValue);
        };
    }

    /**
     * Creates a string property mapping.
     *
     * @param setter The setter function to call with the string value
     * @return PropertyMapping for string properties
     */
    public static PropertyMapping stringProperty(Consumer<String> setter) {
        return (value, logger) -> {
            String stringValue = value.toString();
            setter.accept(stringValue);
        };
    }

    /**
     * Creates an integer property mapping.
     *
     * @param setter The setter function to call with the integer value
     * @return PropertyMapping for integer properties
     */
    public static PropertyMapping integerProperty(Consumer<Integer> setter) {
        return (value, logger) -> {
            int intValue = Integer.parseInt(value.toString());
            setter.accept(intValue);
        };
    }

    /**
     * Creates a long property mapping.
     *
     * @param setter The setter function to call with the long value
     * @return PropertyMapping for long properties
     */
    public static PropertyMapping longProperty(Consumer<Long> setter) {
        return (value, logger) -> {
            long longValue = Long.parseLong(value.toString());
            setter.accept(longValue);
        };
    }

    /**
     * Creates a custom property mapping for special cases.
     *
     * @param processor The custom processor function
     * @return PropertyMapping for custom properties
     */
    public static PropertyMapping customProperty(PropertyProcessor processor) {
        return processor::process;
    }

    /**
     * Functional interface for property processing.
     */
    @FunctionalInterface
    public interface PropertyMapping {
        /**
         * Applies the property value using the appropriate setter.
         *
         * @param value The property value
         * @param logger The logger to use
         * @throws Exception if there's an error processing the property
         */
        void apply(Object value, Logger logger) throws Exception;
    }

    /**
     * Functional interface for custom property processing.
     */
    @FunctionalInterface
    public interface PropertyProcessor {
        /**
         * Processes the property value.
         *
         * @param value The property value
         * @param logger The logger to use
         * @throws Exception if there's an error processing the property
         */
        void process(Object value, Logger logger) throws Exception;
    }
}
