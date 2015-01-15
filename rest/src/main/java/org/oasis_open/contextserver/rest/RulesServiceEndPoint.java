package org.oasis_open.contextserver.rest;

import org.apache.cxf.rs.security.cors.CrossOriginResourceSharing;
import org.oasis_open.contextserver.api.Metadata;
import org.oasis_open.contextserver.api.rules.Rule;
import org.oasis_open.contextserver.api.services.RulesService;

import javax.jws.WebMethod;
import javax.jws.WebService;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.Set;

/**
 * Created by loom on 10.08.14.
 */
@WebService
@Produces(MediaType.APPLICATION_JSON)
@CrossOriginResourceSharing(
        allowAllOrigins = true,
        allowCredentials = true
)
public class RulesServiceEndPoint {

    private RulesService rulesService;

    public RulesServiceEndPoint() {
        System.out.println("Initializing rule service endpoint...");
    }

    @WebMethod(exclude=true)
    public void setRulesService(RulesService rulesService) {
        this.rulesService = rulesService;
    }

    @GET
    @Path("/")
    public Set<Metadata> getRuleMetadatas() {
        return rulesService.getRuleMetadatas();
    }

    @GET
    @Path("/{scope}")
    public Set<Metadata> getRuleMetadatas(@PathParam("scope") String scope) {
        return rulesService.getRuleMetadatas(scope);
    }

    @GET
    @Path("/{scope}/{ruleId}")
    public Rule getRule(@PathParam("scope") String scope, @PathParam("ruleId") String ruleId) {
        return rulesService.getRule(scope, ruleId);
    }

    @POST
    @Path("/{scope}/{ruleId}")
    public void setRule(@PathParam("scope") String scope, @PathParam("ruleId") String ruleId, Rule rule) {
        rulesService.setRule(rule);
    }

    @PUT
    @Path("/{scope}/{ruleId}")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public void createRule(@PathParam("scope") String scope, @PathParam("ruleId") String ruleId, @FormParam("ruleName") String name, @FormParam("ruleDescription") String description) {
        rulesService.createRule(scope, ruleId, name, description);

    }

    @DELETE
    @Path("/{scope}/{ruleId}")
    public void removeRule(@PathParam("scope") String scope, @PathParam("ruleId") String ruleId) {
        rulesService.removeRule(scope, ruleId);
    }


    @GET
    @Path("/resetQueries")
    public void resetQueries() {
        for (Metadata metadata : rulesService.getRuleMetadatas()) {
            Rule r = rulesService.getRule(metadata.getScope(), metadata.getId());
            rulesService.setRule(r);
        }
    }

}
