package org.oasis_open.wemi.context.server.impl.services;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.AnnotationIntrospector;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.AnnotationIntrospectorPair;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationIntrospector;
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationModule;
import org.oasis_open.wemi.context.server.api.conditions.*;
import org.oasis_open.wemi.context.server.api.consequences.Consequence;
import org.oasis_open.wemi.context.server.api.consequences.ConsequenceType;
import org.oasis_open.wemi.context.server.api.services.DefinitionsService;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonString;
import javax.json.JsonValue;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ParserHelper {

    private static ObjectMapper objectMapper = null;

    public static Condition parseCondition(DefinitionsService service, JsonObject object) {
        ConditionType typeNode = service.getConditionType(object.getString("type"));
        JsonObject parameterValues = object.getJsonObject("parameterValues");

        Condition node = new Condition();
        node.setConditionType(typeNode);
        Map<String, ParameterValue> values = new HashMap<String, ParameterValue>();
        node.setParameterValues(values);

        for (Parameter parameter : typeNode.getConditionParameters()) {
            ParameterValue parameterValue = new ParameterValue();
            if (parameter.isMultivalued()) {
                final ArrayList<Object> objects = new ArrayList<Object>();
                JsonArray array = parameterValues.getJsonArray(parameter.getId());
                for (JsonValue value : array) {
                    objects.add(getParameterValue(service, parameter, value));
                }
                parameterValue.setValue(objects);
            } else {
                parameterValue.setValue(getParameterValue(service, parameter, parameterValues.get(parameter.getId())));
            }
            values.put(parameter.getId(), parameterValue);
        }
        return node;
    }

    public static Consequence parseConsequence(DefinitionsService service, JsonObject object) {
        ConsequenceType typeNode = service.getConsequenceType(object.getString("type"));
        JsonObject parameterValues = object.getJsonObject("parameterValues");

        Consequence node = new Consequence();
        node.setConsequenceType(typeNode);
        Map<String, ParameterValue> values = new HashMap<String, ParameterValue>();
        node.setConsequencesParameterValues(values);

        for (Parameter parameter : typeNode.getConsequenceParameters()) {
            ParameterValue parameterValue = new ParameterValue();
            if (parameter.isMultivalued()) {
                final ArrayList<Object> objects = new ArrayList<Object>();
                JsonArray array = parameterValues.getJsonArray(parameter.getId());
                for (JsonValue value : array) {
                    objects.add(getParameterValue(service, parameter, value));
                }
                parameterValue.setValue(objects);
            } else {
                parameterValue.setValue(getParameterValue(service, parameter, parameterValues.get(parameter.getId())));
            }
            values.put(parameter.getId(), parameterValue);
        }
        return node;
    }

    private static Object getParameterValue(DefinitionsService service, Parameter parameter, JsonValue value) {
        if (parameter.getType().equals("Condition")) {
            return parseCondition(service, (JsonObject) value);
        } else if (parameter.getType().equals("comparisonOperator")) {
            return ((JsonString)value).getString();
        } else if (parameter.getType().equals("string")) {
            return ((JsonString)value).getString();
        }
        return null;
    }


    public static List<Parameter> parseParameters(JsonObject conditionObject) {
        List<Parameter> parameters = new ArrayList<Parameter>();
        JsonArray parameterArray = conditionObject.getJsonArray("parameters");
        for (int i = 0; i < parameterArray.size(); i++) {
            JsonObject parameterObject = parameterArray.getJsonObject(i);
            String paramId = parameterObject.getString("id");
            String paramName = parameterObject.getString("name");
            String paramDescription = parameterObject.getString("description");
            String paramType = parameterObject.getString("type");
            boolean multivalued = parameterObject.getBoolean("multivalued");
            String paramChoiceListInitializerClass = null;
            try {
                paramChoiceListInitializerClass = parameterObject.getString("choicelistInitializerClass");
            } catch (Exception e) {
                e.printStackTrace();
            }
            Parameter conditionParameter = new Parameter(paramId, paramName, paramDescription, paramType, multivalued, paramChoiceListInitializerClass);
            parameters.add(conditionParameter);
        }
        return parameters;
    }

    public synchronized static ObjectMapper getObjectMapper() {
        if (objectMapper == null) {
            objectMapper = new ObjectMapper();
            objectMapper.registerModule(new JaxbAnnotationModule());
            // objectMapper.enableDefaultTypingAsProperty(ObjectMapper.DefaultTyping.JAVA_LANG_OBJECT, "@class");
            SimpleModule deserializerModule =
                  new SimpleModule("PolymorphicParameterValueDeserializerModule",
                      new Version(1, 0, 0, null, "org.oasis_open.wemi.context.server.rest", "deserializer"));
            ParameterValueDeserializer parameterValueDeserializer = new ParameterValueDeserializer();
            parameterValueDeserializer.registerClass("type=.*Condition", Condition.class);
            deserializerModule.addDeserializer(Object.class, parameterValueDeserializer);
            objectMapper.registerModule(deserializerModule);
        }
        return objectMapper;
    }

}
