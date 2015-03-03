package org.oasis_open.contextserver.api.query;

import org.oasis_open.contextserver.api.conditions.Condition;

public class AggregateQuery {
    private Aggregate aggregate;
    private Condition condition;

    public AggregateQuery() {
    }

    public AggregateQuery(Aggregate aggregate) {
        this.aggregate = aggregate;
    }

    public AggregateQuery(Condition condition) {
        this.condition = condition;
    }

    public AggregateQuery(Aggregate aggregate, Condition condition) {
        this.aggregate = aggregate;
        this.condition = condition;
    }

    public Aggregate getAggregate() {
        return aggregate;
    }

    public void setAggregate(Aggregate aggregate) {
        this.aggregate = aggregate;
    }

    public Condition getCondition() {
        return condition;
    }

    public void setCondition(Condition condition) {
        this.condition = condition;
    }
}
