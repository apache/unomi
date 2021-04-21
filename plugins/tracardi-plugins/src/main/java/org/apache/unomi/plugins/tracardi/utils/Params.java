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

package org.apache.unomi.plugins.tracardi.utils;

import org.apache.unomi.api.actions.Action;

public class Params {

    public static Integer getInteger(Object value) throws NumberFormatException {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        } else {
            return Integer.parseInt(value.toString());
        }
    }

    public static String getParamAsString(Action action, String key, boolean reguired) throws IllegalArgumentException, NoSuchFieldException {
        Object property = action.getParameterValues().get(key);
        if (property == null) {
            if (reguired) {
                throw new NoSuchFieldException("Missing parameter " + key);
            } else {
                return null;
            }
        }
        Object value = action.getParameterValues().get(key);
        if (!(value instanceof String)) {
            throw new IllegalArgumentException("Parameter `" + key + "` must be string.");
        }
        return (String) value;
    }

    public static Integer getParamAsInteger(Action action, String key, int defaultValue) throws NumberFormatException {
        Object property = action.getParameterValues().get(key);
        if (property == null) {
            return defaultValue;
        }
        Object value = action.getParameterValues().get(key);
        return getInteger(value);
    }

    public static Integer getParamAsInteger(Action action, String key, boolean required) throws NoSuchFieldException {
        Object property = action.getParameterValues().get(key);
        if (property == null) {
            if (required) {
                throw new NoSuchFieldException("Missing parameter " + key);
            } else {
                return null;
            }
        }
        Object value = action.getParameterValues().get(key);
        return getInteger(value);
    }
}
