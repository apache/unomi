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

package org.apache.unomi.graphql.fetchers;

import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.Profile;
import org.apache.unomi.graphql.types.output.CDPPageInfo;
import org.apache.unomi.graphql.types.output.CDPProfileConnection;
import org.apache.unomi.graphql.types.output.CDPProfileEdge;

import java.util.List;
import java.util.stream.Collectors;

public abstract class ProfileConnectionDataFetcher extends BaseConnectionDataFetcher<CDPProfileConnection> {

    protected CDPProfileConnection createProfileConnection(PartialList<Profile> profiles) {
        final List<CDPProfileEdge> eventEdges = profiles.getList().stream()
                .map(CDPProfileEdge::new)
                .collect(Collectors.toList());
        final CDPPageInfo pageInfo = new CDPPageInfo(profiles.getOffset() > 0, profiles.getTotalSize() > profiles.getList().size());

        return new CDPProfileConnection(eventEdges, pageInfo);
    }
}
