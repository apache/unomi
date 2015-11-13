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

package org.oasis_open.contextserver.rest;

import org.apache.cxf.rs.security.cors.CrossOriginResourceSharing;
import org.oasis_open.contextserver.api.Metadata;
import org.oasis_open.contextserver.api.PartialList;
import org.oasis_open.contextserver.api.query.Query;
import org.oasis_open.contextserver.api.rules.Rule;
import org.oasis_open.contextserver.api.services.RulesService;

import javax.jws.WebMethod;
import javax.jws.WebService;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.Set;

/**
 * A JAX-RS endpoint to manage {@link Rule}s.
 */
@WebService
@Produces(MediaType.APPLICATION_JSON)
@CrossOriginResourceSharing(
        allowAllOrigins = true,
        allowCredentials = true
)
public class RulesServiceEndPoint {

    private RulesService rulesService;

    public RulesServiceEndPoint() {
        System.out.println("Initializing rule service endpoint...");
    }

    @WebMethod(exclude=true)
    public void setRulesService(RulesService rulesService) {
        this.rulesService = rulesService;
    }

    /**
     * Retrieves the metadata for all known rules.
     *
     * @return the Set of known metadata
     */
    @GET
    @Path("/")
    public Set<Metadata> getRuleMetadatas() {
        return rulesService.getRuleMetadatas();
    }

    /**
     * Persists the specified rule to the context server.
     *
     * @param rule the rule to be persisted
     */
    @POST
    @Path("/")
    public void setRule(Rule rule) {
        rulesService.setRule(rule);
    }

    /**
     * Retrieves rule metadatas for rules matching the specified {@link Query}.
     *
     * @param query the query the rules which metadata we want to retrieve must match
     * @return a {@link PartialList} of rules metadata for the rules matching the specified query
     */
    @POST
    @Path("/query")
    public PartialList<Metadata> getRuleMetadatas(Query query) {
        return rulesService.getRuleMetadatas(query);
    }

    /**
     * Retrieves the rule identified by the specified identifier.
     *
     * @param ruleId the identifier of the rule we want to retrieve
     * @return the rule identified by the specified identifier or {@code null} if no such rule exists.
     */
    @GET
    @Path("/{ruleId}")
    public Rule getRule( @PathParam("ruleId") String ruleId) {
        return rulesService.getRule(ruleId);
    }

    /**
     * Deletes the rule identified by the specified identifier.
     *
     * @param ruleId the identifier of the rule we want to delete
     */
    @DELETE
    @Path("/{ruleId}")
    public void removeRule(@PathParam("ruleId") String ruleId) {
        rulesService.removeRule(ruleId);
    }

    /**
     * TODO: remove
     *
     * @deprecated not needed anymore
     */
    @GET
    @Path("/resetQueries")
    public void resetQueries() {
        for (Metadata metadata : rulesService.getRuleMetadatas()) {
            Rule r = rulesService.getRule(metadata.getId());
            rulesService.setRule(r);
        }
    }

}
