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
package org.apache.unomi.graphql.providers;

import org.apache.unomi.graphql.types.output.CDPConsentUpdateEvent;
import org.apache.unomi.graphql.types.output.CDPListsUpdateEvent;
import org.apache.unomi.graphql.types.output.CDPProfileUpdateEvent;
import org.apache.unomi.graphql.types.output.CDPSessionEvent;
import org.apache.unomi.graphql.types.output.UnomiEvent;
import org.osgi.service.component.annotations.Component;

import java.util.HashSet;
import java.util.Set;

@Component
public class CDPDefaultGraphQLProvider implements GraphQLTypesProvider {

    @Override
    public Set<Class<?>> getTypes() {
        final Set<Class<?>> additionalTypes = new HashSet<>();

        additionalTypes.add(CDPSessionEvent.class);
        additionalTypes.add(CDPProfileUpdateEvent.class);
        additionalTypes.add(CDPConsentUpdateEvent.class);
        additionalTypes.add(CDPListsUpdateEvent.class);
        additionalTypes.add(UnomiEvent.class);

        return additionalTypes;
    }

}
