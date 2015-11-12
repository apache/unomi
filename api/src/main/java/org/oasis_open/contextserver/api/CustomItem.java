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

package org.oasis_open.contextserver.api;

import java.util.HashMap;
import java.util.Map;

/**
 * A generic extension of Item for context server extensions, properties are stored in a Map.
 */
public class CustomItem extends Item {
    private static final long serialVersionUID = -7178914125308851922L;

    /**
     * The CustomItem ITEM_TYPE.
     * @see Item for a discussion of ITEM_TYPE
     */
    public static final String ITEM_TYPE = "custom";

    private Map<String,Object> properties = new HashMap<String,Object>();

    /**
     * Instantiates a new Custom item.
     */
    public CustomItem() {
    }

    /**
     * Instantiates a new Custom item.
     *
     * @param itemId   the item id
     * @param itemType the item type
     */
    public CustomItem(String itemId, String itemType) {
        super(itemId);
        this.itemType = itemType;
    }

    /**
     * Retrieves this CustomItem's properties.
     *
     * @return a Map of the item's properties associating the property name as key to its value.
     */
    public Map<String, Object> getProperties() {
        return properties;
    }

    /**
     * Sets the properties.
     *
     * @param properties the properties
     */
    public void setProperties(Map<String, Object> properties) {
        this.properties = properties;
    }
}
