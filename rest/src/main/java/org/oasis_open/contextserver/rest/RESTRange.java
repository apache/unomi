package org.oasis_open.contextserver.rest;

public class RESTRange {
    private String key;
    private Object to;
    private Object from;

    public RESTRange() {
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public Object getTo() {
        return to;
    }

    public void setTo(Object to) {
        this.to = to;
    }

    public Object getFrom() {
        return from;
    }

    public void setFrom(Object from) {
        this.from = from;
    }
}
