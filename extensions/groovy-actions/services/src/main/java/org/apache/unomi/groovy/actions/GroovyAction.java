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
package org.apache.unomi.groovy.actions;

import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.MetadataItem;

/**
 * Object which represents a Groovy action (including its script)
 */
public class GroovyAction extends MetadataItem {
    public static final String ITEM_TYPE = "groovyAction";

    private String name;
    private String script;

    /**
     * Instantiates a new Groovy action.
     */
    public GroovyAction() {
    }

    public GroovyAction(String id, String name, String script) {
        super(new Metadata(id));
        this.itemId = id;
        this.name = name;
        this.script = script;

    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getScript() {
        return script;
    }

    public void setScript(String script) {
        this.script = script;
    }
}
