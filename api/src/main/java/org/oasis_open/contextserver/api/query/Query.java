package org.oasis_open.contextserver.api.query;

import org.oasis_open.contextserver.api.conditions.Condition;

/**
 * Created by kevan on 14/05/15.
 */
public class Query {
    private int offset;
    private int limit;
    private String sortby;
    private Condition condition;

    public Query() {
    }

    public int getOffset() {
        return offset;
    }

    public void setOffset(int offset) {
        this.offset = offset;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    public String getSortby() {
        return sortby;
    }

    public void setSortby(String sortby) {
        this.sortby = sortby;
    }

    public Condition getCondition() {
        return condition;
    }

    public void setCondition(Condition condition) {
        this.condition = condition;
    }
}
