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

import org.apache.unomi.api.Consent;

public class ConsentHardcodedPropertyAccessor extends HardcodedPropertyAccessor<Consent> {

    public ConsentHardcodedPropertyAccessor(HardcodedPropertyAccessorRegistry registry) {
        super(registry);
    }

    @Override
    Object getProperty(Consent object, String propertyName, String leftoverExpression) {
        if ("typeIdentifier".equals(propertyName)) {
            return object.getTypeIdentifier();
        } else if ("scope".equals(propertyName)) {
            return object.getScope();
        } else if ("status".equals(propertyName)) {
            return object.getStatus();
        } else if ("statusDate".equals(propertyName)) {
            return object.getStatusDate();
        } else if ("revokeDate".equals(propertyName)) {
            return object.getRevokeDate();
        } else {
            return PROPERTY_NOT_FOUND_MARKER;
        }
    }
}
