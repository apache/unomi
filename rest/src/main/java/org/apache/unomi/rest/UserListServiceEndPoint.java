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

package org.apache.unomi.rest;

/**
 * Created by amidani on 24/03/2017.
 */

import org.apache.cxf.rs.security.cors.CrossOriginResourceSharing;
import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.lists.UserList;
import org.apache.unomi.api.services.UserListService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jws.WebMethod;
import javax.jws.WebService;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
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
public class UserListServiceEndPoint {

    private static final Logger logger = LoggerFactory.getLogger(UserListServiceEndPoint.class.getName());

    private UserListService userListService;

    public UserListServiceEndPoint() {
        logger.info("Initializing user lists service endpoint...");
    }

    @WebMethod(exclude=true)
    public void setUserListService(UserListService userListService) {
        this.userListService = userListService;
    }

    /**
     * Retrieves the 50 first {@link UserList} metadatas.
     *
     * @return a List of the 50 first {@link UserList} metadata
     */
    @GET
    @Path("/")
    public List<Metadata> getUserListsMetadatas() {
        return userListService.getUserListMetadatas(0, 50, null).getList();
    }
}
