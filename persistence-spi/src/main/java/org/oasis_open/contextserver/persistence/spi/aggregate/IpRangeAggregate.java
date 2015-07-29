package org.oasis_open.contextserver.persistence.spi.aggregate;

/*
 * #%L
 * context-server-persistence-spi
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

import org.oasis_open.contextserver.api.query.IpRange;

import java.util.List;

public class IpRangeAggregate extends BaseAggregate{
    public IpRangeAggregate(String field, List<IpRange> ranges) {
        super(field);
        this.ranges = ranges;
    }

    private List<IpRange> ranges;

    public List<IpRange> getRanges() {
        return ranges;
    }

    public void setRanges(List<IpRange> ranges) {
        this.ranges = ranges;
    }
}
