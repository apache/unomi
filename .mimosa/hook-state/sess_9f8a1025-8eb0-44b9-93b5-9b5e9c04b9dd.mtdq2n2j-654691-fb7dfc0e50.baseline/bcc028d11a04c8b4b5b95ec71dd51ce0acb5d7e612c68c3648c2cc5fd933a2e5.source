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
package org.apache.unomi.graphql.commands;

import org.apache.unomi.api.Consent;
import org.apache.unomi.api.ConsentStatus;
import org.apache.unomi.api.Persona;
import org.apache.unomi.api.services.ProfileService;
import org.apache.unomi.graphql.schema.PropertyNameTranslator;
import org.apache.unomi.graphql.types.input.CDPPersonaConsentInput;
import org.apache.unomi.graphql.types.input.CDPPersonaInput;
import org.apache.unomi.graphql.types.input.CDPProfileIDInput;
import org.apache.unomi.graphql.types.output.CDPPersona;
import org.apache.unomi.graphql.utils.DateUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static org.apache.unomi.graphql.CDPGraphQLConstants.PERSONA_ARGUMENT_NAME;

public class CreateOrUpdatePersonaCommand extends BaseCommand<CDPPersona> {

    private final CDPPersonaInput personaInput;

    private CreateOrUpdatePersonaCommand(final Builder builder) {
        super(builder);
        this.personaInput = builder.personaInput;
    }

    public static Builder create(final CDPPersonaInput personaInput) {
        return new Builder(personaInput);
    }

    @Override
    public CDPPersona execute() {
        final ProfileService profileService = serviceManager.getService(ProfileService.class);

        final Map<String, Object> personaAsMap = environment.getArgument(PERSONA_ARGUMENT_NAME);

        Persona persona = profileService.savePersona(createPersona(personaInput, personaAsMap));

        return new CDPPersona(persona);
    }

    private Persona createPersona(final CDPPersonaInput personaInput, Map<String, Object> personaAsMap) {
        final Persona persona = new Persona(personaInput.getId());

        persona.setScope(personaInput.getCdp_view());
        persona.getProperties().put("cdp_name", personaInput.getCdp_name());

        if (personaInput.getCdp_profileIDs() != null && !personaInput.getCdp_profileIDs().isEmpty()) {
            final String profileIds = personaInput.getCdp_profileIDs().stream().map(CDPProfileIDInput::getId).collect(Collectors.joining(","));
            persona.setMergedWith(profileIds);
        }

        persona.setSegments(personaInput.getCdp_segments());
        if (personaInput.getCdp_consents() != null && !personaInput.getCdp_consents().isEmpty()) {
            personaInput.getCdp_consents().forEach(consentInput -> {
                persona.setConsent(createConsent(personaInput.getCdp_view(), consentInput));
            });
        }

        if (personaInput.getCdp_interests() != null && !personaInput.getCdp_consents().isEmpty()) {
            Map<String, Integer> interestMap = new HashMap<>();
            personaInput.getCdp_interests().forEach(interestInput -> {
                final Integer intScore = interestInput.getScore() != null ? interestInput.getScore().intValue() : null;
                interestMap.put(interestInput.getTopic(), intScore);
            });
            persona.setScores(interestMap);
        }

        personaAsMap.forEach((graphQlName, value) -> persona.setProperty(PropertyNameTranslator.translateFromGraphQLToUnomi(graphQlName), value));

        return persona;
    }

    private Consent createConsent(final String scope, final CDPPersonaConsentInput consentInput) {
        return new Consent(scope,
                consentInput.getType(),
                ConsentStatus.valueOf(consentInput.getStatus()),
                DateUtils.toDate(consentInput.getLastUpdate()),
                DateUtils.toDate(consentInput.getExpiration()));
    }

    public static class Builder extends BaseCommand.Builder<Builder> {

        final CDPPersonaInput personaInput;

        Builder(final CDPPersonaInput personaInput) {
            this.personaInput = personaInput;
        }

        @Override
        public void validate() {
            super.validate();

            if (personaInput == null) {
                throw new IllegalArgumentException("Persona input can not be null.");
            }
        }

        public CreateOrUpdatePersonaCommand build() {
            validate();

            return new CreateOrUpdatePersonaCommand(this);
        }
    }

}
