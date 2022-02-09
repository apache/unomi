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
package org.apache.unomi.services.impl.personalization;

import org.apache.unomi.persistence.spi.CustomObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.ParseException;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents a personalization control group, stored in a profile and/or a session
 */
public class ControlGroup {

    private static final Logger logger = LoggerFactory.getLogger(ControlGroup.class.getName());

    String id;
    String displayName;
    String path;
    Date timeStamp;

    public ControlGroup(String id, String displayName, String path, Date timeStamp) {
        this.id = id;
        this.displayName = displayName;
        this.path = path;
        this.timeStamp = timeStamp;
    }

    public static ControlGroup fromMap(Map<String,Object> map) {
        String id = (String) map.get("id");
        String displayName = (String) map.get("displayName");
        String path = (String) map.get("path");
        String dateStr = (String) map.get("timeStamp");
        Date date = null;
        try {
            date = CustomObjectMapper.getObjectMapper().getDateFormat().parse(dateStr);
        } catch (ParseException e) {
            logger.error("Error parsing control group date", e);
        }
        return new ControlGroup(id, displayName, path, date);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public Date getTimeStamp() {
        return timeStamp;
    }

    public void setTimeStamp(Date timeStamp) {
        this.timeStamp = timeStamp;
    }

    public Map<String,Object> toMap() {
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("displayName", displayName);
        result.put("path", path);
        result.put("timeStamp", CustomObjectMapper.getObjectMapper().getDateFormat().format(timeStamp));
        return result;
    }
}
