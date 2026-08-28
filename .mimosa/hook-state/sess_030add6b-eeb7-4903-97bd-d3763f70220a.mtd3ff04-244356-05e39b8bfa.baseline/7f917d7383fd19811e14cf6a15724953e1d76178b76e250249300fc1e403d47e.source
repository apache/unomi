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

import org.apache.unomi.api.Profile;

import java.io.Serializable;
import java.util.Map;
import java.util.Set;

/**
 * Segment memberships and scoring totals for one {@link org.apache.unomi.api.Profile}.
 * {@link org.apache.unomi.api.services.ProfileService} returns this snapshot when
 * clients need both current segment ids and per-scoring-plan scores in a single
 * response (for example after context resolution or profile reads).
 */
public class SegmentsAndScores implements Serializable {
    private Set<String> segments;
    private Map<String,Integer> scores;

    /**
     * Creates a snapshot with the given segment memberships and scores.
     *
     * @param segments segment identifiers
     * @param scores map of scoring name to score value
     */
    public SegmentsAndScores(Set<String> segments, Map<String, Integer> scores) {
        this.segments = segments;
        this.scores = scores;
    }

    /**
     * Segment ids the profile belongs to.
     *
     * @return segment identifiers
     */
    public Set<String> getSegments() {
        return segments;
    }

    /**
     * Scoring totals keyed by scoring plan name.
     *
     * @return map of scoring name to score value
     */
    public Map<String, Integer> getScores() {
        return scores;
    }
}
