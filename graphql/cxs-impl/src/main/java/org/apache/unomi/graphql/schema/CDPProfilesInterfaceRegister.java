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
package org.apache.unomi.graphql.schema;

import org.apache.unomi.api.Profile;
import org.apache.unomi.graphql.types.output.CDPProfileInterface;
import org.apache.unomi.graphql.utils.ReflectionUtil;
import org.osgi.service.component.annotations.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component(immediate = true, service = CDPProfilesInterfaceRegister.class)
public class CDPProfilesInterfaceRegister {

    private ConcurrentHashMap<String, Class<? extends CDPProfileInterface>> profiles;

    public CDPProfilesInterfaceRegister() {
        profiles = new ConcurrentHashMap<>();
    }

    public void register(final Class<? extends CDPProfileInterface> profileMember) {
        profiles.put(getProfileType(profileMember), profileMember);
    }

    public <PROFILE extends Profile> CDPProfileInterface getProfile(final PROFILE profile) {
        try {
            return profiles.get(profile.getItemType()).getConstructor(Profile.class).newInstance(profile);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String getProfileType(final Class<? extends CDPProfileInterface> clazz) {
        return transformProfileType(ReflectionUtil.getTypeName(clazz));
    }

    private String transformProfileType(final String eventType) {
        int index = eventType.indexOf("_");
        return eventType.substring(index + 1).toLowerCase();
    }

}
