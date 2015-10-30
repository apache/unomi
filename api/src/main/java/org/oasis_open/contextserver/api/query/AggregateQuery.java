package org.oasis_open.contextserver.api.query;

/*
 * #%L
 * context-server-api
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2014 - 2015 Jahia Solutions
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import org.oasis_open.contextserver.api.conditions.Condition;

/**
 * A query by aggregate: results matching the specified {@link Condition} are aggregated using a given {@link Aggregate} specification, creating buckets which cardinality is
 * calculated. The results of the query are returned as Map associating the bucket key to the cardinality of its member.
 */
public class AggregateQuery {
    private Aggregate aggregate;
    private Condition condition;

    /**
     * Instantiates a new Aggregate query.
     */
    public AggregateQuery() {
    }

    /**
     * Instantiates a new Aggregate query with the specified {@link Aggregate}.
     *
     * @param aggregate the aggregate
     */
    public AggregateQuery(Aggregate aggregate) {
        this.aggregate = aggregate;
    }

    /**
     * Instantiates a new Aggregate query with the specified {@link Condition}.
     *
     * @param condition the condition
     */
    public AggregateQuery(Condition condition) {
        this.condition = condition;
    }

    /**
     * Instantiates a new Aggregate query with the specified {@link Aggregate} and {@link Condition}
     *
     * @param aggregate the aggregate
     * @param condition the condition
     */
    public AggregateQuery(Aggregate aggregate, Condition condition) {
        this.aggregate = aggregate;
        this.condition = condition;
    }

    /**
     * Retrieves the aggregate.
     *
     * @return the aggregate
     */
    public Aggregate getAggregate() {
        return aggregate;
    }

    /**
     * Sets the aggregate.
     *
     * @param aggregate the aggregate
     */
    public void setAggregate(Aggregate aggregate) {
        this.aggregate = aggregate;
    }

    /**
     * Retrieves the condition.
     *
     * @return the condition
     */
    public Condition getCondition() {
        return condition;
    }

    /**
     * Sets the condition.
     *
     * @param condition the condition
     */
    public void setCondition(Condition condition) {
        this.condition = condition;
    }
}
