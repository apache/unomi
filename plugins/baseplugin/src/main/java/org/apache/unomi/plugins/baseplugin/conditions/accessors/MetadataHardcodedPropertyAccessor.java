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
package org.apache.unomi.plugins.baseplugin.conditions.accessors;

import org.apache.unomi.api.Metadata;

public class MetadataHardcodedPropertyAccessor extends HardcodedPropertyAccessor<Metadata> {

    public MetadataHardcodedPropertyAccessor(HardcodedPropertyAccessorRegistry registry) {
        super(registry);
    }

    @Override
    Object getProperty(Metadata object, String propertyName, String leftoverExpression) {
        if ("id".equals(propertyName)) {
            return object.getId();
        } else if ("name".equals(propertyName)) {
            return object.getName();
        } else if ("description".equals(propertyName)) {
            return object.getDescription();
        } else if ("scope".equals(propertyName)) {
            return object.getScope();
        } else if ("tags".equals(propertyName)) {
            return object.getTags();
        } else if ("systemTags".equals(propertyName)) {
            return object.getSystemTags();
        } else if ("enabled".equals(propertyName)) {
            return object.isEnabled();
        } else if ("missingPlugins".equals(propertyName)) {
            return object.isMissingPlugins();
        } else if ("hidden".equals(propertyName)) {
            return object.isHidden();
        } else if ("readOnly".equals(propertyName)) {
            return object.isReadOnly();
        }
        return PROPERTY_NOT_FOUND_MARKER;
    }
}
