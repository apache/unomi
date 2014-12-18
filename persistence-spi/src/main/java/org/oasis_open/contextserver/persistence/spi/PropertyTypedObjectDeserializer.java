package org.oasis_open.contextserver.persistence.spi;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.deser.std.UntypedObjectDeserializer;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

/**
 * This Jackson deserializer makes it possible to register field matching
 * regular expressions that can be matched to class names, as in the following
 * example:
 *
 *            SimpleModule deserializerModule =
 *                  new SimpleModule("PropertyTypedObjectDeserializerModule",
 *                      new Version(1, 0, 0, null, "org.oasis_open.contextserver.rest", "deserializer"));
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

    public PropertyTypedObjectDeserializer() {
        super();
    }

    private Map<String, Class<? extends Object>> registry =
            new HashMap<String, Class<? extends Object>>();

    private Map<String,Set<String>> fieldValuesToMatch = new HashMap<String,Set<String>>();

    public void registerMapping(String matchExpression,
                                Class<? extends Object> mappedClass) {
        registry.put(matchExpression, mappedClass);
        String[] fieldParts = matchExpression.split("=");
        Set<String> valuesToMatch = fieldValuesToMatch.get(fieldParts[0]);
        if (valuesToMatch == null) {
            valuesToMatch = new HashSet<String>();
        }
        valuesToMatch.add(fieldParts[1]);
        fieldValuesToMatch.put(fieldParts[0], valuesToMatch);
    }

    @Override
    public Object deserialize(
            JsonParser jp, DeserializationContext ctxt)
            throws IOException, JsonProcessingException {
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