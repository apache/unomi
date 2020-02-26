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

import java.util.Date;

public class ConnectionParams {
    private int first;
    private int last;
    private Date after;
    private Date before;

    private ConnectionParams(final Builder builder) {
        first = builder.first;
        last = builder.last;
        after = builder.after;
        before = builder.before;
    }

    public int getFirst() {
        return first;
    }

    public int getLast() {
        return last;
    }

    public int getSize() {
        return last - first;
    }

    public Date getAfter() {
        return after;
    }

    public Date getBefore() {
        return before;
    }

    public static Builder create() {
        return new Builder();
    }

    public static class Builder {
        private int first;
        private int last;
        private Date after;
        private Date before;

        private Builder() {
        }

        public Builder first(int first) {
            this.first = first;
            return this;
        }

        public Builder last(int last) {
            this.last = last;
            return this;
        }

        public Builder after(Date after) {
            this.after = after;
            return this;
        }

        public Builder before(Date before) {
            this.before = before;
            return this;
        }

        public ConnectionParams build() {
            return new ConnectionParams(this);
        }
    }
}
