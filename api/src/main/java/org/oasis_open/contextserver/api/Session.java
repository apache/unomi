package org.oasis_open.contextserver.api;

/*
 * #%L
 * context-server-api
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2014 - 2015 Jahia Solutions
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class Session extends Item implements TimestampedItem {

    private static final long serialVersionUID = 4628640198281687336L;

    public static final String ITEM_TYPE = "session";

    private String profileId;

    private Profile profile;

    private Map<String,Object> properties;

    private Date timeStamp;

    private String scope;

    private Date lastEventDate;

    private int size = 0;

    private int duration = 0;

    public Session() {
    }

    public Session(String itemId, Profile profile, Date timeStamp, String scope) {
        super(itemId);
        this.profile = profile;
        this.profileId = profile.getItemId();
        properties = new HashMap<String, Object>();
        this.timeStamp = timeStamp;
        this.scope = scope;
    }

    public String getProfileId() {
        return profileId;
    }

    public Profile getProfile() {
        return profile;
    }

    public void setProfile(Profile profile) {
        this.profileId = profile.getItemId();
        this.profile = profile;
    }

    public void setProperty(String name, Object value) {
        properties.put(name, value);
    }

    public Object getProperty(String name) {
        return properties.get(name);
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, Object> properties) {
        this.properties = properties;
    }

    public Date getTimeStamp() {
        return timeStamp;
    }

    public Date getLastEventDate() {
        return lastEventDate;
    }

    public void setLastEventDate(Date lastEventDate) {
        this.lastEventDate = lastEventDate;
        if (lastEventDate != null) {
            duration = (int) (lastEventDate.getTime() - timeStamp.getTime());
        }
    }

    public int getDuration() {
        return duration;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }
}
