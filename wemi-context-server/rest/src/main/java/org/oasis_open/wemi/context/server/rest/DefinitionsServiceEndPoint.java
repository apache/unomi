package org.oasis_open.wemi.context.server.rest;

import org.apache.cxf.rs.security.cors.CrossOriginResourceSharing;
import org.oasis_open.wemi.context.server.api.conditions.Tag;
import org.oasis_open.wemi.context.server.api.conditions.ConditionType;
import org.oasis_open.wemi.context.server.api.consequences.ConsequenceType;
import org.oasis_open.wemi.context.server.api.services.DefinitionsService;

import javax.jws.WebMethod;
import javax.jws.WebService;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.Collection;
import java.util.Set;

@WebService
@Produces(MediaType.APPLICATION_JSON)
@CrossOriginResourceSharing(
        allowAllOrigins = true,
        allowCredentials = true
)
public class DefinitionsServiceEndPoint implements DefinitionsService {

    DefinitionsService definitionsService;

    @WebMethod(exclude=true)
    public void setDefinitionsService(DefinitionsService definitionsService) {
        this.definitionsService = definitionsService;
    }

    @GET
    @Path("/tags")
    public Set<Tag> getAllTags() {
        return definitionsService.getAllTags();
    }

    @GET
    @Path("/rootTags")
    public Set<Tag> getRootTags() {
        return definitionsService.getRootTags();
    }

    @GET
    @Path("/tags/{tagId}")
    public Set<Tag> getChildTags(@PathParam("tagId") Tag tag) {
        return definitionsService.getChildTags(tag);
    }

    @GET
    @Path("/conditions")
    public Collection<ConditionType> getAllConditions() {
        return definitionsService.getAllConditions();
    }

    @GET
    @Path("/conditions/tags/{tagId}")
    public Set<ConditionType> getConditionTypesByTag(@PathParam("tagId") Tag tag) {
        return definitionsService.getConditionTypesByTag(tag);
    }

    @GET
    @Path("/conditions/{conditionId}")
    public ConditionType getConditionType(@PathParam("conditionId") String conditionId) {
        return definitionsService.getConditionType(conditionId);
    }

    @GET
    @Path("/consequences/{consequenceId}")
    public ConsequenceType getConsequenceType(@PathParam("consequenceId") String consequenceId) {
        return definitionsService.getConsequenceType(consequenceId);
    }

}
