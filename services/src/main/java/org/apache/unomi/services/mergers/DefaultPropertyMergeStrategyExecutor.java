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
import org.apache.unomi.persistence.spi.PropertyHelper;

import java.util.List;

public class DefaultPropertyMergeStrategyExecutor implements PropertyMergeStrategyExecutor {
    public boolean mergeProperty(String propertyName, PropertyType propertyType, List<Profile> profilesToMerge, Profile targetProfile) {
        boolean modified = false;
        for (Profile profileToMerge : profilesToMerge) {
            if (profileToMerge.getNestedProperty(propertyName) != null &&
                    profileToMerge.getNestedProperty(propertyName).toString().length() > 0) {
                PropertyHelper.setProperty(targetProfile, "properties." + propertyName, profileToMerge.getNestedProperty(propertyName), "alwaysSet");
                modified = true;
            }
        }
        return modified;
    }
}
