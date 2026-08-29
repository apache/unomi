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

import org.apache.commons.lang3.StringUtils;
import org.apache.unomi.api.Topic;
import org.apache.unomi.api.services.TopicService;
import org.apache.unomi.graphql.types.input.CDPTopicInput;
import org.apache.unomi.graphql.types.output.CDPTopic;

import java.util.Objects;

public class CreateOrUpdateTopicCommand extends BaseCommand<CDPTopic> {

    private final CDPTopicInput topicInput;

    private CreateOrUpdateTopicCommand(Builder builder) {
        super(builder);

        this.topicInput = builder.topicInput;
    }

    @Override
    public CDPTopic execute() {
        final TopicService topicService = serviceManager.getService(TopicService.class);

        Topic topic = topicService.load(topicInput.getId());

        if (topic == null) {
            topic = new Topic();
        }

        final String topicId = StringUtils.isEmpty(topicInput.getId())
                ? topicInput.getName()
                : topicInput.getId();

        topic.setTopicId(topicId);
        topic.setItemId(topicId);
        topic.setName(topicInput.getName());
        topic.setScope(topicInput.getView());

        Topic storedTopic = topicService.save(topic);

        return new CDPTopic(storedTopic);
    }

    public static Builder create(final CDPTopicInput topicInput) {
        return new Builder(topicInput);
    }

    public static class Builder extends BaseCommand.Builder<Builder> {

        final CDPTopicInput topicInput;

        public Builder(CDPTopicInput topicInput) {
            this.topicInput = topicInput;
        }

        @Override
        public void validate() {
            super.validate();

            Objects.requireNonNull(topicInput, "Topic can not be null");
            Objects.requireNonNull(topicInput.getView(), "View can not be null");
            Objects.requireNonNull(topicInput.getName(), "Name can not be null");
        }

        public CreateOrUpdateTopicCommand build() {
            validate();

            return new CreateOrUpdateTopicCommand(this);
        }

    }

}
