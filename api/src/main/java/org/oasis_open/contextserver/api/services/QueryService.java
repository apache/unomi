package org.oasis_open.contextserver.api.services;

import org.oasis_open.contextserver.api.conditions.Condition;

import java.util.Map;

/**
 * Created by loom on 24.04.14.
 */
public interface QueryService {

    Map<String, Long> getAggregate(String type, String property);

    Map<String, Long> getAggregate(String type, String property, Condition filter);
}
