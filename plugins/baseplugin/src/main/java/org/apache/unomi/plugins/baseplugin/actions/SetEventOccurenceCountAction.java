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

package org.apache.unomi.plugins.baseplugin.actions;

import org.apache.unomi.api.Event;
import org.apache.unomi.api.actions.Action;
import org.apache.unomi.api.actions.ActionExecutor;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.services.DefinitionsService;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.apache.unomi.persistence.spi.PropertyHelper;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.xml.bind.DatatypeConverter;

public class SetEventOccurenceCountAction implements ActionExecutor {
    private DefinitionsService definitionsService;

    private PersistenceService persistenceService;

    public void setDefinitionsService(DefinitionsService definitionsService) {
        this.definitionsService = definitionsService;
    }

    public void setPersistenceService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    @Override
    public int execute(Action action, Event event) {
        final Condition pastEventCondition = (Condition) action.getParameterValues().get("pastEventCondition");

        Condition andCondition = new Condition(definitionsService.getConditionType("booleanCondition"));
        andCondition.setParameter("operator", "and");
        ArrayList<Condition> conditions = new ArrayList<Condition>();

        Condition eventCondition = (Condition) pastEventCondition.getParameter("eventCondition");
        definitionsService.resolveConditionType(eventCondition);
        conditions.add(eventCondition);

        Condition c = new Condition(definitionsService.getConditionType("eventPropertyCondition"));
        c.setParameter("propertyName", "profileId");
        c.setParameter("comparisonOperator", "equals");
        c.setParameter("propertyValue", event.getProfileId());
        conditions.add(c);

        // may be current event is already persisted and indexed, in that case we filter it from the count to increment it manually at the end
        Condition eventIdFilter = new Condition(definitionsService.getConditionType("eventPropertyCondition"));
        eventIdFilter.setParameter("propertyName", "itemId");
        eventIdFilter.setParameter("comparisonOperator", "notEquals");
        eventIdFilter.setParameter("propertyValue", event.getItemId());
        conditions.add(eventIdFilter);

        Integer numberOfDays = (Integer) pastEventCondition.getParameter("numberOfDays");
        String fromDate = (String) pastEventCondition.getParameter("fromDate");
        String toDate = (String) pastEventCondition.getParameter("toDate");

        if (numberOfDays != null) {
            Condition numberOfDaysCondition = new Condition(definitionsService.getConditionType("eventPropertyCondition"));
            numberOfDaysCondition.setParameter("propertyName", "timeStamp");
            numberOfDaysCondition.setParameter("comparisonOperator", "greaterThan");
            numberOfDaysCondition.setParameter("propertyValueDateExpr", "now-" + numberOfDays + "d");
            conditions.add(numberOfDaysCondition);
        }
        if (fromDate != null)  {
            Condition startDateCondition = new Condition();
            startDateCondition.setConditionType(definitionsService.getConditionType("eventPropertyCondition"));
            startDateCondition.setParameter("propertyName", "timeStamp");
            startDateCondition.setParameter("comparisonOperator", "greaterThanOrEqualTo");
            startDateCondition.setParameter("propertyValueDate", fromDate);
            conditions.add(startDateCondition);
        }
        if (toDate != null)  {
            Condition endDateCondition = new Condition();
            endDateCondition.setConditionType(definitionsService.getConditionType("eventPropertyCondition"));
            endDateCondition.setParameter("propertyName", "timeStamp");
            endDateCondition.setParameter("comparisonOperator", "lessThanOrEqualTo");
            endDateCondition.setParameter("propertyValueDate", toDate);
            conditions.add(endDateCondition);
        }

        andCondition.setParameter("subConditions", conditions);

        long count = persistenceService.queryCount(andCondition, Event.ITEM_TYPE);

        LocalDateTime fromDateTime = null;
        if (fromDate != null) {
            Calendar fromDateCalendar = DatatypeConverter.parseDateTime(fromDate);
            fromDateTime = LocalDateTime.ofInstant(fromDateCalendar.toInstant(), ZoneId.of("UTC"));
        }
        LocalDateTime toDateTime = null;
        if (toDate != null) {
            Calendar toDateCalendar = DatatypeConverter.parseDateTime(toDate);
            toDateTime = LocalDateTime.ofInstant(toDateCalendar.toInstant(), ZoneId.of("UTC"));
        }

        LocalDateTime eventTime = LocalDateTime.ofInstant(event.getTimeStamp().toInstant(),ZoneId.of("UTC"));

        if (inTimeRange(eventTime, numberOfDays, fromDateTime, toDateTime)) {
            count++;
        }

        String generatedPropertyKey = (String) pastEventCondition.getParameter("generatedPropertyKey");
        if (PropertyHelper.setProperty(event.getProfile(), "systemProperties.pastEvents." + generatedPropertyKey, count, "alwaysSet")) {
            return EventService.PROFILE_UPDATED;
        }

        return EventService.NO_CHANGE;
    }

    private boolean inTimeRange(LocalDateTime eventTime, Integer numberOfDays, LocalDateTime fromDate, LocalDateTime toDate) {
        boolean inTimeRange = true;

        if (numberOfDays != null) {
            LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));
            if (eventTime.isAfter(now)) {
                inTimeRange = false;
            }
            long daysDiff = Duration.between(eventTime, now).toDays();
            if (daysDiff > numberOfDays) {
                inTimeRange = false;
            }
        }
        if (fromDate != null && fromDate.isAfter(eventTime)) {
            inTimeRange = false;
        }
        if (toDate != null && toDate.isBefore(eventTime)) {
            inTimeRange = false;
        }

        return inTimeRange;
    }
}
