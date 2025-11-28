/*
 * Copyright (C) 2002-2025 Jahia Solutions Group SA. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.unomi.shell.services.internal;

import java.util.Date;
import java.util.Dictionary;
import java.util.Objects;

/**
 * @author Jerome Blanchard
 */
public class UnomiSetup {

    private String date;
    private String distribution;

    public UnomiSetup() {
    }

    public String getDate() {
        return date;
    }

    private void setDate(String date) {
        this.date = date;
    }

    public String getDistribution() {
        return distribution;
    }

    public void setDistribution(String distribution) {
        this.distribution = distribution;
    }

    public UnomiSetup withDistribution(String distribution) {
        this.distribution = distribution;
        return this;
    }

    public Dictionary<String, Object> toProperties() {
        Dictionary<String, Object> properties = new java.util.Hashtable<>();
        properties.put("unomi.setup.date", date);
        properties.put("unomi.setup.distribution", distribution);
        return properties;
    }

    public static UnomiSetup init() {
        UnomiSetup setup = new UnomiSetup();
        setup.setDate(new Date().toString());
        return setup;
    }

    public static UnomiSetup fromDictionary(Dictionary<String, Object> properties) {
        UnomiSetup setup = new UnomiSetup();
        if (properties == null) {
            return null;
        }
        setup.setDate(Objects.toString(properties.get("unomi.setup.date"), null));
        setup.setDistribution(Objects.toString(properties.get("unomi.setup.distribution"), null));
        return setup;
    }

}
