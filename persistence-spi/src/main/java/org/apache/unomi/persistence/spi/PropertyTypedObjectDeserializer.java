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
import com.fasterxml.jackson.core.JsonTokenId;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.UntypedObjectDeserializer;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.util.*;

/**
 * This Jackson deserializer makes it possible to register field matching
 * regular expressions that can be matched to class names, as in the following
 * example:
 *
 *            SimpleModule deserializerModule =
 *                  new SimpleModule("PropertyTypedObjectDeserializerModule",
 *                      new Version(1, 0, 0, null, "org.apache.unomi.rest", "deserializer"));
 *            PropertyTypedObjectDeserializer propertyTypedObjectDeserializer = new PropertyTypedObjectDeserializer();
 *            propertyTypedObjectDeserializer.registerMapping("type=.*Condition", Condition.class);
 *            deserializerModule.addDeserializer(Object.class, propertyTypedObjectDeserializer);
 *            objectMapper.registerModule(deserializerModule);
 *
 * In this example any JSON object that has a "type" property that matches the
 * ".*Condition" regular expression will be parsed and mapped to a Condition class
 *
 * Note that there exists a way to map properties as type identifiers in Jackson,
 * but this feature is very limited and requires hardcoding possible values.
 * This deserializer is much more flexible and powerful.
 */
public class PropertyTypedObjectDeserializer extends UntypedObjectDeserializer {

    private static final long serialVersionUID = -2561171359946902967L;

    private Map<String, Class<? extends Object>> registry =
            new LinkedHashMap<String, Class<? extends Object>>();

    private Map<String,Set<String>> fieldValuesToMatch = new LinkedHashMap<String,Set<String>>();

    public void registerMapping(String matchExpression,
                                Class<? extends Object> mappedClass) {
        registry.put(matchExpression, mappedClass);
        String[] fieldParts = matchExpression.split("=");
        Set<String> valuesToMatch = fieldValuesToMatch.get(fieldParts[0]);
        if (valuesToMatch == null) {
            valuesToMatch = new LinkedHashSet<String>();
        }
        valuesToMatch.add(fieldParts[1]);
        fieldValuesToMatch.put(fieldParts[0], valuesToMatch);
    }

    @Override
    public Object deserialize(
            JsonParser jp, DeserializationContext ctxt)
            throws IOException {
        if (jp.getCurrentTokenId() != JsonTokenId.ID_START_OBJECT) {
            return super.deserialize(jp, ctxt);
        }
        ObjectCodec codec = jp.getCodec();
        TreeNode treeNode = codec.readTree(jp);
        Class<? extends Object> objectClass = null;
        if (treeNode instanceof ObjectNode) {
            ObjectNode root = (ObjectNode) treeNode;
            Iterator<Map.Entry<String, JsonNode>> elementsIterator =
                    root.fields();
            while (elementsIterator.hasNext()) {
                Map.Entry<String, JsonNode> element = elementsIterator.next();
                String name = element.getKey();
                if (fieldValuesToMatch.containsKey(name)) {
                    Set<String> valuesToMatch = fieldValuesToMatch.get(name);
                    for (String valueToMatch : valuesToMatch) {
                        if (element.getValue().asText().matches(valueToMatch)) {
                            objectClass = registry.get(name + "=" + valueToMatch);
                            break;
                        }
                    }
                    if (objectClass != null) {
                        break;
                    }
                }
            }
            if (objectClass == null) {
                objectClass = HashMap.class;
            }
        } else {

        }
        if (objectClass == null) {
            return super.deserialize(codec.treeAsTokens(treeNode), ctxt);
        }
        return codec.treeToValue(treeNode, objectClass);
    }
}
