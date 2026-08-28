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

package org.apache.unomi.api;

import javax.xml.bind.annotation.XmlTransient;
import java.io.Serializable;

/**
 * Plugin declaration for a profile property merge strategy.
 * Merge strategies decide how conflicting property values are combined when
 * several updates apply to the same profile field (sum, latest value, etc.).
 */
public class PropertyMergeStrategyType implements PluginType, Serializable {

    private String id;
    private String filter;

    private long pluginId;

    /**
     * Merge strategy identifier.
     *
     * @return the strategy id
     */
    public String getId() {
        return id;
    }

    /**
     * Sets the merge strategy identifier.
     *
     * @param id the strategy id
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * OSGi LDAP filter that locates the executor for this strategy.
     * Format: {@code (propertyMergeStrategyExecutorId=<id>)} where {@code id} matches the
     * {@code propertyMergeStrategyExecutorId} service property in the Blueprint definition.
     *
     * @return the OSGi filter string
     */
    public String getFilter() {
        return filter;
    }

    /**
     * Sets the OSGi filter for locating the strategy executor.
     *
     * @param filter the OSGi filter string
     */
    public void setFilter(String filter) {
        this.filter = filter;
    }

    /**
     * Returns the OSGi bundle id of the plugin that registered this strategy.
     * Used internally when resolving merge strategy implementations.
     *
     * @return the plugin bundle id
     */
    @XmlTransient
    public long getPluginId() {
        return pluginId;
    }

    /**
     * Sets the OSGi bundle id of the plugin that registered this strategy.
     *
     * @param pluginId the plugin bundle id
     */
    public void setPluginId(long pluginId) {
        this.pluginId = pluginId;
    }

}
