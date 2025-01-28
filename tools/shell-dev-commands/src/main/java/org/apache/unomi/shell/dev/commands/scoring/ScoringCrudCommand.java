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
package org.apache.unomi.shell.dev.commands.scoring;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.query.Query;
import org.apache.unomi.api.segments.Scoring;
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
public class ScoringCrudCommand extends BaseCrudCommand {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final List<String> PROPERTY_NAMES = List.of(
            "itemId", "scope", "name", "description", "elements", "metadata"
    );

    @Reference
    private SegmentService segmentService;

    @Override
    public String getObjectType() {
        return "scoring";
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
        return segmentService.getScoringMetadatas(query);
    }

    @Override
    protected Comparable[] buildRow(Object item) {
        Metadata metadata = (Metadata) item;
        ArrayList<Comparable> rowData = new ArrayList<>();
        rowData.add(metadata.isEnabled() ? "x" : "");
        rowData.add(metadata.isHidden() ? "x" : "");
        rowData.add(metadata.isReadOnly() ? "x" : "");
        rowData.add(metadata.getId());
        rowData.add(metadata.getScope());
        rowData.add(metadata.getName());
        rowData.add(StringUtils.join(metadata.getTags(), ","));
        rowData.add(StringUtils.join(metadata.getSystemTags(), ","));
        return rowData.toArray(new Comparable[0]);
    }

    @Override
    public String create(Map<String, Object> properties) {
        Scoring scoring = OBJECT_MAPPER.convertValue(properties, Scoring.class);
        segmentService.setScoringDefinition(scoring);
        return scoring.getItemId();
    }

    @Override
    public Map<String, Object> read(String id) {
        Scoring scoring = segmentService.getScoringDefinition(id);
        if (scoring == null) {
            return null;
        }
        return OBJECT_MAPPER.convertValue(scoring, Map.class);
    }

    @Override
    public void update(String id, Map<String, Object> properties) {
        Scoring scoring = segmentService.getScoringDefinition(id);
        if (scoring == null) {
            throw new IllegalArgumentException("Scoring with id '" + id + "' not found");
        }
        Scoring updatedScoring = OBJECT_MAPPER.convertValue(properties, Scoring.class);
        updatedScoring.setItemId(id);
        segmentService.setScoringDefinition(updatedScoring);
    }

    @Override
    public void delete(String id) {
        segmentService.removeScoringDefinition(id, false);
    }

    @Override
    public List<String> completePropertyNames(String prefix) {
        return PROPERTY_NAMES.stream()
                .filter(name -> name.startsWith(prefix))
                .collect(Collectors.toList());
    }

    @Override
    public String getPropertiesHelp() {
        return "Required properties:\n" +
               "- itemId (string): Unique identifier for the scoring\n" +
               "- scope (string): Scope of the scoring\n" +
               "- name (string): Human-readable name\n" +
               "\n" +
               "Optional properties:\n" +
               "- description (string): Description of the scoring\n" +
               "- elements (array): List of scoring elements, each containing:\n" +
               "  - condition (object): Condition that triggers the scoring element\n" +
               "  - value (number): Score value to add when condition is met\n" +
               "- enabled (boolean): Whether the scoring is enabled (default: true)\n" +
               "- hidden (boolean): Whether the scoring is hidden (default: false)\n" +
               "- readOnly (boolean): Whether the scoring is read-only (default: false)\n" +
               "- tags (array): List of tags for the scoring\n" +
               "- systemTags (array): List of system tags for the scoring";
    }
}
