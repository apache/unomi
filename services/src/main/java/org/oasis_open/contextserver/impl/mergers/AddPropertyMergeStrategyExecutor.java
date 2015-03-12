package org.oasis_open.contextserver.impl.mergers;

/*
 * #%L
 * context-server-services
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2014 - 2015 Jahia Solutions
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import org.oasis_open.contextserver.api.PropertyMergeStrategyExecutor;
import org.oasis_open.contextserver.api.PropertyType;
import org.oasis_open.contextserver.api.Profile;

import java.util.List;

public class AddPropertyMergeStrategyExecutor implements PropertyMergeStrategyExecutor {
    public boolean mergeProperty(String propertyName, PropertyType propertyType, List<Profile> profilesToMerge, Profile targetProfile) {
        Object result = null;
        if (propertyType.getValueTypeId() != null) {
            if (propertyType.getValueTypeId().equals("integer")) {
                result = new Integer(0);
            } else if (propertyType.getValueTypeId().equals("long")) {
                result = new Long(0);
            } else if (propertyType.getValueTypeId().equals("double")) {
                result = new Double(0.0);
            } else if (propertyType.getValueTypeId().equals("float")) {
                result = new Float(0.0);
            } else {
                result = new Long(0);
            }
        } else {
            result = new Long(0);
        }

        for (Profile profileToMerge : profilesToMerge) {

            if (profileToMerge.getProperty(propertyName) == null) {
                continue;
            }

            if (propertyType != null) {
                if (propertyType.getValueTypeId().equals("integer") || (profileToMerge.getProperty(propertyName) instanceof Integer)) {
                    result = (Integer) result + (Integer) profileToMerge.getProperty(propertyName);
                } else if (propertyType.getValueTypeId().equals("long") || (profileToMerge.getProperty(propertyName) instanceof Long)) {
                    result = (Long) result + (Long) profileToMerge.getProperty(propertyName);
                } else if (propertyType.getValueTypeId().equals("double") || (profileToMerge.getProperty(propertyName) instanceof Double)) {
                    result = (Double) result + (Double) profileToMerge.getProperty(propertyName);
                } else if (propertyType.getValueTypeId().equals("float") || (profileToMerge.getProperty(propertyName) instanceof Float)) {
                    result = (Float) result + (Float) profileToMerge.getProperty(propertyName);
                } else {
                    result = (Long) result + Long.parseLong(profileToMerge.getProperty(propertyName).toString());
                }
            } else {
                result = (Long) result + Long.parseLong(profileToMerge.getProperty(propertyName).toString());
            }

        }
        if (targetProfile.getProperty(propertyName) == null || !targetProfile.getProperty(propertyName).equals(result)) {
            targetProfile.setProperty(propertyName, result);
            return true;
        }
        return false;
    }
}
