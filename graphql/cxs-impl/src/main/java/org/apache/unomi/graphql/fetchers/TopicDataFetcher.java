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

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import org.apache.unomi.api.Topic;
import org.apache.unomi.api.services.TopicService;
import org.apache.unomi.graphql.services.ServiceManager;
import org.apache.unomi.graphql.types.output.CDPTopic;

public class TopicDataFetcher implements DataFetcher<CDPTopic> {

    private final String topicId;

    public TopicDataFetcher(final String topicId) {
        this.topicId = topicId;
    }

    @Override
    public CDPTopic get(DataFetchingEnvironment environment) throws Exception {
        final ServiceManager serviceManager = environment.getContext();

        final TopicService topicService = serviceManager.getService(TopicService.class);

        final Topic topic = topicService.load(topicId);

        if (topic != null) {
            return new CDPTopic(topic);
        }

        return null;
    }

}
