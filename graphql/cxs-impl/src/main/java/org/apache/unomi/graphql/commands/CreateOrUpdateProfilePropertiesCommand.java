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
import org.apache.unomi.api.PropertyType;
import org.apache.unomi.api.services.ProfileService;
import org.apache.unomi.graphql.propertytypes.CDPPropertyType;
import org.apache.unomi.graphql.types.input.CDPPropertyInput;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class CreateOrUpdateProfilePropertiesCommand extends BaseCommand<Boolean> {

    private final List<CDPPropertyInput> properties;

    private CreateOrUpdateProfilePropertiesCommand(final Builder builder) {
        super(builder);
        this.properties = builder.properties;
    }

    public static Builder create(List<CDPPropertyInput> properties) {
        return new Builder(properties);
    }

    @Override
    public Boolean execute() {
        final ProfileService profileService = serviceManager.getProfileService();

        // TODO handle properties for SET
        properties.forEach(propertyInput -> {
            final CDPPropertyType cdpPropertyType = propertyInput.getProperty();

            PropertyType propertyType = profileService.getPropertyType(cdpPropertyType.getName());

            if (propertyType == null) {
                propertyType = createPropertyType(cdpPropertyType);
                profileService.setPropertyType(propertyType);
            }

            if (!propertyType.getValueTypeId().equals(cdpPropertyType.getCDPPropertyType())) {
                profileService.deletePropertyType(cdpPropertyType.getName());

                propertyType = createPropertyType(cdpPropertyType);
                profileService.setPropertyType(propertyType);
            }
        });

        serviceManager.getGraphQLSchemaUpdater().updateSchema();

        return true;
    }

    private PropertyType createPropertyType(final CDPPropertyType cdpPropertyType) {
        final PropertyType propertyType = new PropertyType();

        propertyType.setTarget("profiles");
        propertyType.setItemId(cdpPropertyType.getName());
        propertyType.setValueTypeId(cdpPropertyType.getCDPPropertyType());

        final Metadata metadata = createMetadata(cdpPropertyType);
        propertyType.setMetadata(metadata);

        return propertyType;
    }

    private Metadata createMetadata(final CDPPropertyType property) {
        final Metadata metadata = new Metadata();

        metadata.setId(property.getName());
        metadata.setName(property.getName());

        final Set<String> systemTags = new HashSet<>();

        systemTags.add("profileProperties");
        systemTags.add("properties");
        systemTags.add("systemProfileProperties");

        metadata.setSystemTags(systemTags);

        return metadata;
    }

    public static class Builder extends BaseCommand.Builder<Builder> {

        final List<CDPPropertyInput> properties;

        Builder(final List<CDPPropertyInput> properties) {
            this.properties = properties;
        }

        private void validate() {
            if (properties == null || properties.isEmpty()) {
                throw new IllegalArgumentException("Properties can not be null or empty.");
            }

            properties.forEach(prop -> {
                final List<CDPPropertyType> properties = Arrays.asList(
                        prop.identifierPropertyTypeInput,
                        prop.stringPropertyTypeInput,
                        prop.integerPropertyTypeInput,
                        prop.floatPropertyTypeInput,
                        prop.datePropertyTypeInput,
                        prop.booleanPropertyTypeInput,
                        prop.geoPointPropertyTypeInput,
                        prop.setPropertyTypeInput);

                final List<CDPPropertyType> filteredProperties = properties.stream().filter(Objects::nonNull).collect(Collectors.toList());

                if (filteredProperties.size() != 1) {
                    throw new IllegalArgumentException("Only one field is allowed to have a value corresponding to the declared property value type.  All other value fields must be null.");
                }
            });
        }

        public CreateOrUpdateProfilePropertiesCommand build() {
            validate();

            return new CreateOrUpdateProfilePropertiesCommand(this);
        }
    }

}
