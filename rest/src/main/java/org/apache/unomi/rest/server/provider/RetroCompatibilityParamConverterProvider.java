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
package org.apache.unomi.rest.server.provider;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.unomi.api.ContextRequest;
import org.apache.unomi.api.EventsCollectorRequest;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.ext.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * This is a custom param converter provider only to be able to deserialize correctly objects used by the old servlets.
 * The old servlets was supporting JSON objects passed on GET requests using a parameter named "payload".
 * This only concern:
 *  GET /context.js
 *  GET /context.json
 *  GET /eventcollector
 *
 * And objects: ContextRequest, EventsCollectorRequest
 */
@Provider
public class RetroCompatibilityParamConverterProvider implements ParamConverterProvider {

    private final ObjectMapper objectMapper;
    private final List<Class<?>> allowedConversionForTypes = new ArrayList<>();

    public RetroCompatibilityParamConverterProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;

        allowedConversionForTypes.add(ContextRequest.class);
        allowedConversionForTypes.add(EventsCollectorRequest.class);
    }

    @Override
    public <T> ParamConverter<T> getConverter(Class<T> rawType, Type genericType, Annotation[] annotations) {

        if (allowedConversionForTypes.stream().anyMatch(rawType::isAssignableFrom)) {
            return new ParamConverter<T>() {
                @Override
                public T fromString(final String value) {
                    JsonFactory factory = objectMapper.getFactory();
                    try {
                        return objectMapper.readValue(factory.createParser(value), rawType);
                    } catch (Exception e) {
                        throw new ProcessingException(e);
                    }
                }

                @Override
                public String toString(final T value) {
                    throw new UnsupportedOperationException();
                }
            };
        }

        return null;
    }
}
