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
package org.apache.unomi.plugins.baseplugin.conditions;

import org.apache.unomi.api.*;
import org.apache.unomi.api.campaigns.Campaign;
import org.apache.unomi.api.goals.Goal;
import org.apache.unomi.api.rules.Rule;
import org.apache.unomi.plugins.baseplugin.conditions.accessors.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * This class contains the registry of all the hardcoded property accessors.
 * For the moment this list of accessors is hardcoded, but in a future update it could be made dynamic.
 */
public class HardcodedPropertyAccessorRegistry {

    private static final Logger logger = LoggerFactory.getLogger(HardcodedPropertyAccessorRegistry.class.getName());

    protected Map<Class<?>, HardcodedPropertyAccessor> propertyAccessors = new HashMap<>();
    protected Map<Class<?>, List<Class<?>>> cachedClassAncestors = new HashMap<>();

    public HardcodedPropertyAccessorRegistry() {
        propertyAccessors.put(Item.class, new ItemAccessor(this));
        propertyAccessors.put(MetadataItem.class, new MetadataItemAccessor(this));
        propertyAccessors.put(Metadata.class, new MetadataAccessor(this));
        propertyAccessors.put(TimestampedItem.class, new TimestampedItemAccessor(this));
        propertyAccessors.put(Event.class, new EventAccessor(this));
        propertyAccessors.put(Profile.class, new ProfileAccessor(this));
        propertyAccessors.put(Consent.class, new ConsentAccessor(this));
        propertyAccessors.put(Session.class, new SessionAccessor(this));
        propertyAccessors.put(Rule.class, new RuleAccessor(this));
        propertyAccessors.put(Goal.class, new GoalAccessor(this));
        propertyAccessors.put(CustomItem.class, new CustomItemAccessor(this));
        propertyAccessors.put(Campaign.class, new CampaignAccessor(this));
        propertyAccessors.put(Map.class, new MapAccessor(this));
    }

    public static class NextTokens {
        public String propertyName;
        public String leftoverExpression;
    }

    protected NextTokens getNextTokens(String expression) {
        if (expression.startsWith("[\"")) {
            int lookupNameBeginPos = "[\"".length();
            int lookupNameEndPos = expression.indexOf("\"]", lookupNameBeginPos);
            return buildNextTokens(expression, lookupNameBeginPos, lookupNameEndPos, lookupNameEndPos+2);
        } else if (expression.startsWith(".")) {
            int lookupNameBeginPos = ".".length();
            int lookupNameEndPos = findNextStartDelimiterPos(expression, lookupNameBeginPos);
            return buildNextTokens(expression, lookupNameBeginPos, lookupNameEndPos, lookupNameEndPos);
        } else {
            int lookupNameBeginPos = 0;
            int lookupNameEndPos = findNextStartDelimiterPos(expression, lookupNameBeginPos);
            return buildNextTokens(expression, lookupNameBeginPos, lookupNameEndPos, lookupNameEndPos);
        }
    }

    private NextTokens buildNextTokens(String expression, int lookupNameBeginPos, int lookupNameEndPos, int leftoverStartPos) {
        NextTokens nextTokens = new NextTokens();
        if (lookupNameEndPos >= lookupNameBeginPos) {
            nextTokens.propertyName = expression.substring(lookupNameBeginPos, lookupNameEndPos);
            nextTokens.leftoverExpression = expression.substring(leftoverStartPos);
            if ("".equals(nextTokens.leftoverExpression)) {
                nextTokens.leftoverExpression = null;
            }
        } else {
            nextTokens.propertyName = expression.substring(lookupNameBeginPos);
            nextTokens.leftoverExpression = null;
        }
        return nextTokens;
    }

    private int findNextStartDelimiterPos(String expression, int lookupNameBeginPos) {
        int lookupNameEndPos;
        int dotlookupNameEndPos = expression.indexOf(".", lookupNameBeginPos);
        int squareBracketlookupNameEndPos = expression.indexOf("[\"", lookupNameBeginPos);
        if (dotlookupNameEndPos >= lookupNameBeginPos && squareBracketlookupNameEndPos >= lookupNameBeginPos) {
            lookupNameEndPos = Math.min(dotlookupNameEndPos, squareBracketlookupNameEndPos);
        } else if (dotlookupNameEndPos >= lookupNameBeginPos) {
            lookupNameEndPos = dotlookupNameEndPos;
        } else if (squareBracketlookupNameEndPos >= lookupNameBeginPos) {
            lookupNameEndPos = squareBracketlookupNameEndPos;
        } else {
            lookupNameEndPos = -1;
        }
        return lookupNameEndPos;
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
        List<Class<?>> objectClassAncestors = cachedClassAncestors.get(object.getClass());
        if (objectClassAncestors == null) {
            objectClassAncestors = collectAncestors(object.getClass(), propertyAccessors.keySet());
            cachedClassAncestors.put(object.getClass(), objectClassAncestors);
        }
        if (objectClassAncestors != null) {
            lookupClasses.addAll(objectClassAncestors);
        }
        for (Class<?> lookupClass : lookupClasses) {
            HardcodedPropertyAccessor propertyAccessor = propertyAccessors.get(lookupClass);
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

    public List<Class<?>> collectAncestors(Class<?> targetClass, Set<Class<?>> availableAccessors) {
        Set<Class<?>> parentClasses = new LinkedHashSet<>();
        if (targetClass.getSuperclass() != null) {
            parentClasses.add(targetClass.getSuperclass());
        }
        if (targetClass.getInterfaces().length > 0) {
            parentClasses.addAll(Arrays.asList(targetClass.getInterfaces()));
        }
        Set<Class<?>> ancestors = new LinkedHashSet<>();
        for (Class<?> parentClass : parentClasses) {
            ancestors.addAll(collectAncestors(parentClass, availableAccessors));
        }
        Set<Class<?>> result = new LinkedHashSet<>();
        result.addAll(parentClasses);
        result.addAll(ancestors);
        return result.stream().filter(availableAccessors::contains).collect(Collectors.toList());
    }
}
