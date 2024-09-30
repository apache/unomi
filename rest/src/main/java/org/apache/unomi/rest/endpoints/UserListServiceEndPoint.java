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

/**
 * Created by amidani on 24/03/2017.
 */

import org.apache.cxf.rs.security.cors.CrossOriginResourceSharing;
import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.lists.UserList;
import org.apache.unomi.api.services.UserListService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jws.WebMethod;
import javax.jws.WebService;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.List;

/**
 * A JAX-RS endpoint to manage {@link UserList}s.
 */
@WebService
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

    public UserListServiceEndPoint() {
        LOGGER.info("Initializing user lists service endpoint...");
    }

    @WebMethod(exclude=true)
    public void setUserListService(UserListService userListService) {
        this.userListService = userListService;
    }

    /**
     * Retrieves the 50 first {@link UserList} metadatas.
     *
     * @param offset zero or a positive integer specifying the position of the first element in the total ordered collection of matching elements
     * @param size   a positive integer specifying how many matching elements should be retrieved or {@code -1} if all of them should be retrieved
     * @param sortBy an optional ({@code null} if no sorting is required) String of comma ({@code ,}) separated property names on which ordering should be performed, ordering
     *               elements according to the property order in the
     *               String, considering each in turn and moving on to the next one in case of equality of all preceding ones. Each property name is optionally followed by
     *               a column ({@code :}) and an order specifier: {@code asc} or {@code desc}.
     *
     * @return a List of the 50 first {@link UserList} metadata
     */
    @GET
    @Path("/")
    public List<Metadata> getUserListsMetadatas(@QueryParam("offset") @DefaultValue("0") int offset, @QueryParam("size") @DefaultValue("50") int size, @QueryParam("sort") String sortBy) {
        return userListService.getUserListMetadatas(offset,size, sortBy).getList();
    }
}
