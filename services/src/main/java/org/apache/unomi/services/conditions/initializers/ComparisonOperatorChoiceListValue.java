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
package org.apache.unomi.services.conditions.initializers;

import org.apache.unomi.api.PluginType;
import org.apache.unomi.api.conditions.initializers.ChoiceListValue;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Choice list value for the comparison operator, which also includes the information about applicable value types for the operator.
 * 
 * @author Sergiy Shyrkov
 */
public class ComparisonOperatorChoiceListValue extends ChoiceListValue implements PluginType {

    private Set<String> appliesTo = Collections.emptySet();

    private long pluginId;

    /**
     * Initializes an instance of this class.
     * 
     * @param id
     *            the ID of the property
     * @param name
     *            the display name
     */
    public ComparisonOperatorChoiceListValue(String id, String name) {
        super(id, name);
    }

    /**
     * Initializes an instance of this class.
     * 
     * @param id
     *            the ID of the property
     * @param name
     *            the display name
     * @param appliesTo
     *            array of value types this operator applies to; if not specified the operator is applicable for all value types
     */
    public ComparisonOperatorChoiceListValue(String id, String name, String... appliesTo) {
        this(id, name);
        if (appliesTo != null && appliesTo.length > 0) {
            this.appliesTo = new HashSet<>(appliesTo.length);
            for (String at : appliesTo) {
                this.appliesTo.add(at);
            }
        }
    }

    /**
     * Returns a set of value types this comparison operator is applicable to. Returns an empty set in case there are no type restrictions,
     * i.e. operator can be applied to any value type.
     * 
     * @return a set of value types this comparison operator is applicable to. Returns an empty set in case there are no type restrictions,
     *         i.e. operator can be applied to any value type
     */
    public Set<String> getAppliesTo() {
        return appliesTo;
    }

    @Override
    public long getPluginId() {
        return pluginId;
    }

    @Override
    public void setPluginId(long pluginId) {
        this.pluginId = pluginId;
    }
}
