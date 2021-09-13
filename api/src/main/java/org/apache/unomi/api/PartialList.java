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

import javax.xml.bind.annotation.XmlTransient;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * A list of elements representing a limited view of a larger list, starting from a given element (offset from the first) and showing only a given number of elements, instead of
 * showing all of them. This is useful to retrieve "pages" of large element collections.
 *
 * @param <T> the generic type of contained elements
 */
public class PartialList<T> implements Serializable {

    private static final long serialVersionUID = 2661946814840468260L;
    private List<T> list;
    private long offset;
    private long pageSize;
    private long totalSize;
    private Relation totalSizeRelation;
    private String scrollIdentifier = null;
    private String scrollTimeValidity = null;

    /**
     * This enum exists to replicate Lucene's total hits relation in a back-end agnostic way. Basically Lucene will
     * by default not report accurate total hit counts above a certain threshold for performance reasons. Using the
     * relation we can understand if we are in the case of an accurate hit or not.
     */
    public enum Relation {
        EQUAL,
        GREATER_THAN_OR_EQUAL_TO
    }

    /**
     * Instantiates a new PartialList.
     */
    public PartialList() {
        list = new ArrayList<>();
        offset = 0;
        pageSize = 0;
        totalSize = 0;
        totalSizeRelation = Relation.EQUAL;
    }

    /**
     * Instantiates a new PartialList.
     *
     * @param list      the limited view into the bigger List this PartialList is representing
     * @param offset    the offset of the first element in the view
     * @param pageSize  the number of elements this PartialList contains
     * @param totalSize the total size of elements in the original List
     * @param totalSizeRelation the relation to the total size (equals or greater than)
     */
    public PartialList(List<T> list, long offset, long pageSize, long totalSize, Relation totalSizeRelation) {
        this.list = list;
        this.offset = offset;
        this.pageSize = pageSize;
        this.totalSize = totalSize;
        this.totalSizeRelation = totalSizeRelation;
    }

    /**
     * Retrieves the limited list view.
     *
     * @return a List of the {@code size} elements starting from the {@code offset}-th one from the original, larger list
     */
    public List<T> getList() {
        return list;
    }

    /**
     * Sets the view list.
     *
     * @param list the view list into the bigger List this PartialList is representing
     */
    public void setList(List<T> list) {
        this.list = list;
    }

    /**
     * Retrieves the offset of the first element of the view.
     *
     * @return the offset of the first element of the view
     */
    public long getOffset() {
        return offset;
    }

    public void setOffset(long offset) {
        this.offset = offset;
    }

    /**
     * Retrieves the number of elements this PartialList contains.
     *
     * @return the number of elements this PartialList contains
     */
    public long getPageSize() {
        return pageSize;
    }

    public void setPageSize(long pageSize) {
        this.pageSize = pageSize;
    }

    /**
     * Retrieves the total size of elements in the original List.
     *
     * @return the total size of elements in the original List
     */
    public long getTotalSize() {
        return totalSize;
    }

    public void setTotalSize(long totalSize) {
        this.totalSize = totalSize;
    }

    /**
     * Retrieves the size of this PartialList. Should equal {@link #getPageSize()}.
     *
     * @return the size of this PartialList
     */
    @XmlTransient
    public int size() {
        return list.size();
    }

    /**
     * Retrieves the element at the specified index
     *
     * @param index the index of the element to retrieve
     * @return the element at the specified index
     */
    @XmlTransient
    public T get(int index) {
        return list.get(index);
    }

    /**
     * Retrieve the scroll identifier to make it possible to continue a scrolling list query
     * @return a string containing the scroll identifier, to be sent back in an subsequent request
     */
    public String getScrollIdentifier() {
        return scrollIdentifier;
    }

    public void setScrollIdentifier(String scrollIdentifier) {
        this.scrollIdentifier = scrollIdentifier;
    }

    /**
     * Retrieve the value of the scroll time validity to make it possible to continue a scrolling list query
     * @return a string containing a time value for the scroll validity, to be sent back in a subsequent request
     */
    public String getScrollTimeValidity() {
        return scrollTimeValidity;
    }

    public void setScrollTimeValidity(String scrollTimeValidity) {
        this.scrollTimeValidity = scrollTimeValidity;
    }

    /**
     * Retrieve the relation to the total site, wether it is equal to or greater than the value stored in the
     * totalSize property.
     * @return a Relation enum value that describes the type of total size we have in this object.
     */
    public Relation getTotalSizeRelation() {
        return totalSizeRelation;
    }

    public void setTotalSizeRelation(Relation totalSizeRelation) {
        this.totalSizeRelation = totalSizeRelation;
    }
}
