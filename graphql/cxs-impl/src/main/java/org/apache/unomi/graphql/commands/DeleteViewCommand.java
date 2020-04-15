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
package org.apache.unomi.graphql.commands;

import java.util.Objects;

public class DeleteViewCommand extends BaseCommand<Boolean> {

    private final String viewId;

    public DeleteViewCommand(Builder builder) {
        super(builder);

        this.viewId = builder.viewId;
    }

    @Override
    public Boolean execute() {
        // Unomi doesn't have an API for that yet, so return a stub
        return true;
    }

    public static Builder create(final String viewId) {
        return new Builder(viewId);
    }

    public static class Builder extends BaseCommand.Builder<Builder> {

        final String viewId;

        public Builder(String viewId) {
            this.viewId = viewId;
        }

        @Override
        public void validate() {
            super.validate();

            Objects.requireNonNull(viewId, "ViewID can not be null");
        }

        public DeleteViewCommand build() {
            validate();

            return new DeleteViewCommand(this);
        }

    }


}
