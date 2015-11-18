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

import javax.xml.bind.annotation.XmlRootElement;
import java.util.List;

/**
 * A set of conditions associated with a value to assign to {@link Profile}s when matching so that the associated users can be scored along that
 * dimension. Each {@link ScoringElement} is evaluated and matching profiles' scores are incremented with the associated value.
 */
@XmlRootElement
public class Scoring extends MetadataItem {
    /**
     * The Scoring ITEM_TYPE.
     *
     * @see Item for a discussion of ITEM_TYPE
     */
    public static final String ITEM_TYPE = "scoring";
    private static final long serialVersionUID = 6351058906259967559L;
    private List<ScoringElement> elements;

    /**
     * Instantiates a new Scoring.
     */
    public Scoring() {
    }

    /**
     * Instantiates a new Scoring with the specified metadata.
     *
     * @param metadata the metadata
     */
    public Scoring(Metadata metadata) {
        super(metadata);
    }

    /**
     * Retrieves the details of this Scoring.
     *
     * @return the elements
     */
    public List<ScoringElement> getElements() {
        return elements;
    }

    /**
     * Sets the elements.
     *
     * @param elements the elements
     */
    public void setElements(List<ScoringElement> elements) {
        this.elements = elements;
    }

}
