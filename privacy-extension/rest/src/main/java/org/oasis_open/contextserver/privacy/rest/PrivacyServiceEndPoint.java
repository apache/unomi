package org.oasis_open.contextserver.privacy.rest;

import org.apache.cxf.rs.security.cors.CrossOriginResourceSharing;
import org.oasis_open.contextserver.api.services.PrivacyService;
import org.oasis_open.contextserver.api.ServerInfo;

import javax.jws.WebMethod;
import javax.jws.WebService;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;
import java.util.List;

/**
 * Created by loom on 10.09.15.
 */
@WebService
@Produces(MediaType.APPLICATION_JSON)
@CrossOriginResourceSharing(
        allowAllOrigins = true,
        allowCredentials = true
)
public class PrivacyServiceEndPoint {

    private PrivacyService privacyService;

    @WebMethod(exclude = true)
    public void setPrivacyService(PrivacyService privacyService) {
        this.privacyService = privacyService;
    }

    @GET
    @Path("/info")
    public ServerInfo getServerInfo() {
        return privacyService.getServerInfo();
    }

    @DELETE
    @Path("/profiles/{profileId}")
    public Response deleteProfileData(@PathParam("profileId") String profileId, @QueryParam("withData") @DefaultValue("false") boolean withData) {
        if (withData) {
            privacyService.deleteProfileData(profileId);
        } else {
            privacyService.deleteProfile(profileId);
        }
        return Response.ok().build();
    }

    @POST
    @Path("/profiles/{profileId}/anonymize")
    public Response anonymizeBrowsingData(@PathParam("profileId") String profileId) {
        String newProfileId = privacyService.anonymizeBrowsingData(profileId);
        if (!profileId.equals(newProfileId)) {
            return Response.ok()
                    .cookie(new NewCookie("context-profile-id", newProfileId, "/", null, null, NewCookie.DEFAULT_MAX_AGE, false))
                    .entity(newProfileId)
                    .build();
        }
        return Response.serverError().build();
    }

    @GET
    @Path("/profiles/{profileId}/anonymous")
    public Boolean isAnonymous(@PathParam("profileId") String profileId) {
        return privacyService.isAnonymous(profileId);
    }

    @POST
    @Path("/profiles/{profileId}/anonymous")
    public Response activateAnonymousSurfing(@PathParam("profileId") String profileId) {
        privacyService.setAnonymous(profileId, true);
        return Response.ok().build();
    }

    @DELETE
    @Path("/profiles/{profileId}/anonymous")
    public Response deactivateAnonymousSurfing(@PathParam("profileId") String profileId) {
        privacyService.setAnonymous(profileId, false);
        return Response.ok().build();
    }

    @GET
    @Path("/profiles/{profileId}/eventFilters")
    public List<String> getEventFilters(@PathParam("profileId") String profileId) {
        return privacyService.getFilteredEventTypes(profileId);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/profiles/{profileId}/eventFilters")
    public Response setEventFilters(@PathParam("profileId") String profileId, List<String> eventFilters) {
        privacyService.setFilteredEventTypes(profileId, eventFilters);
        return Response.ok().build();
    }

}
