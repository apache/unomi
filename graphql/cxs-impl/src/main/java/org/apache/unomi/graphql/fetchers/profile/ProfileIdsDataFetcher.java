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

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import org.apache.unomi.api.Profile;
import org.apache.unomi.graphql.types.output.CDPProfile;
import org.apache.unomi.graphql.types.output.CDPProfileID;

import java.util.Collections;
import java.util.List;

public class ProfileIdsDataFetcher implements DataFetcher<List<CDPProfileID>> {

    @Override
    public List<CDPProfileID> get(DataFetchingEnvironment environment) throws Exception {
        CDPProfile cdpProfile = environment.getSource();
        return Collections.singletonList(createProfileId(cdpProfile.getProfile()));
    }

    private CDPProfileID createProfileId(Profile profile) {
        return new CDPProfileID(profile.getItemId());
    }
}
