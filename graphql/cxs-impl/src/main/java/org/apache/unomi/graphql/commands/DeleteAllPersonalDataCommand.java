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

import com.google.common.base.Strings;
import org.apache.unomi.api.Profile;
import org.apache.unomi.graphql.services.ServiceManager;

import java.util.Map;
import java.util.Objects;

public class DeleteAllPersonalDataCommand extends BaseCommand<Boolean> {

    private DeleteAllPersonalDataCommand(Builder builder) {
        super(builder);
    }

    @Override
    public Boolean execute() {
        final ServiceManager serviceManager = environment.getContext();

        final Map<String, Object> cdpProfileIdInput = environment.getArgument("profileID");

        final String profileId = (String) cdpProfileIdInput.get("id");

        final Profile profile = serviceManager.getProfileService().load(profileId);

        if (profile == null) {
            return false;
        }

        return serviceManager.getPrivacyService().deleteProfileData(profileId);
    }

    public static Builder create() {
        return new Builder();
    }

    public static class Builder extends BaseCommand.Builder<Builder> {

        public Builder() {
        }

        @Override
        public void validate() {
            super.validate();

            final Map<String, Object> cdpProfileIDInput = environment.getArgument("profileID");

            Objects.requireNonNull(cdpProfileIDInput, "The \"profileID\" variable can not be null");

            final String personaId = (String) cdpProfileIDInput.get("id");

            if (Strings.isNullOrEmpty(personaId)) {
                throw new IllegalArgumentException("The \"id\" variable can not be null or empty");
            }
        }

        public DeleteAllPersonalDataCommand build() {
            validate();

            return new DeleteAllPersonalDataCommand(this);
        }

    }

}
