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
package org.apache.unomi.graphql.types.output;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import org.apache.unomi.api.ProfileAlias;

import java.time.OffsetDateTime;
import java.time.ZoneId;

import static org.apache.unomi.graphql.types.output.CDPProfileAlias.TYPE_NAME;

@GraphQLName(TYPE_NAME)
@GraphQLDescription("Alias for Profile.")
public class CDPProfileAlias {
    public static final String TYPE_NAME = "CDP_ProfileAlias";

    private final ProfileAlias profileAlias;

    public CDPProfileAlias(final ProfileAlias profileAlias) {
        this.profileAlias = profileAlias;
    }

    @GraphQLField
    public String alias() {
        return profileAlias != null ? profileAlias.getItemId() : null;
    }

    @GraphQLField
    public CDPProfileID profileID() {
        if (profileAlias != null) {
            CDPProfileID profileID = new CDPProfileID(profileAlias.getProfileID());
            if (profileAlias.getClientID() != null) {
                profileID.setClient(new CDPClient(profileAlias.getClientID(), profileAlias.getClientID()));
            }
            return profileID;
        }
        return null;
    }

    @GraphQLField
    public OffsetDateTime creationTime() {
        return profileAlias != null ? profileAlias.getCreationTime().toInstant().atZone(ZoneId.systemDefault()).toOffsetDateTime() : null;
    }

    @GraphQLField
    public OffsetDateTime modifiedTime() {
        return profileAlias != null ? profileAlias.getModifiedTime().toInstant().atZone(ZoneId.systemDefault()).toOffsetDateTime() : null;
    }
}
