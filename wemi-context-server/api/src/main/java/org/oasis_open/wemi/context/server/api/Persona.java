package org.oasis_open.wemi.context.server.api;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A persona is a "virtual" user used to represent categories of users, and may also be used to test
 * how a personalized experience would look like using this virtual user.
 */
public class Persona extends User {

    public static final String ITEM_TYPE = "persona";

    private String description;

    private Map<String, Object> requestParameters = new LinkedHashMap<String, Object>();
    private Map<String, Object> requestHeaders = new LinkedHashMap<String, Object>();

    public Persona() {
    }

    public Persona(String personaId) {
        super(personaId);
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Map<String, Object> getRequestParameters() {
        return requestParameters;
    }

    public void setRequestParameters(Map<String, Object> requestParameters) {
        this.requestParameters = requestParameters;
    }

    public Map<String, Object> getRequestHeaders() {
        return requestHeaders;
    }

    public void setRequestHeaders(Map<String, Object> requestHeaders) {
        this.requestHeaders = requestHeaders;
    }
}
