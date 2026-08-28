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
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;


/**
 * JAX-RS endpoint for static {@link UserList} CRUD and metadata queries.
 */
@Produces(MediaType.APPLICATION_JSON)
@CrossOriginResourceSharing(
        allowAllOrigins = true,
        allowCredentials = true
)
@Path("/lists")
@Component(service=UserListServiceEndPoint.class,property = "osgi.jaxrs.resource=true")
public class UserListServiceEndPoint {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserListServiceEndPoint.class.getName());

    @Reference
    private UserListService userListService;

    public UserListServiceEndPoint() {
        LOGGER.info("Initializing user list service endpoint...");
    }

    public void setUserListService(UserListService userListService) {
        this.userListService = userListService;
    }

    /**
     * Returns the first page of user list metadata (offset 0, size 50).
     *
     * @return user list metadata page
     * @api.status 200 org.apache.unomi.api.PartialList Metadata page (list items are Metadata; may be empty).
     * @api.example {"list":[{"id":"newsletter-subscribers","name":"Newsletter subscribers","scope":"mysite","enabled":true}],"offset":0,"pageSize":1,"totalSize":1,"totalSizeRelation":"EQUAL"}
     */
    @GET
    @Path("/")
    public PartialList<Metadata> getListMetadatas() {
        return userListService.getListMetadatas(0, 50, null);
    }

    /**
     * Returns user list metadata matching the given query.
     *
     * @param query the query lists must match
     * @return a paged list of matching metadata
     * @api.status 200 org.apache.unomi.api.PartialList Metadata page (list items are Metadata).
     * @api.status 400 empty Invalid or unreadable query body.
     * @api.example {"list":[{"id":"newsletter-subscribers","name":"Newsletter subscribers","scope":"mysite","enabled":true}],"offset":0,"pageSize":1,"totalSize":1,"totalSizeRelation":"EQUAL"}
     */
    @POST
    @Path("/query")
    public PartialList<Metadata> getListMetadatas(Query query) {
        return userListService.getListMetadatas(query);
    }

    /**
     * Returns the user list with the given ID.
     * When the list does not exist the endpoint returns {@code null} (HTTP 200 with empty body).
     *
     * @param listId the list identifier
     * @return the user list, or {@code null} when missing
     * @api.status 200 org.apache.unomi.lists.UserList List found, or empty body when missing.
     * @api.example {"itemId":"newsletter-subscribers","itemType":"userList","metadata":{"id":"newsletter-subscribers","name":"Newsletter subscribers","scope":"mysite","enabled":true}}
     */
    @GET
    @Path("/{listId}")
    public UserList load(@PathParam("listId") String listId) {
        return userListService.load(listId);
    }

    /**
     * Persists the specified user list.
     *
     * @param list the list to save
     * @api.status 204 empty List created or updated.
     * @api.example {"itemId":"newsletter-subscribers","itemType":"userList","metadata":{"id":"newsletter-subscribers","name":"Newsletter subscribers","scope":"mysite","enabled":true}}
     */
    @POST
    @Path("/")
    public void save(UserList list) {
        userListService.save(list);
    }

    /**
     * Deletes the user list with the given ID.
     *
     * @param listId the list identifier
     * @api.status 204 empty List deleted.
     * @api.example {"itemId":"newsletter-subscribers","itemType":"userList","metadata":{"id":"newsletter-subscribers","name":"Newsletter subscribers","scope":"mysite","enabled":true}}
     */
    @DELETE
    @Path("/{listId}")
    public void delete(@PathParam("listId") String listId) {
        userListService.delete(listId);
    }
}
