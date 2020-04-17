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
package org.apache.unomi.graphql.commands.list;

import org.apache.unomi.graphql.commands.BaseCommand;

import java.util.Objects;

public class DeleteListCommand extends BaseCommand<Boolean> {

    private final String listId;

    private DeleteListCommand(final Builder builder) {
        super(builder);

        this.listId = builder.listId;
    }

    @Override
    public Boolean execute() {
        if (serviceManager.getUserListServiceExt().load(listId) == null) {
            return false;
        }

        serviceManager.getUserListServiceExt().delete(listId);

        return serviceManager.getUserListServiceExt().load(listId) == null;
    }

    public static Builder create(final String listId) {
        return new Builder(listId);
    }

    public static class Builder extends BaseCommand.Builder<Builder> {

        private final String listId;

        public Builder(final String listId) {
            this.listId = listId;
        }


        @Override
        public void validate() {
            super.validate();

            Objects.requireNonNull(listId, "The listID argument can not be null");
        }

        public DeleteListCommand build() {
            validate();

            return new DeleteListCommand(this);
        }

    }

}
