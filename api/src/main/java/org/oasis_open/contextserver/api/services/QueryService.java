package org.oasis_open.contextserver.api.services;

import org.oasis_open.contextserver.api.conditions.Condition;
import org.oasis_open.contextserver.api.query.AggregateQuery;

import java.util.Map;

public interface QueryService {

    Map<String, Long> getAggregate(String type, String property);

    Map<String, Long> getAggregate(String type, String property, AggregateQuery query);

    long getQueryCount(String type, Condition condition);
}
