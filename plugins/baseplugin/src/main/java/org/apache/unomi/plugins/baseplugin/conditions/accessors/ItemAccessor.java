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

import org.apache.unomi.api.Item;
import org.apache.unomi.plugins.baseplugin.conditions.HardcodedPropertyAccessorRegistry;

public class ItemAccessor extends HardcodedPropertyAccessor<Item> {

    public ItemAccessor(HardcodedPropertyAccessorRegistry registry) {
        super(registry);
    }

    @Override
    public Object getProperty(Item object, String propertyName, String leftoverExpression) {
        if ("itemId".equals(propertyName)) {
            return object.getItemId();
        }
        if ("itemType".equals(propertyName)) {
            return object.getItemType();
        }
        if ("scope".equals(propertyName)) {
            return object.getScope();
        }
        if ("version".equals(propertyName)) {
            return object.getVersion();
        }
        return PROPERTY_NOT_FOUND_MARKER;
    }
}
