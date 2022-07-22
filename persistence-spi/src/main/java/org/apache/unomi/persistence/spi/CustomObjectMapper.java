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

package org.apache.unomi.persistence.spi;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.util.ISO8601DateFormat;
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationModule;
import org.apache.unomi.api.*;
import org.apache.unomi.api.actions.ActionType;
import org.apache.unomi.api.campaigns.Campaign;
import org.apache.unomi.api.campaigns.events.CampaignEvent;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.conditions.ConditionType;
import org.apache.unomi.api.goals.Goal;
import org.apache.unomi.api.rules.Rule;
import org.apache.unomi.api.segments.Scoring;
import org.apache.unomi.api.segments.Segment;

import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

/**
 * Custom object mapper to be able to configure Jackson to our needs.
 */
public class CustomObjectMapper extends ObjectMapper {

    private static final long serialVersionUID = 4578277612897061535L;
    
    private Map<String,Class<? extends Item>> builtinItemTypeClasses = new HashMap<>();
    private PropertyTypedObjectDeserializer propertyTypedObjectDeserializer;
    private ItemDeserializer itemDeserializer;

    public CustomObjectMapper() {
        this(null);
    }

    public CustomObjectMapper(Map<Class, StdDeserializer<?>> deserializers) {
        super();
        super.registerModule(new JaxbAnnotationModule());
        configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        setSerializationInclusion(JsonInclude.Include.NON_NULL);
        ISO8601DateFormat dateFormat = new ISO8601DateFormat();
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        setDateFormat(dateFormat);
        SimpleModule deserializerModule =
                new SimpleModule("PropertyTypedObjectDeserializerModule",
                        new Version(1, 0, 0, null, "org.apache.unomi.rest", "deserializer"));

        propertyTypedObjectDeserializer = new PropertyTypedObjectDeserializer(null, null);
        propertyTypedObjectDeserializer.registerMapping("type=.*Condition", Condition.class);
        deserializerModule.addDeserializer(Object.class, propertyTypedObjectDeserializer);

        itemDeserializer = new ItemDeserializer();
        deserializerModule.addDeserializer(Item.class, itemDeserializer);

        if (deserializers != null) {
            deserializers.forEach(deserializerModule::addDeserializer);
        }

        builtinItemTypeClasses = new HashMap<>();
        builtinItemTypeClasses.put(Campaign.ITEM_TYPE, Campaign.class);
        builtinItemTypeClasses.put(CampaignEvent.ITEM_TYPE, CampaignEvent.class);
        builtinItemTypeClasses.put(Event.ITEM_TYPE, Event.class);
        builtinItemTypeClasses.put(Goal.ITEM_TYPE, Goal.class);
        builtinItemTypeClasses.put(Persona.ITEM_TYPE, Persona.class);
        builtinItemTypeClasses.put(Profile.ITEM_TYPE, Profile.class);
        builtinItemTypeClasses.put(Rule.ITEM_TYPE, Rule.class);
        builtinItemTypeClasses.put(Scoring.ITEM_TYPE, Scoring.class);
        builtinItemTypeClasses.put(Segment.ITEM_TYPE, Segment.class);
        builtinItemTypeClasses.put(Session.ITEM_TYPE, Session.class);
        builtinItemTypeClasses.put(ConditionType.ITEM_TYPE, ConditionType.class);
        builtinItemTypeClasses.put(ActionType.ITEM_TYPE, ActionType.class);
        builtinItemTypeClasses.put(Topic.ITEM_TYPE, Topic.class);
        builtinItemTypeClasses.put(ProfileAlias.ITEM_TYPE, ProfileAlias.class);
        for (Map.Entry<String, Class<? extends Item>> entry : builtinItemTypeClasses.entrySet()) {
            propertyTypedObjectDeserializer.registerMapping("itemType=" + entry.getKey(), entry.getValue());
            itemDeserializer.registerMapping(entry.getKey(), entry.getValue());
        }
        propertyTypedObjectDeserializer.registerMapping("itemType=.*", CustomItem.class);

        super.registerModule(deserializerModule);
    }

    public void registerBuiltInItemTypeClass(String itemType, Class clazz) {
        propertyTypedObjectDeserializer.registerMapping("itemType=" + itemType, clazz);
        itemDeserializer.registerMapping(itemType, clazz);
    }

    public void unregisterBuiltInItemTypeClass(String itemType) {
        propertyTypedObjectDeserializer.unregisterMapping("itemType=" + itemType);
        itemDeserializer.unregisterMapping(itemType);
    }

    public static ObjectMapper getObjectMapper() {
        return Holder.INSTANCE;
    }

    public static CustomObjectMapper getCustomInstance() { return Holder.INSTANCE; }

    public Class<? extends Item> getBuiltinItemTypeClass(String itemType) {
        return builtinItemTypeClasses.get(itemType);
    }

    private static class Holder {
        static final CustomObjectMapper INSTANCE = new CustomObjectMapper();
    }

}
