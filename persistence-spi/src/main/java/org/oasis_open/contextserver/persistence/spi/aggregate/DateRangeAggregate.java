package org.oasis_open.contextserver.persistence.spi.aggregate;

import org.oasis_open.contextserver.api.query.GenericRange;

import java.util.List;

/**
 * Created by kevan on 13/02/15.
 */
public class DateRangeAggregate extends BaseAggregate{
    public DateRangeAggregate(String field, List<GenericRange> ranges) {
        super(field);
        this.ranges = ranges;
    }

    private List<GenericRange> ranges;

    public List<GenericRange> getRanges() {
        return ranges;
    }

    public void setRanges(List<GenericRange> ranges) {
        this.ranges = ranges;
    }
}
