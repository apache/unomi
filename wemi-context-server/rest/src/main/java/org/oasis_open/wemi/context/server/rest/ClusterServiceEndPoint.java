package org.oasis_open.wemi.context.server.rest;

import org.apache.cxf.rs.security.cors.CrossOriginResourceSharing;
import org.oasis_open.wemi.context.server.api.ClusterNode;
import org.oasis_open.wemi.context.server.api.services.ClusterService;

import javax.jws.WebService;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.List;

/**
 * Created by loom on 16.09.14.
 */
@WebService
@Produces(MediaType.APPLICATION_JSON)
@CrossOriginResourceSharing(
        allowAllOrigins = true,
        allowCredentials = true
)
public class ClusterServiceEndPoint implements ClusterService {

    ClusterService clusterService;

    public ClusterServiceEndPoint() {
        System.out.println("Initializing cluster service endpoint...");
    }

    public void setClusterService(ClusterService clusterService) {
        this.clusterService = clusterService;
    }

    @GET
    @Path("/")
    public List<ClusterNode> getClusterNodes() {
        return clusterService.getClusterNodes();
    }
}
