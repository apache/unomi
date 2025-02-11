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

public class SimpleItem extends Item {
    private static final long serialVersionUID = 1L;
    public static final String ITEM_TYPE = "simpleItem";
    private String simpleProperty;
    private int numericProperty;
    private boolean booleanProperty;

    public SimpleItem() {
        super();
        setItemType(ITEM_TYPE);
        setScope("testScope");
    }

    public String getSimpleProperty() {
        return simpleProperty;
    }

    public void setSimpleProperty(String simpleProperty) {
        this.simpleProperty = simpleProperty;
    }

    public int getNumericProperty() {
        return numericProperty;
    }

    public void setNumericProperty(int numericProperty) {
        this.numericProperty = numericProperty;
    }

    public boolean isBooleanProperty() {
        return booleanProperty;
    }

    public void setBooleanProperty(boolean booleanProperty) {
        this.booleanProperty = booleanProperty;
    }
} 