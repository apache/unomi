package org.oasis_open.wemi.context.server.persistence.spi;

public class Aggregate {
    public enum Type {
        TERMS,DATE
    }

    private Type type;
    private String field;

    public Aggregate(Type type, String field) {
        this.type = type;
        this.field = field;
    }

    public Type getType() {
        return type;
    }

    public String getField() {
        return field;
    }
}
