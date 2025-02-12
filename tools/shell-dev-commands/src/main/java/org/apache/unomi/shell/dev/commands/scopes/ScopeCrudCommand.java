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
package org.apache.unomi.shell.dev.commands.scopes;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.Scope;
import org.apache.unomi.api.query.Query;
import org.apache.unomi.api.services.ScopeService;
import org.apache.unomi.persistence.spi.CustomObjectMapper;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.apache.unomi.shell.dev.services.BaseCrudCommand;
import org.apache.unomi.shell.dev.services.CrudCommand;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component(service = CrudCommand.class, immediate = true)
public class ScopeCrudCommand extends BaseCrudCommand {

    private static final ObjectMapper OBJECT_MAPPER = new CustomObjectMapper();
    private static final List<String> PROPERTY_NAMES = List.of(
        "itemId", "name", "description", "restrictedVisibility", "metadata"
    );

    @Reference
    private ScopeService scopeService;

    @Reference
    private PersistenceService persistenceService;

    @Override
    public String getObjectType() {
        return "scope";
    }

    @Override
    public String[] getHeaders() {
        return new String[] {
            "Activated",
            "Hidden",
            "Read-only",
            "Identifier",
            "Name",
            "Description",
            "Restricted",
            "Tags",
            "System tags"
        };
    }

    @Override
    protected PartialList<?> getItems(Query query) {
        return persistenceService.query(query.getCondition(), query.getSortby(), Scope.class, query.getOffset(), query.getLimit());
    }

    @Override
    protected Comparable[] buildRow(Object item) {
        Scope scope = (Scope) item;
        ArrayList<Comparable> rowData = new ArrayList<>();
        rowData.add(scope.getMetadata().isEnabled() ? "x" : "");
        rowData.add(scope.getMetadata().isHidden() ? "x" : "");
        rowData.add(scope.getMetadata().isReadOnly() ? "x" : "");
        rowData.add(scope.getItemId());
        rowData.add(scope.getMetadata().getName());
        rowData.add(scope.getMetadata().getDescription());
        rowData.add(""); // No restricted visibility in Scope class
        rowData.add(StringUtils.join(scope.getMetadata().getTags(), ","));
        rowData.add(StringUtils.join(scope.getMetadata().getSystemTags(), ","));
        return rowData.toArray(new Comparable[0]);
    }

    @Override
    public String create(Map<String, Object> properties) {
        Scope scope = OBJECT_MAPPER.convertValue(properties, Scope.class);
        scopeService.save(scope);
        return scope.getItemId();
    }

    @Override
    public Map<String, Object> read(String id) {
        Scope scope = scopeService.getScope(id);
        if (scope == null) {
            return null;
        }
        return OBJECT_MAPPER.convertValue(scope, Map.class);
    }

    @Override
    public void update(String id, Map<String, Object> properties) {
        properties.put("itemId", id);
        Scope scope = OBJECT_MAPPER.convertValue(properties, Scope.class);
        scopeService.save(scope);
    }

    @Override
    public void delete(String id) {
        scopeService.delete(id);
    }

    @Override
    public String getPropertiesHelp() {
        return String.join("\n",
            "Required properties:",
            "- itemId: Scope ID (string)",
            "- name: Scope name",
            "",
            "Optional properties:",
            "- description: Scope description",
            "- restrictedVisibility: Whether scope has restricted visibility (boolean)",
            "- metadata: Scope metadata"
        );
    }

    @Override
    public List<String> completePropertyNames(String prefix) {
        return PROPERTY_NAMES.stream()
                .filter(name -> name.startsWith(prefix))
                .collect(Collectors.toList());
    }

}
