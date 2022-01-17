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

import java.util.List;

/**
 * A class to contain the result of a personalization, containing the list of content IDs as well as a changeType to
 * indicate if a profile and/or a session was modified (to store control group information).
 */
public class PersonalizationResult {

    List<String> contentIds;
    int changeType;

    public PersonalizationResult(List<String> contentIds, int changeType) {
        this.contentIds = contentIds;
        this.changeType = changeType;
    }

    public List<String> getContentIds() {
        return contentIds;
    }

    public int getChangeType() {
        return changeType;
    }
}
