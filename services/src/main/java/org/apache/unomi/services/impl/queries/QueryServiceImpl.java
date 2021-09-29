/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.unomi.services.impl.queries;

import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.query.AggregateQuery;
import org.apache.unomi.api.services.DefinitionsService;
import org.apache.unomi.api.services.QueryService;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.apache.unomi.persistence.spi.aggregate.*;
import org.apache.unomi.api.utils.ParserHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class QueryServiceImpl implements QueryService {
    private static final Logger logger = LoggerFactory.getLogger(QueryServiceImpl.class.getName());

    private PersistenceService persistenceService;

    private DefinitionsService definitionsService;

    public void setPersistenceService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    public void setDefinitionsService(DefinitionsService definitionsService) {
        this.definitionsService = definitionsService;
    }

    public void postConstruct() {
        logger.info("Query service initialized.");
    }

    public void preDestroy() {
        logger.info("Query service shutdown.");
    }

    @Override
    public Map<String, Long> getAggregate(String itemType, String property) {
        return persistenceService.aggregateWithOptimizedQuery(null, new TermsAggregate(property), itemType);
    }

    /**
     * @deprecated As of version 1.3.0-incubating, use {@link #getAggregateWithOptimizedQuery(String, String, AggregateQuery)} instead
     */
    @Deprecated
    @Override
    public Map<String, Long> getAggregate(String itemType, String property, AggregateQuery query) {
        return getAggregate(itemType, property, query, false);
    }

    @Override
    public Map<String, Long> getAggregateWithOptimizedQuery(String itemType, String property, AggregateQuery query) {
        return getAggregate(itemType, property, query, true);
    }

    @Override
    public Map<String, Double> getMetric(String type, String property, String slashConcatenatedMetrics, Condition condition) {
        if (condition.getConditionType() == null) {
            ParserHelper.resolveConditionType(definitionsService, condition, "metric " + type + " on property " + property);
        }
        return persistenceService.getSingleValuesMetrics(condition, slashConcatenatedMetrics.split("/"), property, type);
    }

    @Override
    public long getQueryCount(String itemType, Condition condition) {
        if (condition.getConditionType() == null) {
            ParserHelper.resolveConditionType(definitionsService, condition, "query count on " +itemType);
        }
        return persistenceService.queryCount(condition, itemType);
    }

    private Map<String, Long> getAggregate(String itemType, String property, AggregateQuery query, boolean optimizedQuery) {
        if (query != null) {
            // resolve condition
            ParserHelper.resolveConditionType(definitionsService, query.getCondition(), "aggregate on property " + property + " for type " + itemType);

            // resolve aggregate
            BaseAggregate baseAggregate = null;
            if (query.getAggregate() != null) {
                String aggregateType = query.getAggregate().getType();
                if (aggregateType != null) {
                    // try to guess the aggregate type
                    if (aggregateType.equals("date")) {
                        String interval = (String) query.getAggregate().getParameters().get("interval");
                        String format = (String) query.getAggregate().getParameters().get("format");
                        baseAggregate = new DateAggregate(property, interval, format);
                    } else if (aggregateType.equals("dateRange") && query.getAggregate().getDateRanges() != null && query.getAggregate().getDateRanges().size() > 0) {
                        String format = (String) query.getAggregate().getParameters().get("format");
                        baseAggregate = new DateRangeAggregate(query.getAggregate().getProperty(), format, query.getAggregate().getDateRanges());
                    } else if (aggregateType.equals("numericRange") && query.getAggregate().getNumericRanges() != null && query.getAggregate().getNumericRanges().size() > 0) {
                        baseAggregate = new NumericRangeAggregate(query.getAggregate().getProperty(), query.getAggregate().getNumericRanges());
                    } else if (aggregateType.equals("ipRange") && query.getAggregate().ipRanges() != null && query.getAggregate().ipRanges().size() > 0) {
                        baseAggregate = new IpRangeAggregate(query.getAggregate().getProperty(), query.getAggregate().ipRanges());
                    }
                }
            }

            if (baseAggregate == null) {
                baseAggregate = new TermsAggregate(property);
            }

            // fall back on terms aggregate
            if (optimizedQuery) {
                return persistenceService.aggregateWithOptimizedQuery(query.getCondition(), baseAggregate, itemType);
            } else {
                return persistenceService.aggregateQuery(query.getCondition(), baseAggregate, itemType);
            }
        }

        return getAggregate(itemType, property);
    }

}