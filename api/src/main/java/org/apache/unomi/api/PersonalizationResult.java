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
 * Outcome of a personalization request.
 * Returns matching content ids, optional extra metadata (such as control
 * group flags), and internal change codes when the resolved experience
 * updated the profile or session.
 */
public class PersonalizationResult implements Serializable  {

    /**
     * Key used in {@link #getAdditionalResultInfos()} to indicate if the
     * personalization result was generated while running in a control group.
     */
    public final static String ADDITIONAL_RESULT_INFO_IN_CONTROL_GROUP = "inControlGroup";

    /** Matching content identifiers for the resolved personalization. */
    List<String> contentIds;

    /** Extra key/value metadata returned to the client (for example control group flags). */
    Map<String, Object> additionalResultInfos = new HashMap<>();

    /** Internal change flags when resolution updated the profile or session. */
    int changeType = EventService.NO_CHANGE;

    /**
     * Constructs an empty PersonalizationResult with default values.
     */
    public PersonalizationResult() {
    }

    /**
     * Constructs a PersonalizationResult initialized with a
     * list of content IDs.
     * @param contentIds the list of matching ids for current personalization
     */
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
     * Sets the list of content IDs associated with this result.
     * This overwrites any previously set content IDs.
     * @param contentIds the new list of content IDs
     */
    public void setContentIds(List<String> contentIds) {
        this.contentIds = contentIds;
    }

    /**
     * Useful open map to return additional result information to the client
     * @return map of key/value pair for additional information, like: inControlGroup
     */
    public Map<String, Object> getAdditionalResultInfos() {
        return additionalResultInfos;
    }

    /**
     * Sets the map containing additional result information. This map is useful
     * for returning extra data to the client.
     * @param additionalResultInfos a map of key/value pair for additional
     * information, like: inControlGroup
     */
    public void setAdditionalResultInfos(Map<String, Object> additionalResultInfos) {
        this.additionalResultInfos = additionalResultInfos;
    }

    /**
     * Is the current personalization result in a control group ?
     * Control group are used to identify a profile or a session that should not get personalized results,
     * instead the current profile/session should get a specific result (usually the same for all peoples falling in control group)
     * Note: it's for now the responsibility of the client to decide what to do when the current personalization is under control group.
     * @return true in case current profile or session is in control group for the personalization.
     */
    @XmlTransient
    public boolean isInControlGroup() {
        return additionalResultInfos.containsKey(ADDITIONAL_RESULT_INFO_IN_CONTROL_GROUP) &&
                (Boolean) additionalResultInfos.get(ADDITIONAL_RESULT_INFO_IN_CONTROL_GROUP);
    }

    /**
     * Sets whether this personalization result belongs to a control group by
     * storing the boolean value in the internal additional result info map.
     * @param inControlGroup true if the current profile or session is in
     * control group for the
     * personalization, false otherwise
     */
    public void setInControlGroup(boolean inControlGroup) {
        this.additionalResultInfos.put(ADDITIONAL_RESULT_INFO_IN_CONTROL_GROUP, inControlGroup);
    }

    /**
     * Change code in case the personalization resolution modified the profile or the session
     * Only used internally, and will not be serialized either for storage or response payload.
     * @return change code
     */
    @XmlTransient
    public int getChangeType() {
        return changeType;
    }

    /**
     * Adds specified change flags to the current accumulated change type.
     * This method uses bitwise OR operation to ensure that multiple changes
     * are recorded without overwriting previous ones.
     * @param changes The change code or flag(s) to add to the
     * result's change type.
     */
    public void addChanges(int changes) {
        this.changeType |= changes;
    }
}
