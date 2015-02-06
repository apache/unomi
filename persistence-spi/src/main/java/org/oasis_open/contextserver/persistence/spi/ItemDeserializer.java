package org.oasis_open.contextserver.persistence.spi;

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
