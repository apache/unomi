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

public class DependentMetadata implements Serializable {

    private List<Metadata> segments;

    private List<Metadata> scorings;

    public DependentMetadata(List<Metadata> segments, List<Metadata> scorings) {
        this.segments = segments;
        this.scorings = scorings;
    }

    public List<Metadata> getSegments() {
        return segments;
    }

    public void setSegments(List<Metadata> segments) {
        this.segments = segments;
    }

    public List<Metadata> getScorings() {
        return scorings;
    }

    public void setScorings(List<Metadata> scorings) {
        this.scorings = scorings;
    }
}
