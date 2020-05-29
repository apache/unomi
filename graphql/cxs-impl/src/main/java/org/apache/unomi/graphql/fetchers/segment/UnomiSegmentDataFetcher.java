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
package org.apache.unomi.graphql.fetchers.segment;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import org.apache.unomi.api.segments.Segment;
import org.apache.unomi.graphql.services.ServiceManager;
import org.apache.unomi.graphql.types.output.UnomiSegment;

public class UnomiSegmentDataFetcher implements DataFetcher<UnomiSegment> {

    private final String segmentId;

    public UnomiSegmentDataFetcher(String segmentId) {
        this.segmentId = segmentId;
    }

    @Override
    public UnomiSegment get(final DataFetchingEnvironment environment) throws Exception {
        final ServiceManager serviceManager = environment.getContext();

        final Segment segment = serviceManager.getSegmentService().getSegmentDefinition(segmentId);

        if (segment != null) {
            return new UnomiSegment(segment);
        }

        return null;
    }

}
