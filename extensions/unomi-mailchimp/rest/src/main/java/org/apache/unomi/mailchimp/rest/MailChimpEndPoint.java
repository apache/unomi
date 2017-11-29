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
package org.apache.unomi.mailchimp.rest;

import org.apache.cxf.rs.security.cors.CrossOriginResourceSharing;
import org.apache.unomi.mailchimp.services.MailChimpService;

import javax.jws.WebMethod;
import javax.jws.WebService;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.HashMap;
import java.util.List;


@WebService
@Produces(MediaType.APPLICATION_JSON + ";charset=UTF-8")
@CrossOriginResourceSharing(
    allowAllOrigins = true,
    allowCredentials = true
)
public class MailChimpEndPoint {
    private MailChimpService mailChimpService;

    public MailChimpEndPoint() {
        System.out.println("Initializing MailChimpEndPoint service endpoint...");
    }

    /**
     * This function return the available MailChimp lists ID and Name
     *
     * @return The Lists of MailChimp List
     */
    @GET
    @Path("/")
    public List<HashMap<String, String>> getAllLists() {
        return mailChimpService.getAllLists();
    }

    @WebMethod(exclude = true)
    public void setMailChimpService(MailChimpService mailChimpService) {
        this.mailChimpService = mailChimpService;
    }
}

