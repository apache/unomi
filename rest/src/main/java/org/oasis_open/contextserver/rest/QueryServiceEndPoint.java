package org.oasis_open.contextserver.rest;

/*
 * #%L
 * context-server-rest
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

import org.apache.cxf.rs.security.cors.CrossOriginResourceSharing;
import org.oasis_open.contextserver.api.Item;
import org.oasis_open.contextserver.api.conditions.Condition;
import org.oasis_open.contextserver.api.query.AggregateQuery;
import org.oasis_open.contextserver.api.services.QueryService;

import javax.jws.WebMethod;
import javax.jws.WebService;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.Map;

/**
 * A JAX-RS endpoint to perform queries against context-server data.
 */
@WebService
@Produces(MediaType.APPLICATION_JSON)
@CrossOriginResourceSharing(
        allowAllOrigins = true,
        allowCredentials = true
)
public class QueryServiceEndPoint {

    private QueryService queryService;

    private LocalizationHelper localizationHelper;

    @WebMethod(exclude = true)
    public void setQueryService(QueryService queryService) {
        this.queryService = queryService;
    }


    @WebMethod(exclude = true)
    public void setLocalizationHelper(LocalizationHelper localizationHelper) {
        this.localizationHelper = localizationHelper;
    }

    /**
     * Retrieves the number of items with the specified type as defined by the Item subclass public field {@code ITEM_TYPE} and aggregated by possible values of the specified
     * property.
     *
     * @param type     the String representation of the item type we want to retrieve the count of, as defined by its class' {@code ITEM_TYPE} field
     * @param property the property we're aggregating on, i.e. for each possible value of this property, we are counting how many items of the specified type have that value
     * @return a Map associating a specific value of the property to the cardinality of items with that value
     * @see Item Item for a discussion of {@code ITEM_TYPE}
     */
    @GET
    @Path("/{type}/{property}")
    public Map<String, Long> getAggregate(@PathParam("type") String type, @PathParam("property") String property) {
        return queryService.getAggregate(type, property);
    }

    /**
     * TODO: rework, this method is confusing since it either behaves like {@link #getAggregate(String, String)} if query is null but completely differently if it isn't
     *
     * Retrieves the number of items with the specified type as defined by the Item subclass public field {@code ITEM_TYPE} and aggregated by possible values of the specified
     * property or, if the specified query is not {@code null}, perform that aggregate query.
     *
     * @param type           the String representation of the item type we want to retrieve the count of, as defined by its class' {@code ITEM_TYPE} field
     * @param property       the property we're aggregating on, i.e. for each possible value of this property, we are counting how many items of the specified type have that value
     * @param aggregateQuery the {@link AggregateQuery} specifying the aggregation that should be perfomed
     * @return a Map associating a specific value of the property to the cardinality of items with that value
     * @see Item Item for a discussion of {@code ITEM_TYPE}
     */
    @POST
    @Path("/{type}/{property}")
    public Map<String, Long> getAggregate(@PathParam("type") String type, @PathParam("property") String property, AggregateQuery aggregateQuery) {
        return queryService.getAggregate(type, property, aggregateQuery);
    }

    /**
     * Retrieves the specified metrics for the specified field of items of the specified type as defined by the Item subclass public field {@code ITEM_TYPE} and matching the
     * specified {@link Condition}.
     *
     * @param condition   the condition the items must satisfy
     * @param metricsType a String specifying which metrics should be computed, separated by a slash ({@code /}) (possible values: {@code sum} for the sum of the
     *                    values, {@code avg} for the average of the values, {@code min} for the minimum value and {@code max} for the maximum value)
     * @param property    the name of the field for which the metrics should be computed
     * @param type        the String representation of the item type we want to retrieve the count of, as defined by its class' {@code ITEM_TYPE} field
     * @return a Map associating computed metric name as key to its associated value
     * @see Item Item for a discussion of {@code ITEM_TYPE}
     */
    @POST
    @Path("/{type}/{property}/{metricTypes:((sum|avg|min|max)/?)*}")
    public Map<String, Double> getMetric(@PathParam("type") String type, @PathParam("property") String property, @PathParam("metricTypes") String metricsType, Condition condition) {
        return queryService.getMetric(type, property, metricsType, condition);
    }

    /**
     * Retrieves the number of items of the specified type as defined by the Item subclass public field {@code ITEM_TYPE} and matching the specified {@link Condition}.
     *
     * @param condition the condition the items must satisfy
     * @param type      the String representation of the item type we want to retrieve the count of, as defined by its class' {@code ITEM_TYPE} field
     * @return the number of items of the specified type
     * @see Item Item for a discussion of {@code ITEM_TYPE}
     */
    @POST
    @Path("/{type}/count")
    public long getQueryCount(@PathParam("type") String type, Condition condition) {
        return queryService.getQueryCount(type, condition);
    }

}
