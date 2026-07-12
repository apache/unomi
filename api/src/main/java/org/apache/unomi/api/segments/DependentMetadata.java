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

import org.apache.unomi.api.Metadata;

import java.io.Serializable;
import java.util.List;

/**
 * Holds segment and scoring definitions that depend on each other.
 * Used when exporting or editing related segment/scoring metadata as one
 * unit in the segmentation UI.
 */
public class DependentMetadata implements Serializable {

    private List<Metadata> segments;

    private List<Metadata> scorings;

    /**
     * Constructs a {@link DependentMetadata} instance with specified
     * segment and scoring lists.
     * @param segments the list of metadata defining required segments.
     * @param scorings the list of metadata defining associated scores.
     */
    public DependentMetadata(List<Metadata> segments, List<Metadata> scorings) {
        this.segments = segments;
        this.scorings = scorings;
    }

    /**
     * Retrieves the list of metadata representing the dependent segments.
     * @return a {@link List<Metadata>} containing the segment definitions.
     */
    public List<Metadata> getSegments() {
        return segments;
    }

    /**
     * Sets the list of metadata defining the required segments for this object.
     * @param segments the new list of segment metadata.
     */
    public void setSegments(List<Metadata> segments) {
        this.segments = segments;
    }

    /**
     * Retrieves the list of metadata representing the dependent scorings.
     * @return a {@link List<Metadata>} containing the scoring definitions.
     */
    public List<Metadata> getScorings() {
        return scorings;
    }

    /**
     * Sets the list of metadata defining the associated scores for this object.
     * @param scorings the new list of score metadata.
     */
    public void setScorings(List<Metadata> scorings) {
        this.scorings = scorings;
    }
}
