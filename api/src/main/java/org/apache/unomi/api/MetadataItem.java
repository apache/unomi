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

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlTransient;

/**
 * A superclass for all {@link Item}s that bear {@link Metadata}.
 */
public abstract class MetadataItem extends Item {
    private static final long serialVersionUID = -2459510107927663510L;
    protected Metadata metadata;

    public MetadataItem() {
    }

    public MetadataItem(Metadata metadata) {
        super(metadata.getId());
        this.metadata = metadata;
    }

    /**
     * Retrieves the associated Metadata.
     *
     * @return the associated Metadata
     */
    @XmlElement(name = "metadata")
    public Metadata getMetadata() {
        return metadata;
    }

    public void setMetadata(Metadata metadata) {
        this.itemId = metadata.getId();
        this.metadata = metadata;
    }

    @XmlTransient
    public String getScope() {
        return metadata.getScope();
    }

}
