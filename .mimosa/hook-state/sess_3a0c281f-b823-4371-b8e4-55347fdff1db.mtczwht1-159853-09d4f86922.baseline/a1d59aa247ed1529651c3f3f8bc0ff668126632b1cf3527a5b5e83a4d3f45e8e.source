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

import org.apache.unomi.graphql.types.input.CDPViewInput;
import org.apache.unomi.graphql.types.output.CDPView;

import java.util.Objects;

public class CreateOrUpdateViewCommand extends BaseCommand<CDPView> {

    private final CDPViewInput viewInput;

    private CreateOrUpdateViewCommand(Builder builder) {
        super(builder);

        this.viewInput = builder.viewInput;
    }

    @Override
    public CDPView execute() {
        // Unomi doesn't have an API for that yet, so return a stub
        return new CDPView(viewInput.getName());
    }

    public static Builder create(final CDPViewInput viewInput) {
        return new Builder(viewInput);
    }

    public static class Builder extends BaseCommand.Builder<Builder> {

        final CDPViewInput viewInput;

        public Builder(CDPViewInput viewInput) {
            this.viewInput = viewInput;
        }

        @Override
        public void validate() {
            super.validate();

            Objects.requireNonNull(viewInput, "View can not be null");
            Objects.requireNonNull(viewInput.getName(), "Name can not be null");
        }

        public CreateOrUpdateViewCommand build() {
            validate();

            return new CreateOrUpdateViewCommand(this);
        }

    }

}
