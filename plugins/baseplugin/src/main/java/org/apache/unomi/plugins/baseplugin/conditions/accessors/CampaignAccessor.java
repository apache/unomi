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
package org.apache.unomi.plugins.baseplugin.conditions.accessors;

import org.apache.unomi.api.campaigns.Campaign;
import org.apache.unomi.plugins.baseplugin.conditions.HardcodedPropertyAccessorRegistry;

public class CampaignAccessor extends HardcodedPropertyAccessor<Campaign> {
    public CampaignAccessor(HardcodedPropertyAccessorRegistry registry) {
        super(registry);
    }

    @Override
    public Object getProperty(Campaign object, String propertyName, String leftoverExpression) {
        if ("startDate".equals(propertyName)) {
            return object.getStartDate();
        } else if ("endDate".equals(propertyName)) {
            return object.getEndDate();
        } else if ("cost".equals(propertyName)) {
            return object.getCost();
        } else if ("currency".equals(propertyName)) {
            return object.getCurrency();
        } else if ("primaryGoal".equals(propertyName)) {
            return object.getPrimaryGoal();
        } else if ("timezone".equals(propertyName)) {
            return object.getTimezone();
        }
        return PROPERTY_NOT_FOUND_MARKER;
    }
}
