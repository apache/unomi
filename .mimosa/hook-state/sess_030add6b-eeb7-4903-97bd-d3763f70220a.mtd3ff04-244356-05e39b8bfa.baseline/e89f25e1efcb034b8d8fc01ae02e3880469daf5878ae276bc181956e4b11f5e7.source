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

import org.apache.unomi.api.PropertyType;
import org.apache.unomi.graphql.types.output.CDPPropertyInterface;
import org.apache.unomi.graphql.utils.ReflectionUtil;
import org.osgi.service.component.annotations.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component(immediate = true, service = CDPPropertyInterfaceRegister.class)
public class CDPPropertyInterfaceRegister {

    private ConcurrentHashMap<String, Class<? extends CDPPropertyInterface>> properties;

    public CDPPropertyInterfaceRegister() {
        properties = new ConcurrentHashMap<>();
    }

    public void register(final Class<? extends CDPPropertyInterface> propertyMember) {
        properties.put(getPropertyType(propertyMember), propertyMember);
    }

    public CDPPropertyInterface getProperty(final PropertyType type) {
        if (!properties.containsKey(type.getValueTypeId())) {
            return null;
        }

        try {
            return properties.get(type.getValueTypeId()).getConstructor(PropertyType.class).newInstance(type);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String getPropertyType(final Class<? extends CDPPropertyInterface> clazz) {
        return ReflectionUtil.getUnomiType(clazz);
    }
}
