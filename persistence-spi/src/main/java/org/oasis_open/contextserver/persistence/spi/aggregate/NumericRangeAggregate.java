package org.oasis_open.contextserver.persistence.spi.aggregate;

import org.oasis_open.contextserver.api.query.NumericRange;

import java.util.List;

/**
 * Created by kevan on 13/02/15.
 */
public class NumericRangeAggregate extends BaseAggregate{
    public NumericRangeAggregate(String field, List<NumericRange> ranges) {
        super(field);
        this.ranges = ranges;
    }

    private List<NumericRange> ranges;

    public List<NumericRange> getRanges() {
        return ranges;
    }

    public void setRanges(List<NumericRange> ranges) {
        this.ranges = ranges;
    }
}
