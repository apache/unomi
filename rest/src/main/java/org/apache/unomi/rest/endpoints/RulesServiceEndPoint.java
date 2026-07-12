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
import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.query.Query;
import org.apache.unomi.api.rules.Rule;
import org.apache.unomi.api.rules.RuleStatistics;
import org.apache.unomi.api.services.RulesService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.Map;
import java.util.Set;

/**
 * A JAX-RS endpoint to manage {@link Rule}s.
 */
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@CrossOriginResourceSharing(
        allowAllOrigins = true,
        allowCredentials = true
)
@Path("/rules")
@Component(service=RulesServiceEndPoint.class,property = "osgi.jaxrs.resource=true")
public class RulesServiceEndPoint {

    private static final Logger LOGGER = LoggerFactory.getLogger(RulesServiceEndPoint.class.getName());

    @Reference
    private RulesService rulesService;

    /**
     * Creates the rules service endpoint.
     */
    public RulesServiceEndPoint() {
        LOGGER.info("Initializing rule service endpoint...");
    }

    /**
     * Sets the rules service.
     *
     * @param rulesService the rules service
     */
    public void setRulesService(RulesService rulesService) {
        this.rulesService = rulesService;
    }

    /**
     * Returns metadata for all in-memory rules.
     * <p>
     * Note that this includes only rules currently loaded in memory, not every rule in storage.
     *
     * @return known rule metadata
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
     * Returns execution statistics for all known rules.
     *
     * @return rule ID to statistics mappings
     */
    @GET
    @Path("/statistics")
    public Map<String,RuleStatistics> getAllRuleStatistics() {
        return rulesService.getAllRuleStatistics();
    }

    /**
     * Resets execution statistics for all rules to zero.
     */
    @DELETE
    @Path("/statistics")
    public void resetAllRuleStatistics() {
        rulesService.resetAllRuleStatistics();
    }

    /**
     * Returns rule metadata matching the given query.
     *
     * @param query the query rules must match
     * @return a paged list of matching rule metadata
     */
    @POST
    @Path("/query")
    public PartialList<Metadata> getRuleMetadatas(Query query) {
        return rulesService.getRuleMetadatas(query);
    }

    /**
     * Returns full rule definitions matching the given query.
     *
     * @param query the query specifying which rules to include
     * @return a paged list of matching rules
     */
    @POST
    @Path("/query/detailed")
    public PartialList<Rule> getRuleDetails(Query query) {
        return rulesService.getRuleDetails(query);
    }

    /**
     * Returns the rule with the given ID.
     *
     * @param ruleId the rule identifier
     * @return the rule, or {@code null} when it does not exist
     */
    @GET
    @Path("/{ruleId}")
    public Rule getRule( @PathParam("ruleId") String ruleId) {
        return rulesService.getRule(ruleId);
    }

    /**
     * Returns execution statistics for the rule with the given ID.
     *
     * @param ruleId the rule identifier
     * @return the rule statistics, or {@code null} when the rule does not exist
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
     * Deprecated maintenance endpoint kept for backward compatibility.
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
