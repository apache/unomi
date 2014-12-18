package org.oasis_open.contextserver.api.services;

import org.oasis_open.contextserver.api.ClusterNode;

import java.util.Date;
import java.util.List;

/**
 * Created by loom on 13.09.14.
 */
public interface ClusterService {

    List<ClusterNode> getClusterNodes();

    public void purge(final Date date);

}
