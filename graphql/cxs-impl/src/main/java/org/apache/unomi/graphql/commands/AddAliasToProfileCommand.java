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

import org.apache.unomi.api.ProfileAlias;
import org.apache.unomi.api.services.ProfileService;
import org.apache.unomi.graphql.types.input.CDPProfileAliasInput;
import org.apache.unomi.graphql.types.output.CDPProfileAlias;
import org.apache.unomi.persistence.spi.PersistenceService;

public class AddAliasToProfileCommand extends BaseCommand<CDPProfileAlias> {

    private final CDPProfileAliasInput profileAliasInput;

    public AddAliasToProfileCommand(Builder builder) {
        super(builder);
        this.profileAliasInput = builder.profileAliasInput;
    }

    @Override
    public CDPProfileAlias execute() {
        ProfileService profileService = serviceManager.getService(ProfileService.class);

        profileService.addAliasToProfile(
                profileAliasInput.getProfileID().getId(),
                profileAliasInput.getAlias(),
                profileAliasInput.getProfileID().getClient().getId());

        PersistenceService persistenceService = serviceManager.getService(PersistenceService.class);
        persistenceService.refreshIndex(ProfileAlias.class, null);

        return new CDPProfileAlias(persistenceService.load(profileAliasInput.getAlias(), ProfileAlias.class));
    }

    public static Builder create() {
        return new Builder();
    }

    public static class Builder extends BaseCommand.Builder<Builder> {

        private CDPProfileAliasInput profileAliasInput;

        public Builder profileAliasInput(CDPProfileAliasInput profileAliasInput) {
            this.profileAliasInput = profileAliasInput;
            return this;
        }

        public AddAliasToProfileCommand build() {
            return new AddAliasToProfileCommand(this);
        }
    }
}
