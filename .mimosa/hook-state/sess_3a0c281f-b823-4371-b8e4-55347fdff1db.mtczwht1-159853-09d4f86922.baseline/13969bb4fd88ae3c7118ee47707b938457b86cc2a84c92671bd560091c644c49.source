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

    /**
     * Segments that reference the given segment (for example via profileSegmentCondition).
     * @api.example [{"id":"premium-buyers","name":"Premium buyers","scope":"mysite","enabled":true}]
     */
    private List<Metadata> segments;

    /**
     * Scorings that reference the given segment.
     * @api.example [{"id":"premium-score","name":"Premium score","scope":"mysite","enabled":true}]
     */
    private List<Metadata> scorings;

    /**
     * Creates dependent metadata from segment and scoring definitions.
     *
     * @param segments metadata for dependent segments
     * @param scorings metadata for dependent scorings
     */
    public DependentMetadata(List<Metadata> segments, List<Metadata> scorings) {
        this.segments = segments;
        this.scorings = scorings;
    }

    /**
     * Metadata for segments that depend on the edited item.
     *
     * @return the dependent segment metadata
     * @api.example [{"id":"premium-buyers","name":"Premium buyers","scope":"mysite","enabled":true}]
     */
    public List<Metadata> getSegments() {
        return segments;
    }

    /**
     * Sets the dependent segment metadata.
     *
     * @param segments the segment metadata list
     */
    public void setSegments(List<Metadata> segments) {
        this.segments = segments;
    }

    /**
     * Metadata for scorings that depend on the edited item.
     *
     * @return the dependent scoring metadata
     * @api.example [{"id":"premium-score","name":"Premium score","scope":"mysite","enabled":true}]
     */
    public List<Metadata> getScorings() {
        return scorings;
    }

    /**
     * Sets the dependent scoring metadata.
     *
     * @param scorings the scoring metadata list
     */
    public void setScorings(List<Metadata> scorings) {
        this.scorings = scorings;
    }
}
