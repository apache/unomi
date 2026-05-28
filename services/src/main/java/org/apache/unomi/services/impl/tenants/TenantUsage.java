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
package org.apache.unomi.services.impl.tenants;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TenantUsage {
    private long profileCount;
    private long eventCount;
    private long storageSize;
    private Map<String, Long> customUsage = new ConcurrentHashMap<>();

    public long getProfileCount() {
        return profileCount;
    }

    public void setProfileCount(long profileCount) {
        this.profileCount = profileCount;
    }

    public long getEventCount() {
        return eventCount;
    }

    public void setEventCount(long eventCount) {
        this.eventCount = eventCount;
    }

    public long getStorageSize() {
        return storageSize;
    }

    public void setStorageSize(long storageSize) {
        this.storageSize = storageSize;
    }

    public long getCustomUsage(String type) {
        return customUsage.getOrDefault(type, 0L);
    }

    public void setCustomUsage(String type, long value) {
        customUsage.put(type, value);
    }
}
