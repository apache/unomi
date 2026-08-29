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
package org.apache.unomi.services.impl.validation.validators;

import org.apache.unomi.api.services.ValueTypeValidator;

public abstract class AbstractNumericValueTypeValidator implements ValueTypeValidator {
    private final String valueTypeId;
    private final Class<?> expectedClass;

    protected AbstractNumericValueTypeValidator(String valueTypeId, Class<?> expectedClass) {
        this.valueTypeId = valueTypeId;
        this.expectedClass = expectedClass;
    }

    @Override
    public String getValueTypeId() {
        return valueTypeId;
    }

    @Override
    public boolean validate(Object value) {
        return value == null || expectedClass.isInstance(value);
    }

    @Override
    public String getValueTypeDescription() {
        return "Value must be a " + valueTypeId;
    }
}
