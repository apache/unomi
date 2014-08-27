package org.oasis_open.wemi.context.server.api;

import java.util.Set;

/**
 * Created by loom on 27.08.14.
 */
public class UserProperty {
    String id;
    String type;
    String groupId;
    String choiceListInitializerFilter;
    String defaultValue;
    String selectorId;
    Set<String> automaticMappingsFrom;

    public UserProperty() {
    }

    public UserProperty(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getChoiceListInitializerFilter() {
        return choiceListInitializerFilter;
    }

    public void setChoiceListInitializerFilter(String choiceListInitializerFilter) {
        this.choiceListInitializerFilter = choiceListInitializerFilter;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public String getSelectorId() {
        return selectorId;
    }

    public void setSelectorId(String selectorId) {
        this.selectorId = selectorId;
    }

    public Set<String> getAutomaticMappingsFrom() {
        return automaticMappingsFrom;
    }

    public void setAutomaticMappingsFrom(Set<String> automaticMappingsFrom) {
        this.automaticMappingsFrom = automaticMappingsFrom;
    }
}
