package org.oasis_open.contextserver.api.services;

import org.oasis_open.contextserver.api.ClusterNode;

import java.util.Date;
import java.util.List;

public interface ClusterService {

    List<ClusterNode> getClusterNodes();

    public void purge(final Date date);

}
