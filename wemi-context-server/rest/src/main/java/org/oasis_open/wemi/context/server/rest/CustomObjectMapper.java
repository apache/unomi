package org.oasis_open.wemi.context.server.rest;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationModule;
import org.oasis_open.wemi.context.server.api.conditions.Condition;
import org.oasis_open.wemi.context.server.api.conditions.ParameterValue;
import org.oasis_open.wemi.context.server.api.conditions.ParameterValueDeserializer;

/**
 * Custom object mapper to be able to configure Jackson to our needs
 */
public class CustomObjectMapper extends ObjectMapper {

    public CustomObjectMapper() {
        super();
        super.registerModule(new JaxbAnnotationModule());
        // super.enableDefaultTypingAsProperty(ObjectMapper.DefaultTyping.JAVA_LANG_OBJECT, "@class");
        SimpleModule deserializerModule =
              new SimpleModule("PolymorphicParameterValueDeserializerModule",
                  new Version(1, 0, 0, null, "org.oasis_open.wemi.context.server.rest", "deserializer"));
        ParameterValueDeserializer parameterValueDeserializer = new ParameterValueDeserializer();
        parameterValueDeserializer.registerClass("type=.*Condition", Condition.class);
        deserializerModule.addDeserializer(Object.class, parameterValueDeserializer);
        super.registerModule(deserializerModule);
    }
}