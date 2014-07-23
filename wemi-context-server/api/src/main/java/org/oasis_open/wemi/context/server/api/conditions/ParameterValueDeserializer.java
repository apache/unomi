package org.oasis_open.wemi.context.server.api.conditions;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Created by loom on 23.07.14.
 */
public class ParameterValueDeserializer extends StdDeserializer<Object> {

    public ParameterValueDeserializer() {
        super(Object.class);
    }

    private Map<String, Class<? extends Object>> registry =
            new HashMap<String, Class<? extends Object>>();

    void registerClass(String uniqueAttribute,
                        Class<? extends Object> animalClass) {
        registry.put(uniqueAttribute, animalClass);
    }

    @Override
    public Object deserialize(
            JsonParser jp, DeserializationContext ctxt)
            throws IOException, JsonProcessingException {
        ObjectCodec codec = jp.getCodec();
        ObjectNode root = codec.readTree(jp);
        Class<? extends Object> objectClass = null;
        Iterator<Map.Entry<String, JsonNode>> elementsIterator =
                root.fields();
        while (elementsIterator.hasNext()) {
            Map.Entry<String, JsonNode> element = elementsIterator.next();
            String name = element.getKey();
            if (registry.containsKey(name)) {
                objectClass = registry.get(name);
                break;
            }
        }
        if (objectClass == null) return codec.treeToValue(root, Object.class);
        return codec.treeToValue(root, objectClass);
    }
}