package org.oasis_open.contextserver.persistence.spi;

/*
 * #%L
 * context-server-persistence-spi
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2014 - 2015 Jahia Solutions
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.oasis_open.contextserver.api.CustomItem;
import org.oasis_open.contextserver.api.Item;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ItemDeserializer extends StdDeserializer<Item> {

    private static final long serialVersionUID = -7040054009670771266L;
    private Map<String,Class<? extends Item>> classes = new HashMap<>();

    public ItemDeserializer() {
        super(Item.class);
    }

    public void registerMapping(String type, Class<? extends Item> clazz) {
        classes.put(type, clazz);
    }

    @Override
    public Item deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException, JsonProcessingException {
        ObjectCodec codec = jp.getCodec();
        ObjectNode treeNode = codec.readTree(jp);
        String type = treeNode.get("itemType").textValue();
        Class<? extends Item> objectClass = classes.get(type);
        if (objectClass == null) {
            objectClass = CustomItem.class;
        } else {
            treeNode.remove("itemType");
        }
        Item item = codec.treeToValue(treeNode, objectClass);
        item.setItemId(treeNode.get("itemId").asText());
        return item;
    }
}
