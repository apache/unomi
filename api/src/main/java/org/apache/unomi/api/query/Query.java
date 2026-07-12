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

package org.apache.unomi.api.query;

import org.apache.unomi.api.conditions.Condition;

import java.io.Serializable;

/**
 * Search and paging request sent to {@link org.apache.unomi.api.services.QueryService}
 * and profile/segment REST endpoints.
 * Combines an optional full-text filter, a {@link Condition}, sort field, offset/limit,
 * and a {@code forceRefresh} flag that controls whether indexes are refreshed first.
 */
public class Query implements Serializable {
    private String text;
    private int offset;
    private int limit = Integer.MIN_VALUE;
    private String sortby;
    private Condition condition;
    private boolean forceRefresh;
    private String scrollTimeValidity;
    private String scrollIdentifier;

    /**
     * Default constructor.
     */
    public Query() {
    }

    /**
     * Optional full-text filter applied to the search.
     *
     * @return the search text, or {@code null} if none is set
     */
    public String getText() {
        return text;
    }

    /**
     * Sets the full-text filter.
     *
     * @param text the search text, or {@code null} to disable full-text search
     */
    public void setText(String text) {
        this.text = text;
    }

    /**
     * Zero-based index of the first result to return.
     *
     * @return the result offset
     */
    public int getOffset() {
        return offset;
    }

    /**
     * Sets the zero-based result offset.
     *
     * @param offset the first result index
     */
    public void setOffset(int offset) {
        this.offset = offset;
    }

    /**
     * Maximum number of results to return, or {@code -1} for all matches.
     *
     * @return the result limit
     */
    public int getLimit() {
        return limit;
    }

    /**
     * Sets the maximum number of results to return.
     *
     * @param limit the result limit, or {@code -1} for all matches
     */
    public void setLimit(int limit) {
        this.limit = limit;
    }

    /**
     * Sort specification as a comma-separated property list.
     * Each property may be followed by {@code :asc} or {@code :desc}.
     *
     * @return the sort specification, or {@code null} if unsorted
     */
    public String getSortby() {
        return sortby;
    }

    /**
     * Sets the sort specification.
     * See {@link #getSortby()} for the expected format.
     *
     * @param sortby the sort specification, or {@code null} for no sorting
     */
    public void setSortby(String sortby) {
        this.sortby = sortby;
    }

    /**
     * Structured filter condition for the query.
     *
     * @return the condition, or {@code null} if none is set
     */
    public Condition getCondition() {
        return condition;
    }

    /**
     * Sets the structured filter condition.
     *
     * @param condition the condition
     */
    public void setCondition(Condition condition) {
        this.condition = condition;
    }

    /**
     * Whether the search index should be refreshed before executing the query.
     *
     * @return {@code true} to force a refresh, {@code false} otherwise
     */
    public boolean isForceRefresh() {
        return forceRefresh;
    }

    /**
     * Sets whether to refresh the index before executing the query.
     *
     * @param forceRefresh {@code true} to force a refresh
     */
    public void setForceRefresh(boolean forceRefresh) {
        this.forceRefresh = forceRefresh;
    }

    /**
     * Scroll token for continuing a deep result set query.
     *
     * @return the scroll identifier, or {@code null} if scrolling is not active
     */
    public String getScrollIdentifier() {
        return scrollIdentifier;
    }

    /**
     * Sets the scroll token for continuing a scroll query.
     *
     * @param scrollIdentifier the scroll identifier, or {@code null} to clear it
     */
    public void setScrollIdentifier(String scrollIdentifier) {
        this.scrollIdentifier = scrollIdentifier;
    }

    /**
     * How long the scroll context remains valid (for example {@code 10m}).
     *
     * @return the scroll validity period, or {@code null} if not set
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

}
