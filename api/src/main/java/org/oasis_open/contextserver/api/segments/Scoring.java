package org.oasis_open.contextserver.api.segments;

/*
 * #%L
 * context-server-api
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2014 - 2015 Jahia Solutions
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import org.oasis_open.contextserver.api.Item;
import org.oasis_open.contextserver.api.Metadata;
import org.oasis_open.contextserver.api.MetadataItem;

import javax.xml.bind.annotation.XmlRootElement;
import java.util.List;

/**
 * A set of conditions associated with a value to assign to profiles when matching so that they can be scored along that dimension. Each {@link ScoringElement} is evaluated and
 * the {@link org.oasis_open.contextserver.api.Profile}'s score is incremented with the associated value.
 */
@XmlRootElement
public class Scoring extends MetadataItem {
    private static final long serialVersionUID = 6351058906259967559L;

    /**
     * The Scoring ITEM_TYPE.
     *
     * @see Item for a discussion of ITEM_TYPE
     */
    public static final String ITEM_TYPE = "scoring";

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
