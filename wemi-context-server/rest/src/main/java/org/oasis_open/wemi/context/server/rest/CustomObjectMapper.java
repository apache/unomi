package org.oasis_open.wemi.context.server.rest;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationModule;
import org.oasis_open.wemi.context.server.api.conditions.Condition;
import org.oasis_open.wemi.context.server.api.conditions.PropertyTypedObjectDeserializer;

/**
 * Custom object mapper to be able to configure Jackson to our needs
 */
public class CustomObjectMapper extends ObjectMapper {

    public CustomObjectMapper() {
        super();
        super.registerModule(new JaxbAnnotationModule());
        SimpleModule deserializerModule =
              new SimpleModule("PropertyTypedObjectDeserializerModule",
                  new Version(1, 0, 0, null, "org.oasis_open.wemi.context.server.rest", "deserializer"));
        PropertyTypedObjectDeserializer propertyTypedObjectDeserializer = new PropertyTypedObjectDeserializer();
        propertyTypedObjectDeserializer.registerMapping("type=.*Condition", Condition.class);
        deserializerModule.addDeserializer(Object.class, propertyTypedObjectDeserializer);
        super.registerModule(deserializerModule);
    }
}