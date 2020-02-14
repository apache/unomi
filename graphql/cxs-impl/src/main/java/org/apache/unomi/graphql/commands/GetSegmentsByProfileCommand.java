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
import org.apache.unomi.api.Profile;
import org.apache.unomi.graphql.types.output.CDPSegment;

import java.util.List;
import java.util.stream.Collectors;

public class GetSegmentsByProfileCommand extends BaseCommand<List<CDPSegment>> {

    private final Profile profile;

    private GetSegmentsByProfileCommand(Builder builder) {
        super(builder);
        this.profile = builder.profile;
    }

    @Override
    public List<CDPSegment> execute() {
        final List<Metadata> metadata = serviceManager.getSegmentService().getSegmentMetadatasForProfile(profile);

        return metadata.stream().map(m -> CDPSegment.create().id(m.getId()).name(m.getName()).build()).collect(Collectors.toList());
    }

    public static Builder create(Profile profile) {
        return new Builder(profile);
    }

    public static class Builder extends BaseCommand.Builder<Builder> {

        private Profile profile;

        public Builder(Profile profile) {
            this.profile = profile;
        }

        public GetSegmentsByProfileCommand build() {
            return new GetSegmentsByProfileCommand(this);
        }
    }
}
