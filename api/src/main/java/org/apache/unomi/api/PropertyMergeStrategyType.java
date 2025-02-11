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
 * A unomi plugin that defines a new property merge strategy.
 */
public class PropertyMergeStrategyType implements PluginType, Serializable {

    private String id;
    private String filter;

    private long pluginId;

    /**
     * Retrieves the identifier for this PropertyMergeStrategyType.
     *
     * @return the identifier for this PropertyMergeStrategyType
     */
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    /**
     * Retrieves the OSGi filter used to identify the implementation associated with this PropertyMergeStrategyType. Filters take the following form:
     * {@code (propertyMergeStrategyExecutorId=&lt;id&gt;)} where {@code id} corresponds to the value of the {@code propertyMergeStrategyExecutorId} service property in the
     * Blueprint service definition for this PropertyMergeStrategyType.
     *
     * @return the filter string used to identify the implementation associated with this PropertyMergeStrategyType
     */
    public String getFilter() {
        return filter;
    }

    public void setFilter(String filter) {
        this.filter = filter;
    }

    @XmlTransient
    public long getPluginId() {
        return pluginId;
    }

    public void setPluginId(long pluginId) {
        this.pluginId = pluginId;
    }

}
