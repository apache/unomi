package org.oasis_open.wemi.context.server.rest;

import org.oasis_open.wemi.context.server.api.conditions.Condition;
import org.oasis_open.wemi.context.server.api.conditions.ConditionTag;
import org.oasis_open.wemi.context.server.api.conditions.ConditionType;
import org.oasis_open.wemi.context.server.api.conditions.Parameter;
import org.oasis_open.wemi.context.server.api.consequences.ConsequenceType;
import org.oasis_open.wemi.context.server.api.services.DefinitionsService;
import org.oasis_open.wemi.context.server.api.services.SegmentService;

import javax.jws.WebMethod;
import javax.jws.WebService;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.Set;

@WebService
@Produces(MediaType.APPLICATION_JSON)
public class DefinitionsServiceEndPoint implements DefinitionsService {

    DefinitionsService definitionsService;

    @WebMethod(exclude=true)
    public void setDefinitionsService(DefinitionsService definitionsService) {
        this.definitionsService = definitionsService;
    }

    @GET
    @Path("/definitions/conditions/tags")
    public Set<ConditionTag> getConditionTags() {
        return definitionsService.getConditionTags();
    }

    @GET
    @Path("/definitions/conditions/tags/{tagId}")
    public Set<ConditionType> getConditions(@PathParam("tagId") ConditionTag conditionTag) {
        return definitionsService.getConditions(conditionTag);
    }

    @GET
    @Path("/definitions/conditions/{conditionId}")
    public ConditionType getConditionType(@PathParam("conditionId") String conditionId) {
        return definitionsService.getConditionType(conditionId);
    }

    @GET
    @Path("/definitions/conditions/{consequenceId}")
    public ConsequenceType getConsequenceType(@PathParam("conditionId") String consequenceId) {
        return definitionsService.getConsequenceType(consequenceId);
    }



}
