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
package org.apache.unomi.plugins.baseplugin.conditions.accessors;

/**
 * Hardcoded property accessors serve two purpose:
 * - control access to object fields, only expose the ones that are "safe" to use
 * - prevent using Java Reflection API that is both slower and potentially unsafe as there could be potential to abuse it.
 */
public abstract class HardcodedPropertyAccessor<T> {

    public static final String PROPERTY_NOT_FOUND_MARKER = "$$$###PROPERTY_NOT_FOUND###$$$";

    protected HardcodedPropertyAccessorRegistry registry;

    public HardcodedPropertyAccessor(HardcodedPropertyAccessorRegistry registry) {
        this.registry = registry;
    }

    abstract Object getProperty(T object, String propertyName, String leftoverExpression);

}
