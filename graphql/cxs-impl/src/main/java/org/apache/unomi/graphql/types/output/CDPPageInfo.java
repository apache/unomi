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
package org.apache.unomi.graphql.types.output;

import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;

@GraphQLName("CDP_PageInfo")
public class CDPPageInfo {

    @GraphQLField
    private boolean hasPreviousPage;

    @GraphQLField
    private boolean hasNextPage;

    @GraphQLField
    private Long totalSize;

    public CDPPageInfo() {
        this(false, false, 0L);
    }

    public CDPPageInfo(boolean hasPreviousPage, boolean hasNextPage, Long totalSize) {
        this.hasPreviousPage = hasPreviousPage;
        this.hasNextPage = hasNextPage;
        this.totalSize = totalSize;
    }

    public boolean isHasPreviousPage() {
        return hasPreviousPage;
    }

    public CDPPageInfo setHasPreviousPage(boolean hasPreviousPage) {
        this.hasPreviousPage = hasPreviousPage;
        return this;
    }

    public boolean isHasNextPage() {
        return hasNextPage;
    }

    public CDPPageInfo setHasNextPage(boolean hasNextPage) {
        this.hasNextPage = hasNextPage;
        return this;
    }

    public Long getTotalSize() {
        return totalSize;
    }
}
