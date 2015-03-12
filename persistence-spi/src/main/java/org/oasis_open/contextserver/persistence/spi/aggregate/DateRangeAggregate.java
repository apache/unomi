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

import org.oasis_open.contextserver.api.query.GenericRange;

import java.util.List;

public class DateRangeAggregate extends BaseAggregate{
    public DateRangeAggregate(String field, List<GenericRange> ranges) {
        super(field);
        this.ranges = ranges;
    }

    public DateRangeAggregate(String field, String format, List<GenericRange> ranges) {
        super(field);
        this.format = format;
        this.ranges = ranges;
    }

    private String format;

    private List<GenericRange> ranges;

    public List<GenericRange> getRanges() {
        return ranges;
    }

    public void setRanges(List<GenericRange> ranges) {
        this.ranges = ranges;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }
}
