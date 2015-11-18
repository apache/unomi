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

package org.apache.unomi.lists.rest;

import org.apache.cxf.rs.security.cors.CrossOriginResourceSharing;
import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.query.Query;
import org.apache.unomi.lists.UserList;
import org.apache.unomi.services.UserListService;

import javax.jws.WebMethod;
import javax.jws.WebService;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;


/**
 * @author Christophe Laprun
 */
@WebService
@Produces(MediaType.APPLICATION_JSON)
@CrossOriginResourceSharing(
        allowAllOrigins = true,
        allowCredentials = true
)
public class UserListServiceEndPoint {
    private UserListService userListService;

    public UserListServiceEndPoint() {
        System.out.println("Initializing user list service endpoint...");
    }

    @WebMethod(exclude = true)
    public void setUserListService(UserListService userListService) {
        this.userListService = userListService;
    }

    @GET
    @Path("/")
    public PartialList<Metadata> getListMetadatas() {
        return userListService.getListMetadatas(0, 50, null);
    }

    @POST
    @Path("/query")
    public PartialList<Metadata> getListMetadatas(Query query) {
        return userListService.getListMetadatas(query);
    }

    @GET
    @Path("/{listId}")
    public UserList load(@PathParam("listId") String listId) {
        return userListService.load(listId);
    }

    @POST
    @Path("/")
    public void save(UserList list) {
        userListService.save(list);
    }

    @DELETE
    @Path("/{listId}")
    public void delete(@PathParam("listId") String listId) {
        userListService.delete(listId);
    }
}
