package org.oasis_open.wemi.context.server;

import org.oasis_open.wemi.context.server.api.Event;
import org.oasis_open.wemi.context.server.api.conditions.Condition;

import java.util.List;

public class ContextRequest {
    private String pageId;

    private boolean requireSegments;
    private List<String> requiredUserProperties;
    private List<String> requiredSessionProperties;
    private List<Event> events;

    private List<FilteredContent> filters;

    public String getPageId() {
        return pageId;
    }

    public void setPageId(String pageId) {
        this.pageId = pageId;
    }

    public boolean isRequireSegments() {
        return requireSegments;
    }

    public void setRequireSegments(boolean requireSegments) {
        this.requireSegments = requireSegments;
    }

    public List<String> getRequiredUserProperties() {
        return requiredUserProperties;
    }

    public void setRequiredUserProperties(List<String> requiredUserProperties) {
        this.requiredUserProperties = requiredUserProperties;
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

    static class FilteredContent {
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

    static class Filter {
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

    static class Target {
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
