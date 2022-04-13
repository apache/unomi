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
import org.apache.unomi.api.ProfileAlias;
import org.apache.unomi.graphql.services.ServiceManager;
import org.apache.unomi.graphql.types.output.CDPProfileAlias;
import org.apache.unomi.persistence.spi.PersistenceService;

public class ProfileAliasDataFetcher implements DataFetcher<CDPProfileAlias> {

    private final String alias;

    public ProfileAliasDataFetcher(final String alias) {
        this.alias = alias;
    }

    @Override
    public CDPProfileAlias get(final DataFetchingEnvironment environment) throws Exception {
        ServiceManager serviceManager = environment.getContext();
        PersistenceService persistenceService = serviceManager.getService(PersistenceService.class);
        ProfileAlias profileAlias = persistenceService.load(alias, ProfileAlias.class);
        return profileAlias != null ? new CDPProfileAlias(profileAlias) : null;
    }
}
