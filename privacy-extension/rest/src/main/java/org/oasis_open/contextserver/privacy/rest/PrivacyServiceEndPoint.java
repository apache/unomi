package org.oasis_open.contextserver.privacy.rest;

import org.apache.cxf.rs.security.cors.CrossOriginResourceSharing;
import org.oasis_open.contextserver.privacy.PrivacyService;
import org.oasis_open.contextserver.privacy.ServerInfo;

import javax.jws.WebMethod;
import javax.jws.WebService;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

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
    public Response deleteProfileData(@PathParam("profileId") String profileId) {
        privacyService.deleteProfileData(profileId);
        return Response.ok().build();
    }

}
