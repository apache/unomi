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

package org.apache.unomi.persistence.spi;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.unomi.api.CustomItem;
import org.apache.unomi.api.Item;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ItemDeserializer extends StdDeserializer<Item> {

    private static final long serialVersionUID = -7040054009670771266L;
    private Map<String, Class<? extends Item>> classes = new HashMap<>();

    public ItemDeserializer() {
        super(Item.class);
    }

    public void registerMapping(String type, Class<? extends Item> clazz) {
        classes.put(type, clazz);
    }

    public void unregisterMapping(String type) {
        classes.remove(type);
    }

    @Override
    public Item deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
        ObjectCodec codec = jp.getCodec();
        JsonNode jsonNode = codec.readTree(jp);
        if (jsonNode == null || jsonNode.isNull()) {
            return null;
        }
        if (!jsonNode.isObject()) {
            throw JsonMappingException.from(jp, "Expected a JSON object to deserialize an Item but got "
                    + describeJsonNode(jsonNode));
        }
        ObjectNode treeNode = (ObjectNode) jsonNode;
        JsonNode itemTypeNode = treeNode.get("itemType");
        if (itemTypeNode == null || !itemTypeNode.isTextual()) {
            throw JsonMappingException.from(jp, "Item JSON object must contain a textual itemType property");
        }
        String type = itemTypeNode.textValue();
        JsonNode itemIdNode = treeNode.get("itemId");
        if (itemIdNode == null || !itemIdNode.isTextual()) {
            throw JsonMappingException.from(jp, "Item JSON object must contain a textual itemId property");
        }
        Class<? extends Item> objectClass = classes.get(type);
        if (objectClass == null) {
            objectClass = CustomItem.class;
        } else {
            treeNode.remove("itemType");
        }
        Item item = codec.treeToValue(treeNode, objectClass);
        item.setItemId(itemIdNode.asText());
        if (item instanceof CustomItem) {
            ((CustomItem) item).setCustomItemType(type);
        }
        return item;
    }

    private static String describeJsonNode(JsonNode jsonNode) {
        if (jsonNode.isTextual()) {
            return "a string";
        }
        if (jsonNode.isArray()) {
            return "an array";
        }
        return jsonNode.getNodeType().name().toLowerCase();
    }
}
