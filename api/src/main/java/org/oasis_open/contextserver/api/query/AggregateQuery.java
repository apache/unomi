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
