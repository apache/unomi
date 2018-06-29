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

import org.apache.cxf.rs.security.cors.CrossOriginResourceSharing;
import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.query.Query;
import org.apache.unomi.api.rules.Rule;
import org.apache.unomi.api.rules.RuleStatistics;
import org.apache.unomi.api.services.RulesService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jws.WebMethod;
import javax.jws.WebService;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.Map;
import java.util.Set;

/**
 * A JAX-RS endpoint to manage {@link Rule}s.
 */
@WebService
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@CrossOriginResourceSharing(
        allowAllOrigins = true,
        allowCredentials = true
)
public class RulesServiceEndPoint {

    private static final Logger logger = LoggerFactory.getLogger(RulesServiceEndPoint.class.getName());

    private RulesService rulesService;

    public RulesServiceEndPoint() {
        logger.info("Initializing rule service endpoint...");
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
     * Retrieves the rule statistics for all known rules.
     *
     * @return a map that contains the rule key as a key and as the value a @RuleStatistics object.
     */
    @GET
    @Path("/statistics")
    public Map<String,RuleStatistics> getAllRuleStatistics() {
        return rulesService.getAllRuleStatistics();
    }

    /**
     * Deletes all the rule statistics, which basically resets them to 0.
     */
    @DELETE
    @Path("/statistics")
    public void resetAllRuleStatistics() {
        rulesService.resetAllRuleStatistics();
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
     * Retrieves rule details for rules matching the specified query.
     *
     * @param query the query specifying which rules to retrieve
     * @return a {@link PartialList} of rule details for the rules matching the specified query
     */
    @POST
    @Path("/query/detailed")
    public PartialList<Rule> getRuleDetails(Query query) {
        return rulesService.getRuleDetails(query);
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
     * Retrieves the statistics for the rule with the specified identifier
     *
     * @param ruleId the identifier of the rule we want to retrieve
     * @return the statistics for the specified rule or {@code null} if no such rule exists.
     */
    @GET
    @Path("/{ruleId}/statistics")
    public RuleStatistics getRuleStatistics(@PathParam("ruleId") String ruleId) {
        return rulesService.getRuleStatistics(ruleId);
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
     * @deprecated As of version 1.1.0-incubating, not needed anymore
     */
    @Deprecated
    @GET
    @Path("/resetQueries")
    public void resetQueries() {
        for (Metadata metadata : rulesService.getRuleMetadatas()) {
            Rule r = rulesService.getRule(metadata.getId());
            rulesService.setRule(r);
        }
    }

}
