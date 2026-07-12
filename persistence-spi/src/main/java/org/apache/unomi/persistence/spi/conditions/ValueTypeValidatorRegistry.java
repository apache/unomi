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

package org.apache.unomi.persistence.spi.conditions;

import org.apache.unomi.api.services.ValueTypeValidator;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds OSGi-registered {@link ValueTypeValidator} services for runtime condition parameter validation.
 * <p>
 * Used by {@link ConditionContextHelper} when callers do not pass an explicit validator map.
 */
@Component(service = ValueTypeValidatorRegistry.class, immediate = true)
public class ValueTypeValidatorRegistry {

    private static volatile ValueTypeValidatorRegistry instance;

    private final Map<String, ValueTypeValidator> validatorsByTypeId = new ConcurrentHashMap<>();

    /**
     * Creates the registry and sets the static instance.
     */
    public ValueTypeValidatorRegistry() {
        instance = this;
    }

    /**
     * Returns the validators registered in the running container, keyed by lowercase type id.
     * Returns an empty map when the registry component is not active (e.g. unit tests).
     *
     * @return validators keyed by lowercase type id
     */
    public static Map<String, ValueTypeValidator> getValidators() {
        ValueTypeValidatorRegistry registry = instance;
        if (registry == null) {
            return Collections.emptyMap();
        }
        return registry.validatorsByTypeId;
    }

    /**
     * OSGi bind callback for value type validators.
     *
     * @param validator the validator to register
     */
    @Reference(service = ValueTypeValidator.class, cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC)
    public void bindValidator(ValueTypeValidator validator) {
        validatorsByTypeId.put(validator.getValueTypeId().toLowerCase(Locale.ROOT), validator);
    }

    /**
     * OSGi unbind callback for value type validators.
     *
     * @param validator the validator being removed
     */
    public void unbindValidator(ValueTypeValidator validator) {
        validatorsByTypeId.remove(validator.getValueTypeId().toLowerCase(Locale.ROOT));
    }
}
