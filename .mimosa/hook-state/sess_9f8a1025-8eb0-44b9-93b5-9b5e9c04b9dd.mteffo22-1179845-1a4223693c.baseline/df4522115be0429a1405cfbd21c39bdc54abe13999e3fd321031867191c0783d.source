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

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.apache.unomi.api.CustomItem;
import org.apache.unomi.api.Item;
import org.apache.unomi.api.Profile;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ItemDeserializerTest {

    private ObjectMapper objectMapper;

    @Before
    public void setUp() {
        objectMapper = new ObjectMapper();
        SimpleModule module = new SimpleModule();
        module.addDeserializer(Item.class, new ItemDeserializer());
        objectMapper.registerModule(module);
    }

    @Test
    public void deserialize_validCustomItem() throws Exception {
        Item item = objectMapper.readValue("{\"itemType\":\"page\",\"itemId\":\"home\"}", Item.class);
        assertNotNull(item);
        assertEquals("home", item.getItemId());
        assertTrue(item instanceof CustomItem);
        assertEquals("page", ((CustomItem) item).getCustomItemType());
    }

    @Test(expected = JsonMappingException.class)
    public void deserialize_nullItem_throwsJsonMappingException() throws Exception {
        objectMapper.readValue("null", Item.class);
    }

    @Test(expected = JsonMappingException.class)
    public void deserialize_stringValue_throwsJsonMappingException() throws Exception {
        objectMapper.readValue("\"not-an-object\"", Item.class);
    }

    @Test(expected = JsonMappingException.class)
    public void deserialize_arrayValue_throwsJsonMappingException() throws Exception {
        objectMapper.readValue("[{\"itemType\":\"page\",\"itemId\":\"home\"}]", Item.class);
    }

    @Test(expected = JsonMappingException.class)
    public void deserialize_missingItemId_throwsJsonMappingException() throws Exception {
        objectMapper.readValue("{\"itemType\":\"page\"}", Item.class);
    }

    @Test(expected = JsonMappingException.class)
    public void deserialize_missingItemType_throwsJsonMappingException() throws Exception {
        objectMapper.readValue("{\"itemId\":\"home\"}", Item.class);
    }

    @Test(expected = JsonMappingException.class)
    public void deserialize_booleanValue_throwsJsonMappingException() throws Exception {
        objectMapper.readValue("true", Item.class);
    }

    @Test(expected = JsonMappingException.class)
    public void deserialize_numericItemType_throwsJsonMappingException() throws Exception {
        objectMapper.readValue("{\"itemType\":42,\"itemId\":\"home\"}", Item.class);
    }

    @Test(expected = JsonMappingException.class)
    public void deserialize_numericItemId_throwsJsonMappingException() throws Exception {
        objectMapper.readValue("{\"itemType\":\"page\",\"itemId\":99}", Item.class);
    }

    @Test
    public void deserialize_registeredMapping_usesRegisteredClass() throws Exception {
        ItemDeserializer deserializer = new ItemDeserializer();
        deserializer.registerMapping(Profile.ITEM_TYPE, Profile.class);
        SimpleModule module = new SimpleModule();
        module.addDeserializer(Item.class, deserializer);
        ObjectMapper profileMapper = new ObjectMapper();
        profileMapper.registerModule(module);

        Item item = profileMapper.readValue("{\"itemType\":\"profile\",\"itemId\":\"p1\"}", Item.class);
        assertNotNull(item);
        assertTrue(item instanceof Profile);
        assertEquals("p1", item.getItemId());
    }

    @Test
    public void deserialize_unregisterMapping_fallsBackToCustomItem() throws Exception {
        ItemDeserializer deserializer = new ItemDeserializer();
        deserializer.registerMapping(Profile.ITEM_TYPE, Profile.class);
        deserializer.unregisterMapping(Profile.ITEM_TYPE);
        SimpleModule module = new SimpleModule();
        module.addDeserializer(Item.class, deserializer);
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(module);

        Item item = mapper.readValue("{\"itemType\":\"profile\",\"itemId\":\"p1\"}", Item.class);
        assertNotNull(item);
        assertTrue(item instanceof CustomItem);
        assertEquals("profile", ((CustomItem) item).getCustomItemType());
        assertEquals("p1", item.getItemId());
    }
}
