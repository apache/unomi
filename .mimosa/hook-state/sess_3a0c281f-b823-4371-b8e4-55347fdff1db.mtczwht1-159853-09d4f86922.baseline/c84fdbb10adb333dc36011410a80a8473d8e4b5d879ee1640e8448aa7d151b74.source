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
 * Window into a larger result set for paginated queries.
 * Carries the current page, offset, total hit count, and optional scroll
 * continuation tokens for deep result sets.
 *
 * @param <T> the element type
 */
public class PartialList<T> implements Serializable {

    private static final long serialVersionUID = 2661946814840468260L;
    /**
     * Elements in the current page.
     */
    private List<T> list;
    /**
     * Zero-based index of the first element of this page in the full result set.
     * @api.example 0
     */
    private long offset;
    /**
     * Number of elements in this page.
     * @api.example 20
     */
    private long pageSize;
    /**
     * Total number of matching elements (exact or lower bound; see {@link #totalSizeRelation}).
     * @api.example 120
     */
    private long totalSize;
    /**
     * Whether {@link #totalSize} is exact ({@code EQUAL}) or a lower bound ({@code GREATER_THAN_OR_EQUAL_TO}).
     * @api.example EQUAL
     */
    private Relation totalSizeRelation;
    /**
     * Scroll identifier for continuing a deep scroll query, when applicable.
     * @api.example DXF1ZXJ5QW5kRmV0Y2gBAAAAAAAAAD4WYmRUMAkwZGY=
     */
    private String scrollIdentifier = null;
    /**
     * Scroll keep-alive window associated with {@link #scrollIdentifier}.
     * @api.example 10m
     */
    private String scrollTimeValidity = null;

    /**
     * This enum exists to replicate Lucene's total hits relation in a back-end agnostic way. Basically Lucene will
     * by default not report accurate total hit counts above a certain threshold for performance reasons. Using the
     * relation we can understand if we are in the case of an accurate hit or not.
     */
    public enum Relation {
        /** Total size equals the reported value exactly. */
        EQUAL,
        /** Total size is at least the reported value (approximate lower bound). */
        GREATER_THAN_OR_EQUAL_TO
    }

    /**
     * Default constructor with an empty list and zero counts.
     */
    public PartialList() {
        list = new ArrayList<>();
        offset = 0;
        pageSize = 0;
        totalSize = 0;
        totalSizeRelation = Relation.EQUAL;
    }

    /**
     * Creates a partial list view over a full result set.
     *
     * @param list              the page of elements
     * @param offset            index of the first element in the full set
     * @param pageSize          number of elements in this page
     * @param totalSize         total elements in the full set
     * @param totalSizeRelation whether {@code totalSize} is exact or a lower bound
     */
    public PartialList(List<T> list, long offset, long pageSize, long totalSize, Relation totalSizeRelation) {
        this.list = list;
        this.offset = offset;
        this.pageSize = pageSize;
        this.totalSize = totalSize;
        this.totalSizeRelation = totalSizeRelation;
    }

    /**
     * Elements in the current page.
     *
     * @return the page contents
     */
    public List<T> getList() {
        return list;
    }

    /**
     * Sets the page contents.
     *
     * @param list the elements for this page
     */
    public void setList(List<T> list) {
        this.list = list;
    }

    /**
     * Index of the first element in the full result set.
     *
     * @return the offset
     */
    public long getOffset() {
        return offset;
    }

    /**
     * Sets the offset of the first element in the full result set.
     *
     * @param offset the starting index
     */
    public void setOffset(long offset) {
        this.offset = offset;
    }

    /**
     * Maximum number of elements requested for this page.
     *
     * @return the page size
     */
    public long getPageSize() {
        return pageSize;
    }

    /**
     * Sets the page size.
     *
     * @param pageSize the maximum number of elements per page
     */
    public void setPageSize(long pageSize) {
        this.pageSize = pageSize;
    }

    /**
     * Total number of matching elements in the full result set.
     *
     * @return the total size
     */
    public long getTotalSize() {
        return totalSize;
    }

    /**
     * Sets the total number of matching elements.
     *
     * @param totalSize the total size
     */
    public void setTotalSize(long totalSize) {
        this.totalSize = totalSize;
    }

    /**
     * Number of elements in the current page (should match {@link #getPageSize()}).
     *
     * @return the number of elements in {@link #getList()}
     */
    @XmlTransient
    public int size() {
        return list.size();
    }

    /**
     * Element at the given index within the current page.
     *
     * @param index the zero-based index in the page list
     * @return the element at that index
     */
    @XmlTransient
    public T get(int index) {
        return list.get(index);
    }

    /**
     * Scroll token returned by the search backend for the next page of a scroll query.
     *
     * @return the scroll identifier, or {@code null} if scrolling is not in use
     */
    public String getScrollIdentifier() {
        return scrollIdentifier;
    }

    /**
     * Sets the scroll token for continuing a scroll query.
     *
     * @param scrollIdentifier the scroll identifier
     */
    public void setScrollIdentifier(String scrollIdentifier) {
        this.scrollIdentifier = scrollIdentifier;
    }

    /**
     * How long the scroll context remains valid (for example {@code 10m}).
     *
     * @return the scroll time validity, or {@code null} if not set
     */
    public String getScrollTimeValidity() {
        return scrollTimeValidity;
    }

    /**
     * Sets how long the scroll context remains valid.
     *
     * @param scrollTimeValidity the validity period (for example {@code 10m})
     */
    public void setScrollTimeValidity(String scrollTimeValidity) {
        this.scrollTimeValidity = scrollTimeValidity;
    }

    /**
     * Whether {@link #getTotalSize()} is exact or only a lower bound.
     *
     * @return the total-size relation
     */
    public Relation getTotalSizeRelation() {
        return totalSizeRelation;
    }

    /**
     * Sets whether the reported total size is exact or a lower bound.
     *
     * @param totalSizeRelation the total-size relation
     */
    public void setTotalSizeRelation(Relation totalSizeRelation) {
        this.totalSizeRelation = totalSizeRelation;
    }
}
