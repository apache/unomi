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
package org.apache.unomi.shell.dev.commands.segments;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.query.Query;
import org.apache.unomi.api.segments.Segment;
import org.apache.unomi.api.services.SegmentService;
import org.apache.unomi.shell.dev.services.BaseCrudCommand;
import org.apache.unomi.shell.dev.services.CrudCommand;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component(service = CrudCommand.class, immediate = true)
public class SegmentCrudCommand extends BaseCrudCommand {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final List<String> PROPERTY_NAMES = List.of(
        "itemId", "name", "description", "scope", "condition", "metadata"
    );
    private static final List<String> CONDITION_TYPES = List.of(
        "booleanCondition", "profilePropertyCondition", "sessionPropertyCondition", "eventPropertyCondition",
        "pastEventCondition", "matchAllCondition", "notCondition", "orCondition", "andCondition",
        "profileSegmentCondition", "scoringCondition"
    );

    @Reference
    private SegmentService segmentService;

    @Override
    public String getObjectType() {
        return "segment";
    }

    @Override
    public String[] getHeaders() {
        return new String[] {
            "Activated",
            "Hidden",
            "Read-only",
            "Identifier",
            "Scope",
            "Name",
            "Tags",
            "System tags"
        };
    }

    @Override
    protected PartialList<?> getItems(Query query) {
        return segmentService.getSegmentMetadatas(query);
    }

    @Override
    protected Comparable[] buildRow(Object item) {
        Metadata segmentMetadata = (Metadata) item;
        ArrayList<Comparable> rowData = new ArrayList<>();
        rowData.add(segmentMetadata.isEnabled() ? "x" : "");
        rowData.add(segmentMetadata.isHidden() ? "x" : "");
        rowData.add(segmentMetadata.isReadOnly() ? "x" : "");
        rowData.add(segmentMetadata.getId());
        rowData.add(segmentMetadata.getScope());
        rowData.add(segmentMetadata.getName());
        rowData.add(StringUtils.join(segmentMetadata.getTags(), ","));
        rowData.add(StringUtils.join(segmentMetadata.getSystemTags(), ","));
        return rowData.toArray(new Comparable[0]);
    }

    @Override
    public String create(Map<String, Object> properties) {
        Segment segment = OBJECT_MAPPER.convertValue(properties, Segment.class);
        segmentService.setSegmentDefinition(segment);
        return segment.getItemId();
    }

    @Override
    public Map<String, Object> read(String id) {
        Segment segment = segmentService.getSegmentDefinition(id);
        if (segment == null) {
            return null;
        }
        return OBJECT_MAPPER.convertValue(segment, Map.class);
    }

    @Override
    public void update(String id, Map<String, Object> properties) {
        properties.put("itemId", id);
        Segment segment = OBJECT_MAPPER.convertValue(properties, Segment.class);
        segmentService.setSegmentDefinition(segment);
    }

    @Override
    public void delete(String id) {
        segmentService.removeSegmentDefinition(id, false);
    }

    @Override
    public String getPropertiesHelp() {
        return String.join("\n",
            "Required properties:",
            "- itemId: Segment ID (string)",
            "- name: Segment name",
            "- condition: Segment condition object",
            "",
            "Optional properties:",
            "- description: Segment description",
            "- scope: Segment scope",
            "- metadata: Segment metadata",
            "",
            "Condition types:",
            "- booleanCondition: Simple true/false condition",
            "- profilePropertyCondition: Match profile property",
            "- sessionPropertyCondition: Match session property",
            "- eventPropertyCondition: Match event property",
            "- pastEventCondition: Match past events",
            "- matchAllCondition: Match all sub-conditions",
            "- notCondition: Negate sub-condition",
            "- orCondition: Match any sub-condition",
            "- andCondition: Match all sub-conditions",
            "- profileSegmentCondition: Match profile segment",
            "- scoringCondition: Match scoring value"
        );
    }

    @Override
    public List<String> completePropertyNames(String prefix) {
        return PROPERTY_NAMES.stream()
                .filter(name -> name.startsWith(prefix))
                .collect(Collectors.toList());
    }

    @Override
    public List<String> completePropertyValue(String propertyName, String prefix) {
        if ("condition.type".equals(propertyName)) {
            return CONDITION_TYPES.stream()
                    .filter(type -> type.startsWith(prefix))
                    .collect(Collectors.toList());
        } else if ("scope".equals(propertyName)) {
            return List.of();
        }
        return List.of();
    }
}
