package org.oasis_open.contextserver.persistence.spi.aggregate;

public abstract class BaseAggregate {
    private String field;

    public BaseAggregate(String field) {
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
