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
     * @api.status 200 array org.apache.unomi.api.Metadata Rule metadata currently loaded in memory (may be empty).
     * @api.example [{"id":"set-premium-on-view","name":"Set Premium on View","scope":"mysite","enabled":true}]
     */
    @GET
    @Path("/")
    public Set<Metadata> getRuleMetadatas() {
        return rulesService.getRuleMetadatas();
    }

    /**
     * Persists the specified rule to the context server.
     * Body is a full {@link Rule}: {@code metadata}, a {@code condition} (JSON field {@code type} + {@code parameterValues}),
     * and an {@code actions} list (each with {@code type} + {@code parameterValues}).
     *
     * @param rule the rule to be persisted
     * @api.status 204 empty Rule created or updated.
     * @api.status 400 empty Invalid or unreadable rule body (condition/actions schema).
     * @api.example {"itemId":"set-premium-on-view","itemType":"rule","metadata":{"id":"set-premium-on-view","name":"Set Premium on View","scope":"mysite","enabled":true,"description":"Mark profile premium when a view event arrives"},"condition":{"type":"eventTypeCondition","parameterValues":{"eventTypeId":"view"}},"actions":[{"type":"setPropertyAction","parameterValues":{"setPropertyName":"properties.isPremium","setPropertyValueBoolean":true,"storeInSession":false}}],"priority":0,"raiseEventOnlyOnceForProfile":false,"raiseEventOnlyOnceForSession":false,"raiseEventOnlyOnce":false}
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
     * @api.status 200 empty Map of rule id to {@link RuleStatistics}.
     * @api.example {"set-premium-on-view":{"itemId":"set-premium-on-view","itemType":"rulestats","executionCount":42,"localExecutionCount":3,"conditionsTime":120,"actionsTime":95}}
     */
    @GET
    @Path("/statistics")
    public Map<String,RuleStatistics> getAllRuleStatistics() {
        return rulesService.getAllRuleStatistics();
    }

    /**
     * Resets execution statistics for all rules to zero.
     *
     * @api.status 204 empty All rule statistics reset.
     * @api.example {}
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
     * @api.status 200 org.apache.unomi.api.PartialList Metadata page (list items are Metadata).
     * @api.status 400 empty Invalid or unreadable query body.
     * @api.example {"list":[{"id":"set-premium-on-view","name":"Set Premium on View","scope":"mysite","enabled":true}],"offset":0,"pageSize":1,"totalSize":1,"totalSizeRelation":"EQUAL"}
     */
    @POST
    @Path("/query")
    public PartialList<Metadata> getRuleMetadatas(Query query) {
        return rulesService.getRuleMetadatas(query);
    }

    /**
     * Returns full rule definitions matching the given query.
     * Each list element includes {@code condition} and {@code actions} (see {@link Rule}).
     *
     * @param query the query specifying which rules to include
     * @return a paged list of matching rules
     * @api.status 200 org.apache.unomi.api.PartialList Rules page (list items are Rule).
     * @api.status 400 empty Invalid or unreadable query body.
     * @api.example {"list":[{"itemId":"set-premium-on-view","itemType":"rule","metadata":{"id":"set-premium-on-view","name":"Set Premium on View","scope":"mysite","enabled":true,"description":"Mark profile premium when a view event arrives"},"condition":{"type":"eventTypeCondition","parameterValues":{"eventTypeId":"view"}},"actions":[{"type":"setPropertyAction","parameterValues":{"setPropertyName":"properties.isPremium","setPropertyValueBoolean":true,"storeInSession":false}}],"priority":0,"raiseEventOnlyOnceForProfile":false,"raiseEventOnlyOnceForSession":false,"raiseEventOnlyOnce":false}],"offset":0,"pageSize":1,"totalSize":1,"totalSizeRelation":"EQUAL"}
     */
    @POST
    @Path("/query/detailed")
    public PartialList<Rule> getRuleDetails(Query query) {
        return rulesService.getRuleDetails(query);
    }

    /**
     * Returns the rule with the given ID.
     * When the rule does not exist the endpoint returns {@code null} (HTTP 200 with empty body).
     * Use the embedded {@code condition}/{@code actions} objects as templates for new rules
     * ({@code type} + {@code parameterValues} on each).
     *
     * @param ruleId the rule identifier
     * @return the rule, or {@code null} when it does not exist
     * @api.status 200 org.apache.unomi.api.rules.Rule Rule found, or empty body when missing.
     * @api.example {"itemId":"set-premium-on-view","itemType":"rule","metadata":{"id":"set-premium-on-view","name":"Set Premium on View","scope":"mysite","enabled":true,"description":"Mark profile premium when a view event arrives"},"condition":{"type":"eventTypeCondition","parameterValues":{"eventTypeId":"view"}},"actions":[{"type":"setPropertyAction","parameterValues":{"setPropertyName":"properties.isPremium","setPropertyValueBoolean":true,"storeInSession":false}}],"priority":0,"raiseEventOnlyOnceForProfile":false,"raiseEventOnlyOnceForSession":false,"raiseEventOnlyOnce":false}
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
     * @api.status 200 org.apache.unomi.api.rules.RuleStatistics Statistics for the rule, or empty body when missing.
     * @api.example {"itemId":"set-premium-on-view","itemType":"rulestats","executionCount":42,"localExecutionCount":3,"conditionsTime":120,"actionsTime":95}
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
     * @api.status 204 empty Rule deleted.
     * @api.example {"itemId":"set-premium-on-view","itemType":"rule","metadata":{"id":"set-premium-on-view","name":"Set Premium on View","scope":"mysite","enabled":true,"description":"Mark profile premium when a view event arrives"},"condition":{"type":"eventTypeCondition","parameterValues":{"eventTypeId":"view"}},"actions":[{"type":"setPropertyAction","parameterValues":{"setPropertyName":"properties.isPremium","setPropertyValueBoolean":true,"storeInSession":false}}],"priority":0,"raiseEventOnlyOnceForProfile":false,"raiseEventOnlyOnceForSession":false,"raiseEventOnlyOnce":false}
     */
    @DELETE
    @Path("/{ruleId}")
    public void removeRule(@PathParam("ruleId") String ruleId) {
        rulesService.removeRule(ruleId);
    }

    /**
     * Deprecated maintenance endpoint kept for backward compatibility.
     * Reloads every in-memory rule from storage by re-saving it.
     *
     * @deprecated As of version 1.1.0-incubating, not needed anymore
     * @api.status 204 empty In-memory rules re-persisted.
     * @api.example {}
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
