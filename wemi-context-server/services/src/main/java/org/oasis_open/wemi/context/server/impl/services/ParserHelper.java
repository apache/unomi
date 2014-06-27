package org.oasis_open.wemi.context.server.impl.services;

import org.oasis_open.wemi.context.server.api.conditions.Condition;
import org.oasis_open.wemi.context.server.api.conditions.ConditionType;
import org.oasis_open.wemi.context.server.api.conditions.Parameter;
import org.oasis_open.wemi.context.server.api.conditions.ParameterValue;
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
    public static Condition parseCondition(DefinitionsService service, JsonObject object) {
        ConditionType typeNode = service.getConditionType(object.getString("type"));
        JsonObject parameterValues = object.getJsonObject("parameterValues");

        Condition node = new Condition();
        node.setConditionType(typeNode);
        Map<String, ParameterValue> values = new HashMap<String, ParameterValue>();
        node.setConditionParameterValues(values);

        for (Parameter parameter : typeNode.getConditionParameters()) {
            final ArrayList<Object> objects = new ArrayList<Object>();
            values.put(parameter.getId(), new ParameterValue(parameter.getId(), objects));

            if (parameter.isMultivalued()) {
                JsonArray array = parameterValues.getJsonArray(parameter.getId());
                for (JsonValue value : array) {
                    objects.add(getParameterValue(service, parameter, value));
                }
            } else {
                objects.add(getParameterValue(service, parameter, parameterValues.get(parameter.getId())));
            }
        }
        return node;
    }

    public static Consequence parseConsequence(DefinitionsService service, JsonObject object) throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        ConsequenceType typeNode = service.getConsequenceType(object.getString("type"));
        JsonObject parameterValues = object.getJsonObject("parameterValues");

        Consequence node = (Consequence) Class.forName(typeNode.getClazz()).newInstance();

        node.setType(typeNode);
        Map<String, ParameterValue> values = new HashMap<String, ParameterValue>();
        node.setConsequencesParameterValues(values);

        for (Parameter parameter : typeNode.getConsequenceParameters()) {
            final ArrayList<Object> objects = new ArrayList<Object>();
            values.put(parameter.getId(), new ParameterValue(parameter.getId(), objects));

            if (parameter.isMultivalued()) {
                JsonArray array = parameterValues.getJsonArray(parameter.getId());
                for (JsonValue value : array) {
                    objects.add(getParameterValue(service, parameter, value));
                }
            } else {
                objects.add(getParameterValue(service, parameter, parameterValues.get(parameter.getId())));
            }
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


}
