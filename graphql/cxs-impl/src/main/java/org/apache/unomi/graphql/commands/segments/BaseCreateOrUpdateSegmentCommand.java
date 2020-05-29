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

import com.google.common.base.Strings;
import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.segments.Segment;
import org.apache.unomi.api.services.SegmentService;
import org.apache.unomi.graphql.commands.BaseCommand;
import org.apache.unomi.graphql.types.input.BaseSegmentInput;

public abstract class BaseCreateOrUpdateSegmentCommand<INPUT extends BaseSegmentInput, OUTPUT> extends BaseCommand<OUTPUT> {

    private final INPUT segmentInput;

    public BaseCreateOrUpdateSegmentCommand(Builder builder) {
        super(builder);

        this.segmentInput = (INPUT) builder.segmentInput;
    }

    protected Segment preparedSegmentWithoutCondition(final INPUT segmentInput) {
        final SegmentService segmentService = serviceManager.getSegmentService();

        final String segmentId = Strings.isNullOrEmpty(segmentInput.getId())
                ? segmentInput.getName()
                : segmentInput.getId();

        Segment segment = segmentService.getSegmentDefinition(segmentId);

        if (segment == null) {
            segment = new Segment();

            segment.setItemType(Segment.ITEM_TYPE);
            segment.setMetadata(createMedata(segmentId));
        } else {
            if (segment.getMetadata() == null) {
                segment.setMetadata(new Metadata());
            }

            segment.setItemId(segmentId);
            segment.getMetadata().setId(segmentId);
            segment.getMetadata().setName(segmentInput.getName());
            segment.getMetadata().setScope(segmentInput.getView());
        }

        return segment;
    }

    private Metadata createMedata(final String segmentId) {
        final Metadata metadata = new Metadata();

        metadata.setId(segmentId);
        metadata.setName(segmentInput.getName());
        metadata.setScope(segmentInput.getView());

        return metadata;
    }

    public static class Builder<INPUT extends BaseSegmentInput, B extends BaseCommand.Builder> extends BaseCommand.Builder<B> {

        private final INPUT segmentInput;

        public Builder(final INPUT segmentInput) {
            this.segmentInput = segmentInput;
        }

        public INPUT getSegmentInput() {
            return segmentInput;
        }

        @Override
        public void validate() {
            super.validate();

            if (segmentInput == null) {
                throw new IllegalArgumentException();
            }
            if (Strings.isNullOrEmpty(segmentInput.getName())) {
                throw new IllegalArgumentException();
            }
            if (Strings.isNullOrEmpty(segmentInput.getView())) {
                throw new IllegalArgumentException();
            }
        }
    }

}
