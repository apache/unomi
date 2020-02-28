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
import org.apache.unomi.graphql.fetchers.BaseDataFetcher;
import org.apache.unomi.graphql.types.output.CDPList;
import org.apache.unomi.graphql.types.output.CDPProfile;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class ProfileListsDataFetcher extends BaseDataFetcher<List<CDPList>> {

    @Override
    public List<CDPList> get(DataFetchingEnvironment environment) throws Exception {
        final CDPProfile cdpProfile = environment.getSource();
        final List<String> listIds = (List<String>) cdpProfile.getProfile().getSystemProperties().get("lists");
        if (listIds == null) {
            return Collections.emptyList();
        }

        return listIds.stream().map(CDPList::new).collect(Collectors.toList());
    }
}
