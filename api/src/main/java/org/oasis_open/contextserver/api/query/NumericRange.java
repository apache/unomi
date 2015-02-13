package org.oasis_open.contextserver.api.query;

/**
 * Created by kevan on 13/02/15.
 */
public class NumericRange {
    public NumericRange() {
    }

    private String key;
    private double from;
    private double to;

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public double getFrom() {
        return from;
    }

    public void setFrom(double from) {
        this.from = from;
    }

    public double getTo() {
        return to;
    }

    public void setTo(double to) {
        this.to = to;
    }
}
