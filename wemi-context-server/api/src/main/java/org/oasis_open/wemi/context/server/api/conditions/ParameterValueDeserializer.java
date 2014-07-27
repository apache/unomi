package org.oasis_open.wemi.context.server.api.conditions;

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
 * Created by loom on 23.07.14.
 */
public class ParameterValueDeserializer extends UntypedObjectDeserializer {

    public ParameterValueDeserializer() {
        super();
    }

    private Map<String, Class<? extends Object>> registry =
            new HashMap<String, Class<? extends Object>>();

    private Map<String,Set<String>> fieldValuesToMatch = new HashMap<String,Set<String>>();

    public void registerClass(String matchExpression,
                        Class<? extends Object> animalClass) {
        registry.put(matchExpression, animalClass);
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
        } else {

        }
        if (objectClass == null) {
            return super.deserialize(codec.treeAsTokens(treeNode), ctxt);
        }
        return codec.treeToValue(treeNode, objectClass);
    }
}