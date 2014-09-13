package org.oasis_open.wemi.context.server.api.services;

import org.oasis_open.wemi.context.server.api.ClusterNode;

import java.util.List;

/**
 * Created by loom on 13.09.14.
 */
public interface ClusterService {

    public List<ClusterNode> getClusterNodes();

}
