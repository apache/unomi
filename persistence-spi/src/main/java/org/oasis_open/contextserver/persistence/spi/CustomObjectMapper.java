package org.oasis_open.contextserver.persistence.spi;

/*
 * #%L
 * context-server-persistence-spi
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

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.util.ISO8601DateFormat;
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationModule;

import org.oasis_open.contextserver.api.*;
import org.oasis_open.contextserver.api.campaigns.Campaign;
import org.oasis_open.contextserver.api.campaigns.events.CampaignEvent;
import org.oasis_open.contextserver.api.conditions.Condition;
import org.oasis_open.contextserver.api.goals.Goal;
import org.oasis_open.contextserver.api.rules.Rule;
import org.oasis_open.contextserver.api.segments.Scoring;
import org.oasis_open.contextserver.api.segments.Segment;

import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

/**
 * Custom object mapper to be able to configure Jackson to our needs.
 */
public class CustomObjectMapper extends ObjectMapper {

    private static final long serialVersionUID = 4578277612897061535L;

    private static class Holder {
        static final CustomObjectMapper INSTANCE = new CustomObjectMapper();
    }

    public CustomObjectMapper() {
        super();
        super.registerModule(new JaxbAnnotationModule());
        configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        ISO8601DateFormat dateFormat = new ISO8601DateFormat();
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        setDateFormat(dateFormat);
        SimpleModule deserializerModule =
              new SimpleModule("PropertyTypedObjectDeserializerModule",
                  new Version(1, 0, 0, null, "org.oasis_open.contextserver.rest", "deserializer"));

        PropertyTypedObjectDeserializer propertyTypedObjectDeserializer = new PropertyTypedObjectDeserializer();
        propertyTypedObjectDeserializer.registerMapping("type=.*Condition", Condition.class);
        deserializerModule.addDeserializer(Object.class, propertyTypedObjectDeserializer);

        ItemDeserializer itemDeserializer = new ItemDeserializer();
        deserializerModule.addDeserializer(Item.class, itemDeserializer);


        Map<String,Class<? extends Item>> classes = new HashMap<>();
        classes.put(Campaign.ITEM_TYPE,Campaign.class);
        classes.put(CampaignEvent.ITEM_TYPE,CampaignEvent.class);
        classes.put(Event.ITEM_TYPE,Event.class);
        classes.put(Goal.ITEM_TYPE,Goal.class);
        classes.put(Persona.ITEM_TYPE,Persona.class);
        classes.put(Rule.ITEM_TYPE,Rule.class);
        classes.put(Scoring.ITEM_TYPE,Scoring.class);
        classes.put(Segment.ITEM_TYPE,Segment.class);
        classes.put(Session.ITEM_TYPE, Session.class);
        for (Map.Entry<String, Class<? extends Item>> entry : classes.entrySet()) {
            propertyTypedObjectDeserializer.registerMapping("itemType="+entry.getKey(), entry.getValue());
            itemDeserializer.registerMapping(entry.getKey(), entry.getValue());
        }
        propertyTypedObjectDeserializer.registerMapping("itemType=.*", CustomItem.class);


       super.registerModule(deserializerModule);
    }

    public static ObjectMapper getObjectMapper() {
        return Holder.INSTANCE;
    }
}