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

package org.apache.unomi.graphql.fetchers;

public class ConnectionParams {
    private Integer first;
    private Integer last;
    private String after;
    private String before;

    public static int DEFAULT_PAGE_SIZE = 10;

    private ConnectionParams(final Builder builder) {
        first = builder.first;
        last = builder.last;
        after = builder.after;
        before = builder.before;

        if (first != null && before != null
                || last != null && after != null) {
            throw new IllegalArgumentException("Incorrect params: either 'first' and 'after' or 'last' and 'before' should be used simultaneously");
        }
    }

    public Integer getFirst() {
        return first;
    }

    public Integer getLast() {
        return last;
    }

    public int getSize() {
        if (first != null) {
            return first;
        } else if (last != null) {
            return last;
        }
        return DEFAULT_PAGE_SIZE;
    }

    public int getOffset() {
        if (after != null) {
            return parseInt(after, 0);
        } else if (before != null) {
            final int beforeInt = parseInt(before, -1);
            if (beforeInt > 0) {
                return beforeInt - (last != null ? last : DEFAULT_PAGE_SIZE);
            }
        }
        return 0;
    }

    private int parseInt(String after, int defaultValue) {
        try {
            return Integer.parseInt(after);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public String getAfter() {
        return after;
    }

    public String getBefore() {
        return before;
    }

    public static Builder create() {
        return new Builder();
    }

    public static class Builder {
        private Integer first;
        private Integer last;
        private String after;
        private String before;

        private Builder() {
        }

        public Builder first(Integer first) {
            this.first = first;
            return this;
        }

        public Builder last(Integer last) {
            this.last = last;
            return this;
        }

        public Builder after(String after) {
            this.after = after;
            return this;
        }

        public Builder before(String before) {
            this.before = before;
            return this;
        }

        public ConnectionParams build() {
            return new ConnectionParams(this);
        }
    }
}
