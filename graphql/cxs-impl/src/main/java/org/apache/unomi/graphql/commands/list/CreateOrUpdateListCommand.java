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

import com.google.common.base.Strings;
import org.apache.unomi.api.Metadata;
import org.apache.unomi.graphql.commands.BaseCommand;
import org.apache.unomi.graphql.converters.UserListConverter;
import org.apache.unomi.graphql.types.input.CDPListInput;
import org.apache.unomi.graphql.types.output.CDPList;
import org.apache.unomi.lists.UserList;
import org.apache.unomi.services.UserListService;

import java.util.Objects;

public class CreateOrUpdateListCommand extends BaseCommand<CDPList> {

    private final CDPListInput listInput;

    private CreateOrUpdateListCommand(Builder builder) {
        super(builder);

        this.listInput = builder.listInput;
    }

    @Override
    public CDPList execute() {
        final UserListService userListService = serviceManager.getService(UserListService.class);

        final String listId = Strings.isNullOrEmpty(listInput.getId())
                ? listInput.getName()
                : listInput.getId();

        UserList userList = userListService.load(listId);

        if (userList == null) {
            final Metadata metadata = new Metadata();
            metadata.setId(listId);

            userList = new UserList();
            userList.setItemType(UserList.ITEM_TYPE);
            userList.setMetadata(metadata);
        }

        userList.setScope(listInput.getView());
        userList.getMetadata().setName(listInput.getName());
        userList.getMetadata().setScope(listInput.getView());

        userListService.save(userList);

        return new CDPList(UserListConverter.convertToUnomiList(userList));
    }

    public static Builder create(final CDPListInput listInput) {
        return new Builder(listInput);
    }

    public static class Builder extends BaseCommand.Builder<Builder> {

        private final CDPListInput listInput;

        public Builder(final CDPListInput listInput) {
            this.listInput = listInput;
        }

        @Override
        public void validate() {
            super.validate();

            Objects.requireNonNull(listInput, "The list argument can not be null");
            Objects.requireNonNull(listInput.getName(), "The \"name\" field can not be null");
            Objects.requireNonNull(listInput.getView(), "The \"view\" field can not be null");
        }

        public CreateOrUpdateListCommand build() {
            validate();

            return new CreateOrUpdateListCommand(this);
        }

    }

}
