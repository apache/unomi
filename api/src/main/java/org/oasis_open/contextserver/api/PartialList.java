package org.oasis_open.contextserver.api;

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

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a list that is actually a sub-set of a larger list.
 */
@XmlRootElement
public class PartialList<T> implements Serializable {

    private static final long serialVersionUID = 2661946814840468260L;
    private List<T> list;
    private long offset;
    private long pageSize;
    private long totalSize;

    public PartialList() {
        list = new ArrayList<T>();
        offset = 0;
        pageSize = 0;
        totalSize = 0;
    }

    public PartialList(List<T> list, long offset, long pageSize, long totalSize) {
        this.list = list;
        this.offset = offset;
        this.pageSize = pageSize;
        this.totalSize = totalSize;
    }

    public List<T> getList() {
        return list;
    }

    public void setList(List<T> list) {
        this.list = list;
    }

    public long getOffset() {
        return offset;
    }

    public void setOffset(long offset) {
        this.offset = offset;
    }

    public long getPageSize() {
        return pageSize;
    }

    public void setPageSize(long pageSize) {
        this.pageSize = pageSize;
    }

    public long getTotalSize() {
        return totalSize;
    }

    public void setTotalSize(long totalSize) {
        this.totalSize = totalSize;
    }

    @XmlTransient
    public int size() {
        return list.size();
    }

    @XmlTransient
    public T get(int index) {
        return list.get(index);
    }

}
