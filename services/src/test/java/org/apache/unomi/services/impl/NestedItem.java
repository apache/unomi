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
package org.apache.unomi.services.impl;

import org.apache.unomi.api.Item;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class NestedItem extends Item {
    private static final long serialVersionUID = 1L;
    public static final String ITEM_TYPE = "nestedItem";
    private Map<String, Object> nestedMap;
    private List<String> stringList;
    private Set<Map<String, Object>> complexSet;

    public NestedItem() {
        super();
        setItemType(ITEM_TYPE);
        setScope("testScope");
    }

    public Map<String, Object> getNestedMap() {
        return nestedMap;
    }

    public void setNestedMap(Map<String, Object> nestedMap) {
        this.nestedMap = nestedMap;
    }

    public List<String> getStringList() {
        return stringList;
    }

    public void setStringList(List<String> stringList) {
        this.stringList = stringList;
    }

    public Set<Map<String, Object>> getComplexSet() {
        return complexSet;
    }

    public void setComplexSet(Set<Map<String, Object>> complexSet) {
        this.complexSet = complexSet;
    }
}
