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

package org.apache.unomi.api.segments;

import org.apache.unomi.api.Item;
import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.MetadataItem;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.conditions.Condition;

/**
 * A dynamically evaluated group of similar profiles in order to categorize the associated users. To be considered part of a given segment, users must satisfies
 * the segment’s condition. If they match, users are automatically added to the segment. Similarly, if at any given point during, they cease to satisfy the segment’s condition,
 * they are automatically removed from it.
 */
public class Segment extends MetadataItem {

    /**
     * The Segment ITEM_TYPE.
     *
     * @see Item for a discussion of ITEM_TYPE
     */
    public static final String ITEM_TYPE = "segment";
    private static final long serialVersionUID = -1384533444860961296L;
    private Condition condition;

    /**
     * Instantiates a new Segment.
     */
    public Segment() {
    }

    /**
     * Instantiates a new Segment with the specified metadata.
     *
     * @param metadata the metadata
     */
    public Segment(Metadata metadata) {
        super(metadata);
    }

    /**
     * Retrieves the condition that users' {@link Profile} must satisfy in order to be considered member of this Segment.
     *
     * @return the condition that users must match
     */
    public Condition getCondition() {
        return condition;
    }

    /**
     * Sets the condition that users' {@link Profile} must satisfy in order to be considered member of this Segment.
     *
     * @param condition the condition that users must match
     */
    public void setCondition(Condition condition) {
        this.condition = condition;
    }

}
