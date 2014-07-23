package org.oasis_open.wemi.context.server.api.conditions;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.jsontype.impl.TypeIdResolverBase;

/**
 * Created by loom on 23.07.14.
 */
public class ParameterValueTypeIdResolver extends TypeIdResolverBase {
    public String idFromValue(Object value) {
        return null;
    }

    public String idFromValueAndType(Object value, Class<?> suggestedType) {
        return null;
    }

    @Override
    public JavaType typeFromId(String id) {
        return null;
    }

    public JsonTypeInfo.Id getMechanism() {
        return null;
    }
}
