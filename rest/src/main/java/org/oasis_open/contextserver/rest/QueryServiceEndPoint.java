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
import org.oasis_open.contextserver.api.conditions.Condition;
import org.oasis_open.contextserver.api.query.AggregateQuery;
import org.oasis_open.contextserver.api.services.QueryService;

import javax.jws.WebMethod;
import javax.jws.WebService;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.Map;

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

    @GET
    @Path("/{type}/{property}")
    public Map<String, Long> getAggregate(@PathParam("type") String type, @PathParam("property") String property) {
        return queryService.getAggregate(type, property);
    }

    @POST
    @Path("/{type}/{property}")
    public Map<String, Long> getAggregate(@PathParam("type") String type, @PathParam("property") String property, AggregateQuery aggregateQuery) {
        return queryService.getAggregate(type, property, aggregateQuery);
    }

    @POST
    @Path("/{type}/{property}/{metricTypes:((sum|avg|min|max)/?)*}")
    public Map<String, Double> getMetric(@PathParam("type") String type, @PathParam("property") String property, @PathParam("metricTypes") String metricsType, Condition condition) {
        return queryService.getMetric(type, property, metricsType, condition);
    }

    @POST
    @Path("/{type}/count")
    public long getQueryCount(@PathParam("type") String type, Condition condition) {
        return queryService.getQueryCount(type, condition);
    }

}
