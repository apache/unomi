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

import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.MetadataItem;
import org.apache.unomi.api.Scope;
import org.apache.unomi.api.services.ScopeService;
import org.apache.unomi.graphql.types.input.CDPSourceInput;
import org.apache.unomi.graphql.types.output.CDPSource;

import java.util.Objects;

public class CreateOrUpdateSourceCommand extends BaseCommand<CDPSource> {

    private final CDPSourceInput sourceInput;

    private CreateOrUpdateSourceCommand(Builder builder) {
        super(builder);

        this.sourceInput = builder.sourceInput;
    }

    @Override
    public CDPSource execute() {
        ScopeService scopeService = serviceManager.getService(ScopeService.class);

        Scope scope = scopeService.getScope(sourceInput.getId());

        if (scope == null) {
            Metadata metadata = new Metadata();
            metadata.setId(sourceInput.getId());
            metadata.setScope(sourceInput.getId());
            scope = new Scope();
            scope.setMetadata(metadata);
        }

        scopeService.save(scope);

        return new CDPSource(scope.getItemId(), false);
    }

    public static Builder create(final CDPSourceInput topicInput) {
        return new Builder(topicInput);
    }

    public static class Builder extends BaseCommand.Builder<Builder> {

        final CDPSourceInput sourceInput;

        public Builder(CDPSourceInput sourceInput) {
            this.sourceInput = sourceInput;
        }

        @Override
        public void validate() {
            super.validate();

            Objects.requireNonNull(sourceInput, "Source can not be null");
            Objects.requireNonNull(sourceInput.getId(), "SourceID can not be null");
        }

        public CreateOrUpdateSourceCommand build() {
            validate();

            return new CreateOrUpdateSourceCommand(this);
        }

    }

}
