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

package org.apache.unomi.rest.endpoints;

import org.apache.cxf.rs.security.cors.CrossOriginResourceSharing;
import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.lists.UserList;
import org.apache.unomi.api.services.UserListService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.List;

/**
 * JAX-RS endpoint for static {@link UserList} CRUD and membership management.
 * Delegates to {@link UserListService} so marketers can maintain fixed audience
 * lists used by campaigns and exports.
 */
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@CrossOriginResourceSharing(
        allowAllOrigins = true,
        allowCredentials = true
)
@Path("/userList")
@Component(service=UserListServiceEndPoint.class,property = "osgi.jaxrs.resource=true")
public class UserListServiceEndPoint {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserListServiceEndPoint.class.getName());

    @Reference
    private UserListService userListService;

    /**
     * Creates the user list service endpoint.
     */
    public UserListServiceEndPoint() {
        LOGGER.info("Initializing user lists service endpoint...");
    }

    /**
     * Sets the user list service.
     *
     * @param userListService the user list service
     */
    public void setUserListService(UserListService userListService) {
        this.userListService = userListService;
    }

    /**
     * Returns user list metadata with paging and optional sorting.
     *
     * @param offset zero-based index of the first result
     * @param size maximum number of results to return, or {@code -1} for all matches
     * @param sortBy optional comma-separated sort fields with optional {@code :asc} or {@code :desc}
     * @return matching user list metadata
     */
    @GET
    @Path("/")
    public List<Metadata> getUserListsMetadatas(@QueryParam("offset") @DefaultValue("0") int offset, @QueryParam("size") @DefaultValue("50") int size, @QueryParam("sort") String sortBy) {
        return userListService.getUserListMetadatas(offset,size, sortBy).getList();
    }
}
