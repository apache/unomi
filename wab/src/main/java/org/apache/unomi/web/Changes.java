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

package org.apache.unomi.web;

import org.apache.unomi.api.Profile;

/**
 * This class is a simple object to get the updated profile without the need of reloading it
 *
 * @author dgaillard
 */
public class Changes {
    private int changeType;
    private int processedItems;
    private Profile profile;

    public Changes(int changeType, Profile profile) {
        this(changeType,0,profile);
    }

    public Changes(int changeType, int processedItems, Profile profile) {
        this.changeType = changeType;
        this.processedItems = processedItems;
        this.profile = profile;
    }

    public int getChangeType() {
        return changeType;
    }

    public int getProcessedItems() {
        return processedItems;
    }

    public Profile getProfile() {
        return profile;
    }
}
