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

package org.apache.unomi.graphql.fetchers.profile;

import graphql.schema.DataFetchingEnvironment;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.services.ProfileService;
import org.apache.unomi.graphql.fetchers.BaseDataFetcher;
import org.apache.unomi.graphql.services.ServiceManager;
import org.apache.unomi.graphql.types.input.CDPProfileIDInput;
import org.apache.unomi.graphql.types.output.CDPProfile;

public class ProfileDataFetcher extends BaseDataFetcher<CDPProfile> {

    private final CDPProfileIDInput profileIDInput;
    private final Boolean createIfMissing;

    public ProfileDataFetcher(CDPProfileIDInput profileIDInput, Boolean createIfMissing) {
        this.profileIDInput = profileIDInput;
        this.createIfMissing = createIfMissing;
    }

    @Override
    public CDPProfile get(DataFetchingEnvironment environment) throws Exception {
        final ServiceManager serviceManager = environment.getContext();
        final ProfileService profileService = serviceManager.getService(ProfileService.class);

        Profile profile = profileService.load(profileIDInput.getId());

        if (profile != null) {
            return new CDPProfile(profile);
        }

        if (createIfMissing != null && createIfMissing) {
            profile = new Profile();
            profile.setItemId(profileIDInput.getId());
            profile.setItemType("profile");

            profile = profileService.save(profile);

            return new CDPProfile(profile);
        }

        return null;
    }
}
