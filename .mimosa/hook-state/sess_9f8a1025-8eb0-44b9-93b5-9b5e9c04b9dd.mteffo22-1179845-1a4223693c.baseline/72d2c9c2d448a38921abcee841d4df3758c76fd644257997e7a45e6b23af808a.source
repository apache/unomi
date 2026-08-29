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
package org.apache.unomi.graphql.commands.segments;

import org.apache.commons.lang3.StringUtils;
import org.apache.unomi.api.segments.DependentMetadata;
import org.apache.unomi.api.services.SegmentService;
import org.apache.unomi.graphql.commands.BaseCommand;

public class DeleteSegmentCommand extends BaseCommand<Boolean> {

    private final String segmentId;

    private DeleteSegmentCommand(Builder builder) {
        super(builder);

        this.segmentId = builder.segmentId;
    }

    @Override
    public Boolean execute() {
        final DependentMetadata dependentMetadata = serviceManager.getService(SegmentService.class).removeSegmentDefinition(segmentId, true);

        return dependentMetadata.getScorings().isEmpty() && dependentMetadata.getSegments().isEmpty();
    }

    public static Builder create(final String segmentId) {
        return new Builder(segmentId);
    }

    public static class Builder extends BaseCommand.Builder<Builder> {

        private final String segmentId;

        public Builder(String segmentId) {
            this.segmentId = segmentId;
        }

        @Override
        public void validate() {
            super.validate();

            if (StringUtils.isEmpty(segmentId)) {
                throw new IllegalArgumentException("The \"segmentID\" variable can not be null or empty");
            }
        }

        public DeleteSegmentCommand build() {
            validate();

            return new DeleteSegmentCommand(this);
        }

    }

}
