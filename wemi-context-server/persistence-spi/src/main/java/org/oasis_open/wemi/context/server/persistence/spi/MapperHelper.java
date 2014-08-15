package org.oasis_open.wemi.context.server.persistence.spi;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationModule;
import org.oasis_open.wemi.context.server.api.conditions.Condition;

/**
 * Created by toto on 13/08/14.
 */
public class MapperHelper {
    private static ObjectMapper objectMapper = null;

    public synchronized static ObjectMapper getObjectMapper() {
        if (objectMapper == null) {
            objectMapper = new ObjectMapper();
            objectMapper.registerModule(new JaxbAnnotationModule());
            SimpleModule deserializerModule =
                  new SimpleModule("PropertyTypedObjectDeserializerModule",
                      new Version(1, 0, 0, null, "org.oasis_open.wemi.context.server.rest", "deserializer"));
            PropertyTypedObjectDeserializer propertyTypedObjectDeserializer = new PropertyTypedObjectDeserializer();
            propertyTypedObjectDeserializer.registerMapping("type=.*Condition", Condition.class);
            deserializerModule.addDeserializer(Object.class, propertyTypedObjectDeserializer);
            objectMapper.registerModule(deserializerModule);
        }
        return objectMapper;
    }
}
