package org.oasis_open.contextserver.api;

/*
 * #%L
 * context-server-api
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

import org.oasis_open.contextserver.api.conditions.Condition;

import java.util.List;

public class ContextRequest {

    private Item source;
    private boolean requireSegments;
    private List<String> requiredProfileProperties;
    private List<String> requiredSessionProperties;
    private List<Event> events;
    private List<FilteredContent> filters;

    public Item getSource() {
        return source;
    }

    public void setSource(Item source) {
        this.source = source;
    }

    public boolean isRequireSegments() {
        return requireSegments;
    }

    public void setRequireSegments(boolean requireSegments) {
        this.requireSegments = requireSegments;
    }

    public List<String> getRequiredProfileProperties() {
        return requiredProfileProperties;
    }

    public void setRequiredProfileProperties(List<String> requiredProfileProperties) {
        this.requiredProfileProperties = requiredProfileProperties;
    }

    public List<String> getRequiredSessionProperties() {
        return requiredSessionProperties;
    }

    public void setRequiredSessionProperties(List<String> requiredSessionProperties) {
        this.requiredSessionProperties = requiredSessionProperties;
    }

    public List<FilteredContent> getFilters() {
        return filters;
    }

    public void setFilters(List<FilteredContent> filters) {
        this.filters = filters;
    }

    public List<Event> getEvents() {
        return events;
    }

    public void setEvents(List<Event> events) {
        this.events = events;
    }

    public static class FilteredContent {
        private String filterid;
        private List<Filter> filters;

        public String getFilterid() {
            return filterid;
        }

        public void setFilterid(String filterid) {
            this.filterid = filterid;
        }

        public List<Filter> getFilters() {
            return filters;
        }

        public void setFilters(List<Filter> filters) {
            this.filters = filters;
        }
    }

    public static class Filter {
        private List<Target> appliesOn;
        private Condition condition;

        public List<Target> getAppliesOn() {
            return appliesOn;
        }

        public void setAppliesOn(List<Target> appliesOn) {
            this.appliesOn = appliesOn;
        }

        public Condition getCondition() {
            return condition;
        }

        public void setCondition(Condition condition) {
            this.condition = condition;
        }
    }

    public static class Target {
        private String target;
        private List<String> values;

        public String getTarget() {
            return target;
        }

        public void setTarget(String target) {
            this.target = target;
        }

        public List<String> getValues() {
            return values;
        }

        public void setValues(List<String> values) {
            this.values = values;
        }
    }
}
