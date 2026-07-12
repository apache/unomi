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

package org.apache.unomi.rest.endpoints;

import org.apache.cxf.rs.security.cors.CrossOriginResourceSharing;
import org.apache.unomi.api.Item;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.query.AggregateQuery;
import org.apache.unomi.api.services.QueryService;
import org.apache.unomi.rest.service.impl.LocalizationHelper;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Map;

/**
 * JAX-RS endpoint for aggregate counts, metrics, and conditional item counts.
 */
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@CrossOriginResourceSharing(
        allowAllOrigins = true,
        allowCredentials = true
)
@Path("/query")
@Component(service=QueryServiceEndPoint.class,property = "osgi.jaxrs.resource=true")
public class QueryServiceEndPoint {
    private static final Logger LOGGER = LoggerFactory.getLogger(QueryServiceEndPoint.class.getName());

    @Reference
    private QueryService queryService;

    @Reference
    private LocalizationHelper localizationHelper;

    /**
     * Sets the query service.
     *
     * @param queryService the query service
     */
    public void setQueryService(QueryService queryService) {
        this.queryService = queryService;
    }

    /**
     * Sets the localization helper.
     *
     * @param localizationHelper the localization helper
     */
    public void setLocalizationHelper(LocalizationHelper localizationHelper) {
        this.localizationHelper = localizationHelper;
    }

    /**
     * Returns item counts grouped by distinct values of the given property.
     *
     * @param type the item type name from the class {@code ITEM_TYPE} field
     * @param property the property whose distinct values form aggregation buckets
     * @return property value to item count mappings
     * @see Item Item for a discussion of {@code ITEM_TYPE}
     */
    @GET
    @Path("/{type}/{property}")
    public Map<String, Long> getAggregate(@PathParam("type") String type, @PathParam("property") String property) {
        return queryService.getAggregate(type, property);
    }

    /**
     * Returns property-value counts for an item type, optionally using an aggregate query.
     * <p>
     * When {@code optimizedQuery} is {@code true}, the global document count is omitted for faster execution.
     *
     * @param type the item type name from the class {@code ITEM_TYPE} field
     * @param property the property whose distinct values form aggregation buckets
     * @param optimizedQuery whether to use the optimized aggregate path
     * @param aggregateQuery optional aggregate query constraints
     * @return property value to item count mappings
     * @see Item Item for a discussion of {@code ITEM_TYPE}
     */
    @POST
    @Path("/{type}/{property}")
    public Map<String, Long> getAggregate(@PathParam("type") String type, @PathParam("property") String property,
            @QueryParam("optimizedQuery") boolean optimizedQuery, AggregateQuery aggregateQuery) {
        if (optimizedQuery) {
            return queryService.getAggregateWithOptimizedQuery(type, property, aggregateQuery);
        } else {
            return queryService.getAggregate(type, property, aggregateQuery);
        }
    }

    /**
     * Returns numeric metrics for a field on items that match the given condition.
     *
     * @param condition the condition matching items must satisfy
     * @param metricsType slash-separated metric names ({@code sum}, {@code avg}, {@code min}, {@code max})
     * @param property the numeric field to aggregate
     * @param type the item type name from the class {@code ITEM_TYPE} field
     * @return metric name to computed value mappings
     * @see Item Item for a discussion of {@code ITEM_TYPE}
     */
    @POST
    @Path("/{type}/{property}/{metricTypes:((sum|avg|min|max)/?)*}")
    public Map<String, Double> getMetric(@PathParam("type") String type, @PathParam("property") String property, @PathParam("metricTypes") String metricsType, Condition condition) {
        return queryService.getMetric(type, property, metricsType, condition);
    }

    /**
     * Returns how many items of the given type match the condition.
     *
     * @param condition the condition matching items must satisfy
     * @param validate when {@code true} or omitted, invalid draft conditions return HTTP 400; when {@code false}, returns 0 with HTTP 200
     * @param type the item type name from the class {@code ITEM_TYPE} field
     * @param response the HTTP response used to set status on validation failure
     * @return the matching item count, or {@code 0} when validation fails
     * @see Item Item for a discussion of {@code ITEM_TYPE}
     */
    @POST
    @Path("/{type}/count")
    public long getQueryCount(@PathParam("type") String type, @QueryParam("validate") Boolean validate, Condition condition,  @Context final HttpServletResponse response) {
        long count = 0;
        try {
            count = queryService.getQueryCount(type, condition);
        } catch (IllegalArgumentException e) {
            if(validate == null || validate) {
                LOGGER.error("{}", e.getMessage(), e);
                response.setStatus(Response.Status.BAD_REQUEST.getStatusCode());
            }
        }
        return count;
    }

}
