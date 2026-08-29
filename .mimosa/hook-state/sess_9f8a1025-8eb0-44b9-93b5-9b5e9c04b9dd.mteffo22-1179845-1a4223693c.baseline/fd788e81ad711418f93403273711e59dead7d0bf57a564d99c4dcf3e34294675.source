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
import org.apache.unomi.graphql.types.input.CDPProfileIDInput;
import org.apache.unomi.graphql.types.output.CDPProfileAlias;

public class RemoveAliasFromProfileCommand extends BaseCommand<CDPProfileAlias> {

    private final String alias;

    private final CDPProfileIDInput profileIDInput;

    private RemoveAliasFromProfileCommand(Builder builder) {
        super(builder);
        this.alias = builder.alias;
        this.profileIDInput = builder.profileIDInput;
    }

    @Override
    public CDPProfileAlias execute() {
        ProfileService profileService = serviceManager.getService(ProfileService.class);

        ProfileAlias profileAlias = profileService.removeAliasFromProfile(profileIDInput.getId(), alias, profileIDInput.getClient().getId());

        return profileAlias != null ? new CDPProfileAlias(profileAlias) : null;
    }

    public static Builder create() {
        return new Builder();
    }

    public static class Builder extends BaseCommand.Builder<Builder> {

        private String alias;

        private CDPProfileIDInput profileIDInput;

        public Builder setAlias(String alias) {
            this.alias = alias;
            return this;
        }

        public Builder setProfileIDInput(CDPProfileIDInput profileIDInput) {
            this.profileIDInput = profileIDInput;
            return this;
        }

        public RemoveAliasFromProfileCommand build() {
            return new RemoveAliasFromProfileCommand(this);
        }
    }
}
