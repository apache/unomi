package org.oasis_open.contextserver.impl.conditions.initializers;

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

import org.oasis_open.contextserver.api.EventProperty;
import org.oasis_open.contextserver.api.conditions.initializers.ChoiceListInitializer;
import org.oasis_open.contextserver.api.conditions.initializers.ChoiceListValue;
import org.oasis_open.contextserver.api.conditions.initializers.I18nSupport;
import org.oasis_open.contextserver.api.services.EventService;

import java.util.ArrayList;
import java.util.List;

/**
 * Initializer for the set of available event properties.
 */
public class EventPropertyChoiceListInitializer implements ChoiceListInitializer {

    EventService eventService;

    public List<ChoiceListValue> getValues(Object context) {
        List<EventProperty> eventProperties = eventService.getEventProperties();
        List<ChoiceListValue> choiceListValues = new ArrayList<>(eventProperties.size());
        for (EventProperty eventProperty : eventProperties) {
            choiceListValues.add(new PropertyTypeChoiceListValue(eventProperty.getId(), eventProperty.getId(), eventProperty.getValueType()));
        }
        return choiceListValues;
    }

    public void setEventService(EventService eventService) {
        this.eventService = eventService;
    }
}
