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
 * A query wrapper gathering all elements needed for a potentially complex CXS query: {@link Condition}, offset, limit, sorting specification, etc.
 *
 * Created by kevan on 14/05/15.
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
     * Instantiates a new Query.
     */
    public Query() {
    }

    /**
     * Retrieves the text to be used in full-text searches, if any.
     *
     * @return the text to be used in full-text searches or {@code null} if no full-text search is needed for this Query
     */
    public String getText() {
        return text;
    }

    /**
     * Sets to be used in full-text searches
     *
     * @param text the text to be used in full-text searches or {@code null} if no full-text search is needed for this Query
     */
    public void setText(String text) {
        this.text = text;
    }


    /**
     * Retrieves the offset of the first element to be retrieved
     *
     * @return zero or a positive integer specifying the position of the first item to be retrieved in the total ordered collection of matching items
     */
    public int getOffset() {
        return offset;
    }

    /**
     * Sets the offset of the first element to be retrieved
     *
     * @param offset zero or a positive integer specifying the position of the first item to be retrieved in the total ordered collection of matching items
     */
    public void setOffset(int offset) {
        this.offset = offset;
    }

    /**
     * Retrieves the number of elements to retrieve.
     *
     * @return a positive integer specifying how many matching items should be retrieved or {@code -1} if all of them should be retrieved
     */
    public int getLimit() {
        return limit;
    }

    /**
     * Sets the number of elements to retrieve.
     *
     * @param limit a positive integer specifying how many matching items should be retrieved or {@code -1} if all of them should be retrieved
     */
    public void setLimit(int limit) {
        this.limit = limit;
    }

    /**
     * Retrieves the sorting specifications for this Query in String format, if any.
     *
     * @return an optional ({@code null} if no sorting is required) String of comma ({@code ,}) separated property names on which ordering should be performed, ordering elements
     * according to the property order in the String, considering each in turn and moving on to the next one in case of equality of all preceding ones. Each property name is
     * optionally followed by a column ({@code :}) and an order specifier: {@code asc} or {@code desc}.
     */
    public String getSortby() {
        return sortby;
    }

    /**
     * Sets the String representation of the sorting specifications for this Query if any. See {@link #getSortby()} method documentation for format.
     *
     * @param sortby the String representation of the sorting specifications for this Query or {@code null} if no sorting is required
     */
    public void setSortby(String sortby) {
        this.sortby = sortby;
    }

    /**
     * Retrieves the {@link Condition} associated with this Query.
     *
     * @return the {@link Condition} associated with this Query
     */
    public Condition getCondition() {
        return condition;
    }

    /**
     * Sets the {@link Condition} associated with this Query.
     *
     * @param condition the {@link Condition} associated with this Query
     */
    public void setCondition(Condition condition) {
        this.condition = condition;
    }

    /**
     * Determines whether or not an index refresh is needed before performing this Query
     *
     * @return {@code true} if an index refresh is needed before performing this Query, {@code false} otherwise
     */
    public boolean isForceRefresh() {
        return forceRefresh;
    }

    /**
     * Specifies whether or not an index refresh is needed before performing this Query.
     *
     * @param forceRefresh {@code true} if an index refresh is needed before performing this Query, {@code false} otherwise
     */
    public void setForceRefresh(boolean forceRefresh) {
        this.forceRefresh = forceRefresh;
    }

    public String getScrollIdentifier() {
        return scrollIdentifier;
    }

    public void setScrollIdentifier(String scrollIdentifier) {
        this.scrollIdentifier = scrollIdentifier;
    }

    public String getScrollTimeValidity() {
        return scrollTimeValidity;
    }

    public void setScrollTimeValidity(String scrollTimeValidity) {
        this.scrollTimeValidity = scrollTimeValidity;
    }

}
