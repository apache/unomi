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
package org.apache.unomi.graphql.providers.sample;

import graphql.schema.visibility.BlockedFields;
import graphql.schema.visibility.GraphqlFieldVisibility;
import org.apache.unomi.graphql.providers.GraphQLFieldVisibilityProvider;
import org.apache.unomi.graphql.providers.GraphQLProvider;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;

@Component(immediate = true, service = GraphQLProvider.class)
public class CDPVisibilityOnlyProvider
        implements GraphQLFieldVisibilityProvider {

    private boolean isActivated;

    @Activate
    public void activate(final BundleContext context) {
        this.isActivated = true;
    }

    @Deactivate
    public void deactivate() {
        this.isActivated = false;
    }

    @Override
    public GraphqlFieldVisibility getGraphQLFieldVisibility() {
        // Blocks fields based on patterns
        return BlockedFields.newBlock()
                .addPattern("CDP_SegmentInput.view")
                .addPattern(".*\\.remove.*") // regular expressions allowed
                .build();
    }

    @Override
    public int getPriority() {
        return 1;
    }
}
