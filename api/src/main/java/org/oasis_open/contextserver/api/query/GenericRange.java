package org.oasis_open.contextserver.api.query;

/**
 * Created by kevan on 13/02/15.
 */
public class GenericRange {
    public GenericRange() {
    }

    private String key;
    private Object from;
    private Object to;

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public Object getFrom() {
        return from;
    }

    public void setFrom(Object from) {
        this.from = from;
    }

    public Object getTo() {
        return to;
    }

    public void setTo(Object to) {
        this.to = to;
    }
}
