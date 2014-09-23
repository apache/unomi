package org.oasis_open.wemi.context.server.rest;

import org.apache.cxf.rs.security.cors.CrossOriginResourceSharing;
import org.oasis_open.wemi.context.server.api.conditions.Condition;
import org.oasis_open.wemi.context.server.api.services.QueryService;

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
public class QueryServiceEndPoint implements QueryService {
    private QueryService queryService;

    public void setQueryService(QueryService queryService) {
        this.queryService = queryService;
    }

    @GET
    @Path("/{type}/{property}")
    public Map<String, Long> getAggregate(@PathParam("type") String type, @PathParam("property") String property) {
        return queryService.getAggregate(type,property);
    }

    @POST
    @Path("/{type}/{property}")
    public Map<String, Long> getAggregate(@PathParam("type") String type, @PathParam("property") String property, Condition filter) {
        return queryService.getAggregate(type,property, filter);
    }
}
