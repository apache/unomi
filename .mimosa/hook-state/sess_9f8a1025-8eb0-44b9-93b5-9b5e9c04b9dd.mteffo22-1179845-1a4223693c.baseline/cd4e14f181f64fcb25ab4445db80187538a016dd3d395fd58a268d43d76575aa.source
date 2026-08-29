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
package org.apache.unomi.router.api;

import org.apache.unomi.api.PropertyType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

/**
 * Helper routines for building router import/export column mappings.
 * Converts Unomi {@link PropertyType} metadata into header lists and default
 * values used when CSV or similar files are parsed by router Camel processors.
 */
public class RouterUtils {

    /**
     * Appends an execution entry to the configuration history, trimming oldest entries when the limit is reached.
     *
     * @param configuration the import/export configuration to update
     * @param execution the execution metadata to record
     * @param executionsHistorySize maximum number of execution entries to retain
     * @return the updated configuration
     */
    public static ImportExportConfiguration addExecutionEntry(ImportExportConfiguration configuration, Map execution, int executionsHistorySize) {
        if (configuration.getExecutions() == null) {
            configuration.setExecutions(new ArrayList<>());
        }
        if (configuration.getExecutions().size() >= executionsHistorySize) {
            int oldestExecIndex = 0;
            long oldestExecDate = (Long) configuration.getExecutions().get(0).get(RouterConstants.KEY_EXECS_DATE);
            for (int i = 1; i < configuration.getExecutions().size(); i++) {
                if ((Long) configuration.getExecutions().get(i).get(RouterConstants.KEY_EXECS_DATE) < oldestExecDate) {
                    oldestExecDate = (Long) configuration.getExecutions().get(i).get(RouterConstants.KEY_EXECS_DATE);
                    oldestExecIndex = i;
                }
            }
            configuration.getExecutions().remove(oldestExecIndex);
        }

        configuration.getExecutions().add(execution);
        return configuration;
    }

    /**
     * Converts a line-separator string to its single-character form.
     *
     * @param lineSeparator the configured line separator (for example {@code "\n"} or {@code "\r"})
     * @return the corresponding line-separator character
     */
    public static char getCharFromLineSeparator(String lineSeparator) {
        char charLineSep = '\n';
        if ("\r".equals(lineSeparator)) {
            charLineSep = '\r';
        }
        return charLineSep;
    }

    /**
     * Finds a property type by identifier in a collection.
     *
     * @param propertyTypes the property types to search
     * @param propertyTypeId the property type identifier to match
     * @return the matching property type, or {@code null} if none is found
     */
    public static PropertyType getPropertyTypeById(Collection<PropertyType> propertyTypes, String propertyTypeId) {
        for (PropertyType propertyType : propertyTypes) {
            if (propertyType.getMetadata().getId().equals(propertyTypeId)) {
                return propertyType;
            }
        }
        return null;
    }

}
