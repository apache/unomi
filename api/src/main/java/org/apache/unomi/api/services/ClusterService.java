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

import java.util.Date;
import java.util.List;

/**
 * Access point for cluster topology and node health.
 * Returns {@link ClusterNode} records and coordinates cluster-wide
 * operations such as viewing which nodes store data.
 */
public interface ClusterService {

    /**
     * Returns cluster nodes known to this instance.
     *
     * @return cluster nodes in the current topology
     */
    List<ClusterNode> getClusterNodes();

    /**
     * Deletes all persisted data older than the given cutoff date.
     *
     * @param date cutoff; data before this date is removed
     */
    @Deprecated
    void purge(final Date date);

    /**
     * Deletes all persisted data belonging to the given scope.
     *
     * @param scope scope whose data should be removed
     */
    void purge(final String scope);

}
