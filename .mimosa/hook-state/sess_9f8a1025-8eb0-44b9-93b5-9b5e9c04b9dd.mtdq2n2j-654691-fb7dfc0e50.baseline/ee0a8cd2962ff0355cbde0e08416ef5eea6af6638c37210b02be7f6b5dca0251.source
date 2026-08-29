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

package org.apache.unomi.didvc.services.actions;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.unomi.api.Event;
import org.apache.unomi.api.actions.Action;
import org.apache.unomi.api.actions.ActionExecutor;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.didvc.api.CredentialIssueRequest;
import org.apache.unomi.didvc.api.services.IssuanceService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rule action that issues a verifiable credential from an event: the CDP
 * "behavior-driven issuance" pattern. Parameters: {@code schemaId},
 * {@code kid}, {@code verifierCategory}, {@code validityDays},
 * {@code claimsJson} (a JSON object of claim values) and
 * {@code selectiveClaims} (claim names that stay selectively disclosable).
 */
@Component(service = ActionExecutor.class, property = "actionExecutorId=issueCredential", immediate = true)
public class IssueCredentialAction implements ActionExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(IssueCredentialAction.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Reference
    private IssuanceService issuanceService;

    public void setIssuanceService(IssuanceService issuanceService) {
        this.issuanceService = issuanceService;
    }

    @Override
    public int execute(Action action, Event event) {
        Map<String, Object> parameters = action.getParameterValues();
        String schemaId = asString(parameters.get("schemaId"));
        String kid = asString(parameters.get("kid"));
        if (schemaId == null || kid == null) {
            LOGGER.warn("issueCredential action requires schemaId and kid parameters; event {} ignored",
                    event.getEventType());
            return EventService.NO_CHANGE;
        }
        CredentialIssueRequest request = new CredentialIssueRequest();
        request.setTenantId(event.getTenantId());
        request.setSchemaId(schemaId);
        request.setSubjectId(event.getProfileId());
        request.setSubjectType("profile");
        request.setKid(kid);
        request.setVerifierCategory(asString(parameters.get("verifierCategory")));
        request.setValidityDays(asInt(parameters.get("validityDays"), 365));

        Map<String, Object> claims = parseClaims(parameters.get("claimsJson"));
        List<String> selectiveClaims = parseSelectiveClaims(parameters.get("selectiveClaims"));
        for (Map.Entry<String, Object> entry : claims.entrySet()) {
            if (selectiveClaims.contains(entry.getKey())) {
                request.getSelectivelyDisclosedClaims().put(entry.getKey(), entry.getValue());
            } else {
                request.getAlwaysDisclosedClaims().put(entry.getKey(), entry.getValue());
            }
        }
        try {
            issuanceService.issueCredential(request);
        } catch (IllegalArgumentException e) {
            LOGGER.warn("issueCredential action rejected for profile {}: {}", event.getProfileId(), e.getMessage());
        }
        return EventService.NO_CHANGE;
    }

    private Map<String, Object> parseClaims(Object claimsJson) {
        if (!(claimsJson instanceof String) || ((String) claimsJson).isEmpty()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue((String) claimsJson, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            throw new IllegalArgumentException("claimsJson parameter is not a JSON object", e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> parseSelectiveClaims(Object selectiveClaims) {
        List<String> result = new ArrayList<>();
        if (selectiveClaims instanceof List) {
            for (Object value : (List<Object>) selectiveClaims) {
                result.add(String.valueOf(value));
            }
        } else if (selectiveClaims instanceof String && !((String) selectiveClaims).isEmpty()) {
            result.add((String) selectiveClaims);
        }
        return result;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private int asInt(Object value, int defaultValue) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String && !((String) value).isEmpty()) {
            return Integer.parseInt((String) value);
        }
        return defaultValue;
    }
}
