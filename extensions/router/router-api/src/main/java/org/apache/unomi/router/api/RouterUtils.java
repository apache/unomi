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

import java.util.Collection;
import java.util.Map;

/**
 * Created by amidani on 30/06/2017.
 */
public class RouterUtils {

    public static ImportExportConfiguration addExecutionEntry(ImportExportConfiguration configuration, Map execution, int executionsHistorySize) {
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

    public static char getCharFromLineSeparator(String lineSeparator) {
        char charLineSep = '\n';
        if ("\r".equals(lineSeparator)) {
            charLineSep = '\r';
        }
        return charLineSep;
    }

    public static PropertyType getPropertyTypeById(Collection<PropertyType> propertyTypes, String propertyTypeId) {
        for (PropertyType propertyType : propertyTypes) {
            if (propertyType.getMetadata().getId().equals(propertyTypeId)) {
                return propertyType;
            }
        }
        return null;
    }

}
