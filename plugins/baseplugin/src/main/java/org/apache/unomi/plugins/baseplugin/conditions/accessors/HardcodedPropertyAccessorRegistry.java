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

import org.apache.unomi.api.*;
import org.apache.unomi.api.campaigns.Campaign;
import org.apache.unomi.api.goals.Goal;
import org.apache.unomi.api.rules.Rule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * This class contains the registry of all the hardcoded property accessors.
 * For the moment this list of accessors is hardcoded, but in a future update it could be made dynamic.
 */
public class HardcodedPropertyAccessorRegistry {

    private static final Logger logger = LoggerFactory.getLogger(HardcodedPropertyAccessorRegistry.class.getName());

    Map<String, HardcodedPropertyAccessor> propertyAccessors = new HashMap<>();

    public HardcodedPropertyAccessorRegistry() {
        propertyAccessors.put(Item.class.getName(), new ItemHardcodedPropertyAccessor(this));
        propertyAccessors.put(MetadataItem.class.getName(), new MetadataItemHardcodedPropertyAccessor(this));
        propertyAccessors.put(Metadata.class.getName(), new MetadataHardcodedPropertyAccessor(this));
        propertyAccessors.put(TimestampedItem.class.getName(), new TimestampedItemHardcodedPropertyAccessor(this));
        propertyAccessors.put(Event.class.getName(), new EventHardcodedPropertyAccessor(this));
        propertyAccessors.put(Profile.class.getName(), new ProfileHardcodedPropertyAccessor(this));
        propertyAccessors.put(Consent.class.getName(), new ConsentHardcodedPropertyAccessor(this));
        propertyAccessors.put(Session.class.getName(), new SessionHardcodedPropertyAccessor(this));
        propertyAccessors.put(Rule.class.getName(), new RuleHardcodedPropertyAccessor(this));
        propertyAccessors.put(Goal.class.getName(), new GoalHardcodedPropertyAccessor(this));
        propertyAccessors.put(CustomItem.class.getName(), new CustomItemHardcodedPropertyAccessor(this));
        propertyAccessors.put(Campaign.class.getName(), new CampaignHardcodedPropertyAccessor(this));
        propertyAccessors.put(Map.class.getName(), new MapHardcodedPropertyAccessor(this));
    }

    public static class NextTokens {
        public String propertyName;
        public String leftoverExpression;
    }

    protected NextTokens getNextTokens(String expression) {
        NextTokens nextTokens = new NextTokens();
        if (expression.startsWith("[\"")) {
            int lookupNameBeginPos = "[\"".length();
            int lookupNameEndPos = expression.indexOf("\"].", lookupNameBeginPos);
            if (lookupNameEndPos > lookupNameBeginPos) {
                nextTokens.propertyName = expression.substring(lookupNameBeginPos, lookupNameEndPos);
                nextTokens.leftoverExpression = expression.substring(lookupNameEndPos+2);
            } else {
                nextTokens.propertyName = expression.substring(lookupNameBeginPos);
                nextTokens.leftoverExpression = null;
            }
        } else if (expression.startsWith(".")) {
            int lookupNameBeginPos = ".".length();
            int lookupNameEndPos = lookupNameBeginPos;
            int dotlookupNameEndPos = expression.indexOf(".", lookupNameBeginPos);
            int squareBracketlookupNameEndPos = expression.indexOf("[", lookupNameBeginPos);
            if (dotlookupNameEndPos > lookupNameBeginPos && squareBracketlookupNameEndPos > lookupNameBeginPos) {
                lookupNameEndPos = Math.min(dotlookupNameEndPos, squareBracketlookupNameEndPos);
            } else if (dotlookupNameEndPos > lookupNameBeginPos) {
                lookupNameEndPos = dotlookupNameEndPos;
            } else if (squareBracketlookupNameEndPos > lookupNameBeginPos) {
                lookupNameEndPos = squareBracketlookupNameEndPos;
            } else {
                lookupNameEndPos = -1;
            }
            if (lookupNameEndPos > lookupNameBeginPos) {
                nextTokens.propertyName = expression.substring(lookupNameBeginPos, lookupNameEndPos);
                nextTokens.leftoverExpression = expression.substring(lookupNameEndPos);
            } else {
                nextTokens.propertyName = expression.substring(lookupNameBeginPos);
                nextTokens.leftoverExpression = null;
            }
        } else {
            int lookupNameBeginPos = 0;
            int lookupNameEndPos = expression.indexOf(".", lookupNameBeginPos);
            if (lookupNameEndPos > lookupNameBeginPos) {
                nextTokens.propertyName = expression.substring(lookupNameBeginPos, lookupNameEndPos);
                nextTokens.leftoverExpression = expression.substring(lookupNameEndPos);
            } else {
                nextTokens.propertyName = expression.substring(lookupNameBeginPos);
                nextTokens.leftoverExpression = null;
            }
        }
        return nextTokens;
    }


    public Object getProperty(Object object, String expression) {
        if (expression == null) {
            return object;
        }
        if (expression.trim().equals("")) {
            return object;
        }
        NextTokens nextTokens = getNextTokens(expression);
        List<Class<?>> lookupClasses = new ArrayList<>();
        lookupClasses.add(object.getClass());
        lookupClasses.add(object.getClass().getSuperclass());
        lookupClasses.addAll(Arrays.asList(object.getClass().getInterfaces()));
        for (Class<?> lookupClass : lookupClasses) {
            HardcodedPropertyAccessor propertyAccessor = propertyAccessors.get(lookupClass.getName());
            if (propertyAccessor != null) {
                Object result = propertyAccessor.getProperty(object, nextTokens.propertyName, nextTokens.leftoverExpression);
                if (!HardcodedPropertyAccessor.PROPERTY_NOT_FOUND_MARKER.equals(result)) {
                    return result;
                }
            }
        }
        logger.warn("Couldn't find any property access for class {} and expression {}", object.getClass().getName(), expression);
        return HardcodedPropertyAccessor.PROPERTY_NOT_FOUND_MARKER;
    }
}
