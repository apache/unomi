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
package org.apache.unomi.graphql.types.resolvers;

import graphql.TypeResolutionEnvironment;
import graphql.schema.GraphQLObjectType;
import graphql.schema.TypeResolver;
import org.apache.unomi.graphql.types.output.CDPConsentUpdateEvent;
import org.apache.unomi.graphql.types.output.CDPProfileUpdateEvent;
import org.apache.unomi.graphql.types.output.CDPSessionEvent;

public class CDPEventInterfaceResolver implements TypeResolver {

    @Override
    public GraphQLObjectType getType(TypeResolutionEnvironment env) {
        final Object obj = env.getObject();

        if (obj instanceof CDPConsentUpdateEvent) {
            return env.getSchema().getObjectType(CDPConsentUpdateEvent.TYPE_NAME);
        } else if (obj instanceof CDPProfileUpdateEvent) {
            return env.getSchema().getObjectType(CDPProfileUpdateEvent.TYPE_NAME);
        } else if (obj instanceof CDPSessionEvent) {
            return env.getSchema().getObjectType(CDPSessionEvent.TYPE_NAME);
        } else {
            return null;
        }
    }

}
