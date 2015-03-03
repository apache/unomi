package org.oasis_open.contextserver.persistence.spi.aggregate;

import org.oasis_open.contextserver.api.query.GenericRange;

import java.util.List;

public class DateRangeAggregate extends BaseAggregate{
    public DateRangeAggregate(String field, List<GenericRange> ranges) {
        super(field);
        this.ranges = ranges;
    }

    public DateRangeAggregate(String field, String format, List<GenericRange> ranges) {
        super(field);
        this.format = format;
        this.ranges = ranges;
    }

    private String format;

    private List<GenericRange> ranges;

    public List<GenericRange> getRanges() {
        return ranges;
    }

    public void setRanges(List<GenericRange> ranges) {
        this.ranges = ranges;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }
}
