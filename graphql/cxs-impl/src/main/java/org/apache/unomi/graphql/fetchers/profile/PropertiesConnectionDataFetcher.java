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
import org.apache.unomi.api.PropertyType;
import org.apache.unomi.graphql.fetchers.BaseConnectionDataFetcher;
import org.apache.unomi.graphql.fetchers.ConnectionParams;
import org.apache.unomi.graphql.propertytypes.CDPPropertyType;
import org.apache.unomi.graphql.services.ServiceManager;
import org.apache.unomi.graphql.types.output.CDPPageInfo;
import org.apache.unomi.graphql.types.output.CDPPropertyConnection;
import org.apache.unomi.graphql.types.output.CDPPropertyEdge;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class PropertiesConnectionDataFetcher extends BaseConnectionDataFetcher<CDPPropertyConnection> {

    public PropertiesConnectionDataFetcher() {
        super("profile");
    }

    @Override
    public CDPPropertyConnection get(DataFetchingEnvironment environment) throws Exception {
        final ServiceManager serviceManager = environment.getContext();
        final ConnectionParams params = parseConnectionParams(environment);
        final Collection<PropertyType> properties = serviceManager.getProfileService().getTargetPropertyTypes("profiles");

        return createPropertiesConnection(properties, params);
    }

    protected CDPPropertyConnection createPropertiesConnection(Collection<PropertyType> properties, ConnectionParams params) {
        if (properties == null || properties.size() == 0) {
            return new CDPPropertyConnection();
        }

        final CDPPageInfo cdpPageInfo = new CDPPageInfo(params.getFirst() > 0, params.getLast() < properties.size());
        final List<CDPPropertyEdge> edges = properties.stream()
                .map(entry -> new CDPPropertyEdge(new CDPPropertyType(entry), entry.getItemId()))
                .collect(Collectors.toList());
        return new CDPPropertyConnection(edges, cdpPageInfo);
    }
}
