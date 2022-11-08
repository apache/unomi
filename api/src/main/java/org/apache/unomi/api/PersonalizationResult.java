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

import org.apache.unomi.api.services.EventService;

import javax.xml.bind.annotation.XmlTransient;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A class to contain the result of a personalization, containing the list of content IDs as well as a changeType to
 * indicate if a profile and/or a session was modified.
 */
public class PersonalizationResult implements Serializable  {

    public final static String ADDITIONAL_RESULT_INFO_IN_CONTROL_GROUP = "inControlGroup";

    List<String> contentIds;

    Map<String, Object> additionalResultInfos = new HashMap<>();

    int changeType = EventService.NO_CHANGE;

    public PersonalizationResult(List<String> contentIds) {
        this.contentIds = contentIds;
    }

    /**
     * List of matching ids for current personalization
     * @return the list of matching ids
     */
    public List<String> getContentIds() {
        return contentIds;
    }

    /**
     * Useful open map to return additional result information to the client
     * @return map of key/value pair for additional information, like: inControlGroup
     */
    public Map<String, Object> getAdditionalResultInfos() {
        return additionalResultInfos;
    }

    public void setAdditionalResultInfos(Map<String, Object> additionalResultInfos) {
        this.additionalResultInfos = additionalResultInfos;
    }

    /**
     * Is the current personalization result in a control group ?
     * Control group are used to identify a profile or a session that should not get personalized results,
     * instead the current profile/session should get a specific result (usually the same for all peoples falling in control group)
     * Note: it's for now the responsibility of the client to decide what to do when the current personalization is under control group.
     *
     * @return true in case current profile or session is in control group for the personalization.
     */
    @XmlTransient
    public boolean isInControlGroup() {
        return additionalResultInfos.containsKey(ADDITIONAL_RESULT_INFO_IN_CONTROL_GROUP) &&
                (Boolean) additionalResultInfos.get(ADDITIONAL_RESULT_INFO_IN_CONTROL_GROUP);
    }

    public void setInControlGroup(boolean inControlGroup) {
        this.additionalResultInfos.put(ADDITIONAL_RESULT_INFO_IN_CONTROL_GROUP, inControlGroup);
    }

    /**
     * Change code in case the personalization resolution modified the profile or the session
     * Only used internally, and will not be serialized either for storage or response payload.
     *
     * @return change code
     */
    @XmlTransient
    public int getChangeType() {
        return changeType;
    }

    public void addChanges(int changes) {
        this.changeType |= changes;
    }
}
