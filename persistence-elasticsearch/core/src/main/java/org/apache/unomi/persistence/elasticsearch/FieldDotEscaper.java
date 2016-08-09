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
package org.apache.unomi.persistence.elasticsearch;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * A utility class to un/escape field names that contain dots that are not allowed in ElasticSearch 2.x
 */
public class FieldDotEscaper {

    public static final String DOT_ESCAPE_MARKER = "__DOT__";

    public static String escapeJson(final String jsonInput) {
        return escapeJson(jsonInput, null);
    }

    public static String escapeJson(final String jsonInput, final Set<String> modifiedNames) {
        if (!jsonInput.contains(".")) { // optimization in case no dot is present at all
            return jsonInput;
        }
        StringBuffer result = new StringBuffer();
        FieldDotJsonTransformer jsonTransformer = new FieldDotJsonTransformer(jsonInput, result, DOT_ESCAPE_MARKER);
        Set<String> pathsModified = jsonTransformer.transform();
        if (modifiedNames != null) {
            modifiedNames.addAll(pathsModified);
        }
        return result.toString();
    }

    public static String unescapeJson(final String jsonInput) {
        return unescapeString(jsonInput);
    }

    public static String escapeString(final String stringInput) {
        return stringInput.replaceAll("\\.", DOT_ESCAPE_MARKER);
    }

    public static String unescapeString(final String stringInput) {
        return stringInput.replaceAll(DOT_ESCAPE_MARKER, ".");
    }

    public static Map<? extends String, ?> escapeMap(final Map<? extends String, ?> mapInput) {
        Map<String,Object> result = new LinkedHashMap<>(mapInput.size());
        for (Map.Entry<? extends String, ? extends Object> entry : mapInput.entrySet()) {
            String entryKey = entry.getKey();
            if (entryKey.contains(".")) {
                entryKey = escapeString(entryKey);
            }
            result.put(entryKey, entry.getValue());
        }
        return result;
    }

    public static Map<? extends String, ?> unescapeMap(final Map<? extends String, ?> mapInput) {
        Map<String, Object> result = new LinkedHashMap<>(mapInput.size());
        for (Map.Entry<? extends String, ?> entry : mapInput.entrySet()) {
            String entryKey = entry.getKey();
            if (entryKey.contains(DOT_ESCAPE_MARKER)) {
                entryKey = unescapeString(entryKey);
            }
            result.put(entryKey, entry.getValue());
        }
        return result;
    }

    public static Properties escapeProperties(final Properties input) {
        Properties result = new Properties();
        for (String propertyName : input.stringPropertyNames()) {
            String newPropertyName = propertyName;
            if (propertyName.contains(".")) {
                newPropertyName = escapeString(propertyName);
            }
            result.put(newPropertyName, input.getProperty(propertyName));
        }
        return result;
    }

    public static Properties unescapeProperties(final Properties input) {
        Properties result = new Properties();
        for (String propertyName : input.stringPropertyNames()) {
            String newPropertyName = propertyName;
            if (propertyName.contains(DOT_ESCAPE_MARKER)) {
                newPropertyName = unescapeString(propertyName);
            }
            result.put(newPropertyName, input.getProperty(propertyName));
        }
        return result;
    }

}
