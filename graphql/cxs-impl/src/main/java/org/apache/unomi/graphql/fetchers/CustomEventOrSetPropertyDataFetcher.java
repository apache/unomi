/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.unomi.graphql.fetchers;

import graphql.schema.DataFetchingEnvironment;
import org.apache.unomi.graphql.types.output.CDPEventInterface;
import org.apache.unomi.graphql.utils.ReflectionUtil;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;

public class CustomEventOrSetPropertyDataFetcher extends DynamicFieldDataFetcher<Object> {

    public CustomEventOrSetPropertyDataFetcher(String propertyName) {
        super(propertyName, "custom");
    }

    @Override
    public Object get(final DataFetchingEnvironment environment) throws InvocationTargetException, IllegalAccessException {
        final Object source = environment.getSource();

        Object value;
        if (source instanceof Map) {
            // Maps are used for sets
            value = ((Map) source).get(fieldName);
        } else {
            // Search for defined methods and fields of source object ( like methods from CDPEventInterface or CDPProfileInterface )
            value = getValue(fieldName, source, environment);
            if (value == null) {
                if (source instanceof CDPEventInterface) {
                    // Search Event class for custom fields that are not on the CDPEventInterface
                    final CDPEventInterface eventInterface = (CDPEventInterface) source;
                    value = getValue(fieldName, eventInterface.getEvent(), environment);
                    if (value == null) {
                        // Finally fall back to getting named property
                        value = eventInterface.getProperty(fieldName);
                    }
                }
            }
        }
        return inflateType(value);
    }

    private Object getValue(final String fieldName, final Object object, final DataFetchingEnvironment environment) throws IllegalAccessException, InvocationTargetException {
        final Method method = ReflectionUtil.findMethod(fieldName, object.getClass());
        if (method != null) {
            return invokeMethod(method, object, environment);
        } else {
            final Field field = ReflectionUtil.findField(fieldName, object.getClass());
            if (field != null) {
                return field.get(object);
            }
        }
        return null;
    }

    private Object invokeMethod(final Method method, final Object object, final DataFetchingEnvironment environment) throws InvocationTargetException, IllegalAccessException {
        Class[] paramTypes = method.getParameterTypes();
        if (paramTypes.length == 1 && paramTypes[0].equals(DataFetchingEnvironment.class)) {
            return method.invoke(object, environment);
        } else if (paramTypes.length == 0) {
            return method.invoke(object);
        }
        return null;
    }

}
