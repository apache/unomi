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

package org.apache.unomi.didvc.services.impl;

import org.apache.unomi.didvc.api.items.DidSchema;
import org.apache.unomi.didvc.api.services.CredentialSchemaService;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Agent binding schema (FR-ID6, phase 7): {@code hkt_agent_binding_v1}
 * binds an agent's public key hash to an HKT-verified principal. The
 * gateway admits agents whose binding credential verifies — and rejects
 * them at the next VP-verified call once the credential fails (revoked,
 * expired or untrusted). Claims are the binding surface only: a key
 * hash, the binding level and the policy scope — never principal PII.
 */
@Component(service = AgentBindingSchemaBootstrap.class, immediate = true)
public class AgentBindingSchemaBootstrap {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgentBindingSchemaBootstrap.class);

    @Reference
    private CredentialSchemaService schemaService;

    public void setSchemaService(CredentialSchemaService schemaService) {
        this.schemaService = schemaService;
    }

    @Activate
    public void activate() {
        if (schemaService.getSchema("hkt-agent-binding-v1") != null) {
            return;
        }
        DidSchema schema = new DidSchema("hkt-agent-binding-v1");
        schema.setName("Agent binding credential");
        schema.setVct("hkt_agent_binding_v1");
        schema.setDescription("Binds an agent public key hash to an HKT-verified principal "
                + "(GB/Z 185-shaped bridge role): the credential a gateway checks on every "
                + "agent call. No principal PII — a key hash, binding level and policy scope.");
        schema.setAllowedClaims(new HashSet<>(Arrays.asList(
                "agentPubKeyHash", "principalBindingLevel", "policyScope")));
        schema.setRequiredClaims(new HashSet<>(Arrays.asList(
                "agentPubKeyHash", "principalBindingLevel")));
        Map<String, String> claimTypes = new LinkedHashMap<>();
        claimTypes.put("agentPubKeyHash", "string");
        claimTypes.put("principalBindingLevel", "string");
        claimTypes.put("policyScope", "string");
        schema.setClaimTypes(claimTypes);
        schema.setScope("didvc");
        schemaService.saveSchema(schema);
        LOGGER.info("Bootstrapped agent binding schema hkt-agent-binding-v1 (vct={})", schema.getVct());
    }
}
