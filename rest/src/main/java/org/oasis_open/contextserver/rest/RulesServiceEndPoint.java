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

    @POST
    @Path("/")
    public void setRule(Rule rule) {
        rulesService.setRule(rule);
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
