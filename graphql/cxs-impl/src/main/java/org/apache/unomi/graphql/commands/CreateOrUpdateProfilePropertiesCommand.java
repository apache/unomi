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

import org.apache.unomi.api.PropertyType;
import org.apache.unomi.api.services.ProfileService;
import org.apache.unomi.graphql.schema.GraphQLSchemaUpdater;
import org.apache.unomi.graphql.types.input.CDPPropertyInput;
import org.apache.unomi.graphql.types.input.property.BaseCDPPropertyInput;
import org.apache.unomi.graphql.types.input.property.CDPSetPropertyInput;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class CreateOrUpdateProfilePropertiesCommand extends BaseCommand<Boolean> {

    private final List<CDPPropertyInput> properties;

    private ProfileService profileService;

    private CreateOrUpdateProfilePropertiesCommand(final Builder builder) {
        super(builder);

        this.properties = builder.properties;
        this.profileService = serviceManager.getService(ProfileService.class);
    }

    public static Builder create(List<CDPPropertyInput> properties) {
        return new Builder(properties);
    }

    @Override
    public Boolean execute() {
        properties.forEach(cdpPropertyInput -> {
            final PropertyType propertyType = processPropertyType(cdpPropertyInput);

            profileService.setPropertyType(propertyType);
        });

        serviceManager.getService(GraphQLSchemaUpdater.class).updateSchema();

        return true;
    }

    private PropertyType processPropertyType(final CDPPropertyInput propertyInput) {
        final BaseCDPPropertyInput cdpPropertyTypeInput = propertyInput.getProperty();

        PropertyType propertyType = profileService.getPropertyType(cdpPropertyTypeInput.getName());

        if (propertyType == null) {
            propertyType = new PropertyType();
        } else if (!propertyType.getValueTypeId().equals(cdpPropertyTypeInput.getCDPPropertyType())) {
            profileService.deletePropertyType(cdpPropertyTypeInput.getName());
        }

        if (cdpPropertyTypeInput instanceof CDPSetPropertyInput) {
            final CDPSetPropertyInput cdpSetPropertyInput = (CDPSetPropertyInput) cdpPropertyTypeInput;

            final Set<PropertyType> propertyTypes = cdpSetPropertyInput.getProperties().stream()
                    .map(this::processPropertyType)
                    .collect(Collectors.toSet());

            propertyType.setChildPropertyTypes(propertyTypes);
        }

        cdpPropertyTypeInput.updateType(propertyType);

        return propertyType;
    }

    public static class Builder extends BaseCommand.Builder<Builder> {

        final List<CDPPropertyInput> properties;

        Builder(final List<CDPPropertyInput> properties) {
            this.properties = properties;
        }

        @Override
        public void validate() {
            super.validate();

            if (properties == null || properties.isEmpty()) {
                throw new IllegalArgumentException("Properties can not be null or empty.");
            }

            properties.forEach(prop -> {
                final List<BaseCDPPropertyInput> properties = Arrays.asList(
                        prop.getIdentifierPropertyTypeInput(),
                        prop.getStringPropertyTypeInput(),
                        prop.getIntegerPropertyTypeInput(),
                        prop.getFloatPropertyTypeInput(),
                        prop.getDatePropertyTypeInput(),
                        prop.getBooleanPropertyTypeInput(),
                        prop.getGeoPointPropertyTypeInput(),
                        prop.getSetPropertyTypeInput());

                final List<BaseCDPPropertyInput> filteredProperties = properties.stream().filter(Objects::nonNull).collect(Collectors.toList());

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
