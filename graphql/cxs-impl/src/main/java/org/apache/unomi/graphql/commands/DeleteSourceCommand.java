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

import org.apache.unomi.api.services.ScopeService;

import java.util.Objects;

public class DeleteSourceCommand extends BaseCommand<Boolean> {

    private final String sourceId;

    public DeleteSourceCommand(Builder builder) {
        super(builder);

        this.sourceId = builder.sourceId;
    }

    @Override
    public Boolean execute() {
        return serviceManager.getService(ScopeService.class).delete(sourceId);
    }

    public static Builder create(final String sourceId) {
        return new Builder(sourceId);
    }

    public static class Builder extends BaseCommand.Builder<Builder> {

        final String sourceId;

        public Builder(String sourceId) {
            this.sourceId = sourceId;
        }

        @Override
        public void validate() {
            super.validate();

            Objects.requireNonNull(sourceId, "SourceID can not be null");
        }

        public DeleteSourceCommand build() {
            validate();

            return new DeleteSourceCommand(this);
        }

    }

}
