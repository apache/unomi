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

import org.apache.unomi.api.Profile;
import org.apache.unomi.graphql.types.input.CDPProfileIDInput;

public class GetProfileCommand extends BaseCommand<Profile> {

    private final CDPProfileIDInput profileIDInput;
    private final Boolean createIfMissing;

    private GetProfileCommand(final Builder builder) {
        super(builder);
        this.profileIDInput = builder.profileIDInput;
        this.createIfMissing = builder.createIfMissing;
    }

    public Profile execute() {
        Profile profile = serviceManager.getProfileService().load(profileIDInput.getId());

        if (profile != null) {
            return profile;
        }

        if (createIfMissing != null && createIfMissing) {
            profile = new Profile();
            profile.setItemId(profileIDInput.getId());
            profile.setItemType("profile");

            profile = serviceManager.getProfileService().save(profile);
        }

        return profile;
    }

    public static Builder create(CDPProfileIDInput profileIDInput, Boolean createIfMissing) {
        return new Builder(profileIDInput, createIfMissing);
    }

    public static class Builder extends BaseCommand.Builder<Builder> {

        final CDPProfileIDInput profileIDInput;
        final Boolean createIfMissing;

        Builder(CDPProfileIDInput profileIDInput, Boolean createIfMissing) {
            this.profileIDInput = profileIDInput;
            this.createIfMissing = createIfMissing;
        }

        private void validate() {
            if (profileIDInput == null) {
                throw new IllegalArgumentException("The \"profileID\" variable can not be null");
            }
        }

        public GetProfileCommand build() {
            validate();

            return new GetProfileCommand(this);
        }
    }

}
