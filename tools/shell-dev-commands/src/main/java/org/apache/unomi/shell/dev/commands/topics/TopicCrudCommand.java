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
package org.apache.unomi.shell.dev.commands.topics;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.Topic;
import org.apache.unomi.api.query.Query;
import org.apache.unomi.api.services.TopicService;
import org.apache.unomi.persistence.spi.CustomObjectMapper;
import org.apache.unomi.shell.dev.services.BaseCrudCommand;
import org.apache.unomi.shell.dev.services.CrudCommand;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component(service = CrudCommand.class, immediate = true)
public class TopicCrudCommand extends BaseCrudCommand {

    private static final ObjectMapper OBJECT_MAPPER = new CustomObjectMapper();
    private static final List<String> PROPERTY_NAMES = List.of(
        "itemId", "name", "description", "parentId", "metadata"
    );

    @Reference
    private TopicService topicService;

    @Override
    public String getObjectType() {
        return "topic";
    }

    @Override
    public String[] getHeaders() {
        return new String[] {
            "Identifier",
            "Name",
            "Scope",
            "Tenant"
        };
    }

    @Override
    protected PartialList<?> getItems(Query query) {
        return topicService.search(query);
    }

    @Override
    protected String[] buildRow(Object item) {
        Topic topic = (Topic) item;
        return new String[] {
            topic.getItemId(),
            topic.getName(),
            topic.getScope(),
            topic.getTenantId()
        };
    }

    @Override
    public String create(Map<String, Object> properties) {
        Topic topic = OBJECT_MAPPER.convertValue(properties, Topic.class);
        topicService.save(topic);
        return topic.getItemId();
    }

    @Override
    public Map<String, Object> read(String id) {
        Topic topic = topicService.load(id);
        if (topic == null) {
            return null;
        }
        return OBJECT_MAPPER.convertValue(topic, Map.class);
    }

    @Override
    public void update(String id, Map<String, Object> properties) {
        properties.put("itemId", id);
        Topic topic = OBJECT_MAPPER.convertValue(properties, Topic.class);
        topicService.save(topic);
    }

    @Override
    public void delete(String id) {
        topicService.delete(id);
    }

    @Override
    public String getPropertiesHelp() {
        return String.join("\n",
            "Required properties:",
            "- itemId: Topic ID (string)",
            "- name: Topic name",
            "",
            "Optional properties:",
            "- description: Topic description",
            "- parentId: Parent topic ID",
            "- metadata: Topic metadata"
        );
    }

    @Override
    public List<String> completePropertyNames(String prefix) {
        return PROPERTY_NAMES.stream()
                .filter(name -> name.startsWith(prefix))
                .collect(Collectors.toList());
    }
}
