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
package org.apache.unomi.shell.dev.commands.personas;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.Persona;
import org.apache.unomi.api.query.Query;
import org.apache.unomi.api.services.ProfileService;
import org.apache.unomi.shell.dev.services.BaseCrudCommand;
import org.apache.unomi.shell.dev.services.CrudCommand;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component(service = CrudCommand.class, immediate = true)
public class PersonaCrudCommand extends BaseCrudCommand {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final List<String> PROPERTY_NAMES = Arrays.asList(
            "firstName",
            "lastName",
            "email",
            "description",
            "properties"
    );

    @Reference
    private ProfileService profileService;

    @Override
    public String getObjectType() {
        return Persona.ITEM_TYPE;
    }

    @Override
    public String[] getHeaders() {
        return new String[] {
            "Identifier",
            "First Name",
            "Last Name",
            "Email",
            "Description",
            "Last Updated",
            "Tenant"
        };
    }

    @Override
    protected PartialList<?> getItems(Query query) {
        return profileService.search(query, Persona.class);
    }

    @Override
    protected String[] buildRow(Object item) {
        Persona persona = (Persona) item;
        return new String[] {
            persona.getItemId(),
            (String) persona.getProperty("firstName"),
            (String) persona.getProperty("lastName"),
            (String) persona.getProperty("email"),
            (String) persona.getProperty("description"),
            persona.getSystemProperties().get("lastUpdated").toString(),
            persona.getTenantId()
        };
    }

    @Override
    public String create(Map<String, Object> properties) {
        String personaId = (String) properties.get("itemId");
        if (personaId == null) {
            throw new IllegalArgumentException("itemId is required");
        }

        Persona persona = new Persona(personaId);
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            if (!entry.getKey().equals("itemId")) {
                persona.setProperty(entry.getKey(), entry.getValue());
            }
        }

        Persona saved = profileService.savePersona(persona);
        return saved.getItemId();
    }

    @Override
    public Map<String, Object> read(String id) {
        Persona persona = profileService.loadPersona(id);
        return persona != null ? OBJECT_MAPPER.convertValue(persona, Map.class) : null;
    }

    @Override
    public void update(String id, Map<String, Object> properties) {
        Persona persona = profileService.loadPersona(id);
        if (persona == null) {
            throw new IllegalArgumentException("Persona not found: " + id);
        }

        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            persona.setProperty(entry.getKey(), entry.getValue());
        }

        profileService.savePersona(persona);
    }

    @Override
    public void delete(String id) {
        profileService.delete(id, true);
    }

    @Override
    public String getPropertiesHelp() {
        return "Required properties:\n" +
               "- itemId: The identifier for the persona\n" +
               "\n" +
               "Optional properties:\n" +
               "- firstName: The persona's first name\n" +
               "- lastName: The persona's last name\n" +
               "- email: The persona's email address\n" +
               "- description: A description of what this persona represents\n" +
               "- properties: Additional properties as a JSON object";
    }

    @Override
    public List<String> completePropertyNames(String prefix) {
        return PROPERTY_NAMES.stream()
                .filter(name -> name.startsWith(prefix))
                .collect(Collectors.toList());
    }

    @Override
    public List<String> completePropertyValue(String propertyName, String prefix) {
        return List.of();
    }
}
