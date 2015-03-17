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

import org.oasis_open.contextserver.api.Metadata;
import org.oasis_open.contextserver.api.MetadataItem;

import javax.xml.bind.annotation.XmlRootElement;
import java.util.List;

@XmlRootElement
public class Scoring extends MetadataItem {
    private static final long serialVersionUID = 6351058906259967559L;

    public static final String ITEM_TYPE = "scoring";

    private List<ScoringElement> elements;

    public Scoring() {
    }

    public Scoring(Metadata metadata) {
        super(metadata);
    }

    public List<ScoringElement> getElements() {
        return elements;
    }

    public void setElements(List<ScoringElement> elements) {
        this.elements = elements;
    }

}
