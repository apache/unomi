/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.unomi.api.services;

import org.apache.unomi.api.ClusterNode;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * A service to access information about the context server's cluster.
 *
 */
public interface ClusterService {

    /**
     * Retrieves the list of available nodes for this context server instance.
     *
     * @return a list of {@link ClusterNode}
     */
    List<ClusterNode> getClusterNodes();

    /**
     * Removes all data before the specified date from the context server.
     *
     * @param date the Date before which all data needs to be removed
     */
    @Deprecated
    void purge(final Date date);

    /**
     * Removes all data associated with the provided scope.
     *
     * @param scope the scope for which we want to remove data
     */
    void purge(final String scope);

}
