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

package org.oasis_open.contextserver.api.segments;

import java.util.Map;
import java.util.Set;

/**
 * A combination of {@link Segment} and scores (usually associated with a {@link org.oasis_open.contextserver.api.Profile}).
 */
public class SegmentsAndScores {
    private Set<String> segments;
    private Map<String,Integer> scores;

    /**
     * Instantiates a new SegmentsAndScores.
     *
     * @param segments the set of segment identifiers
     * @param scores   the scores as a Map of scoring name - associated score pairs
     */
    public SegmentsAndScores(Set<String> segments, Map<String, Integer> scores) {
        this.segments = segments;
        this.scores = scores;
    }


    /**
     * Retrieves the segments identifiers.
     *
     * @return the segments identifiers
     */
    public Set<String> getSegments() {
        return segments;
    }

    /**
     * Retrieves the scores as a Map of scoring name - associated score pairs.
     *
     * @return the scores as a Map of scoring name - associated score pairs
     */
    public Map<String, Integer> getScores() {
        return scores;
    }
}
