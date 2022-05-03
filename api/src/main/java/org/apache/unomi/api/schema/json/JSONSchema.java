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

package org.apache.unomi.api.schema.json;

import org.apache.unomi.api.PluginType;

import java.util.List;
import java.util.Map;

public class JSONSchema extends JSONType implements PluginType {

    private transient long pluginId;
    private String schemaId;
    private String target;
    private List<JSONType> rootTypes;

    private String vendor;
    private String name;
    private String version;

    private String extensionSchemaId;
    private String extensionOperator;

    public JSONSchema(Map<String, Object> schemaTree, JSONTypeFactory jsonTypeFactory) {
        super(schemaTree, jsonTypeFactory);
        schemaId = (String) schemaTree.get("$id");
        if (schemaTree.containsKey("self")) {
            Map<String, Object> self = (Map<String, Object>) schemaTree.get("self");
            name = (String) self.get("name");
            vendor = (String) self.get("vendor");
            version = (String) self.get("version");
            target = (String) self.get("target");
            if (self.containsKey("unomiExtends")) {
                Map<String,Object> unomiExtends = (Map<String,Object>) self.get("unomiExtends");
                extensionSchemaId = (String) unomiExtends.get("schemaId");
                extensionOperator = (String) unomiExtends.get("operator");
            }
        }
    }

    public String getVendor() {
        return vendor;
    }

    public void setVendor(String vendor) {
        this.vendor = vendor;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public long getPluginId() {
        return pluginId;
    }

    public void setPluginId(long pluginId) {
        this.pluginId = pluginId;
    }

    public String getSchemaId() {
        return schemaId;
    }

    public void setSchemaId(String schemaId) {
        this.schemaId = schemaId;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public String getExtensionSchemaId() {
        return extensionSchemaId;
    }

    public void setExtensionSchemaId(String extensionSchemaId) {
        this.extensionSchemaId = extensionSchemaId;
    }

    public String getExtensionOperator() {
        return extensionOperator;
    }

    public void setExtensionOperator(String extensionOperator) {
        this.extensionOperator = extensionOperator;
    }

    public List<JSONType> getRootTypes() {
        if (rootTypes == null) {
            buildRootTypes();
        }
        return rootTypes;
    }

    private void buildRootTypes() {
        rootTypes = jsonTypeFactory.getTypes(schemaTree);
    }
}
