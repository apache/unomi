package org.oasis_open.contextserver.impl.services;

/*
 * #%L
 * context-server-services
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
import org.oasis_open.contextserver.api.query.AggregateQuery;
import org.oasis_open.contextserver.api.services.DefinitionsService;
import org.oasis_open.contextserver.api.services.QueryService;
import org.oasis_open.contextserver.persistence.spi.PersistenceService;
import org.oasis_open.contextserver.persistence.spi.aggregate.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
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
    }

    public void preDestroy() {
    }

    @Override
    public Map<String, Long> getAggregate(String type, String property) {
        return persistenceService.aggregateQuery(null, new TermsAggregate(property), type);
    }

    @Override
    public Map<String, Long> getAggregate(String type, String property, AggregateQuery query) {
        if(query != null) {
            // resolve condition
            if(query.getCondition() != null){
                ParserHelper.resolveConditionType(definitionsService, query.getCondition());
            }

            // resolve aggregate
            if(query.getAggregate() != null) {
                String aggregateType = query.getAggregate().getType();
                if (aggregateType != null){
                    // try to guess the aggregate type
                    if(aggregateType.equals("date")){
                        String interval = (String) query.getAggregate().getParameters().get("interval");
                        String format = (String) query.getAggregate().getParameters().get("format");
                        return persistenceService.aggregateQuery(query.getCondition(), new DateAggregate(property, interval, format), type);
                    }else if (aggregateType.equals("dateRange") && query.getAggregate().getGenericRanges() != null && query.getAggregate().getGenericRanges().size() > 0) {
                        String format = (String) query.getAggregate().getParameters().get("format");
                        return persistenceService.aggregateQuery(query.getCondition(), new DateRangeAggregate(query.getAggregate().getProperty(), format, query.getAggregate().getGenericRanges()), type);
                    } else if (aggregateType.equals("range") && query.getAggregate().getNumericRanges() != null && query.getAggregate().getNumericRanges().size() > 0) {
                        return persistenceService.aggregateQuery(query.getCondition(), new NumericRangeAggregate(query.getAggregate().getProperty(), query.getAggregate().getNumericRanges()), type);
                    }
                }
            }

            // fall back on terms aggregate
            return persistenceService.aggregateQuery(query.getCondition(), new TermsAggregate(property), type);
        }

        return getAggregate(type, property);
    }

    @Override
    public Map<String, Double> getMetric(String type, String property, String metricType, Condition condition) {
        if (condition.getConditionType() == null) {
            ParserHelper.resolveConditionType(definitionsService, condition);
        }
        return persistenceService.getSingleValuesMetrics(condition,metricType.split("/"),property, type);
    }

    @Override
    public long getQueryCount(String type, Condition condition) {
        try {
            if (condition.getConditionType() == null) {
                ParserHelper.resolveConditionType(definitionsService, condition);
            }
            return persistenceService.queryCount(condition, type);
        } catch (Exception e) {
            logger.warn("Invalid query");
            return 0;
        }
    }
}
