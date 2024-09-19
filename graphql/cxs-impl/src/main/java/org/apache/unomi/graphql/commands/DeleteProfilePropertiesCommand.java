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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class DeleteProfilePropertiesCommand extends BaseCommand<Boolean> {

    private static final Logger LOG = LoggerFactory.getLogger(DeleteProfilePropertiesCommand.class);

    private final List<String> propertyNames;

    private DeleteProfilePropertiesCommand(Builder builder) {
        super(builder);

        this.propertyNames = builder.propertyNames;
    }

    @Override
    public Boolean execute() {
        ProfileService profileService = serviceManager.getService(ProfileService.class);
        final Collection<PropertyType> propertyTypes = profileService.getTargetPropertyTypes("profiles");

        final List<String> persistedPropertyNames = propertyTypes.stream().map(PropertyType::getItemId).collect(Collectors.toList());

        final List<String> incorrectPropertyNames = new ArrayList<>(propertyNames);
        incorrectPropertyNames.removeAll(persistedPropertyNames);

        if (!incorrectPropertyNames.isEmpty()) {
            throw new IllegalArgumentException(String.format("The properties \"%s\" do not belong to profile", incorrectPropertyNames.toString()));
        }

        for (String propertyName : propertyNames) {
            try {
                boolean deleted = profileService.deletePropertyType(propertyName);

                if (deleted) {
                    LOG.info("The property \"{}\" of profile was deleted successfully", propertyName);
                } else {
                    LOG.info("The property \"{}\" of profile was not deleted", propertyName);
                }
            } catch (Exception e) {
                LOG.error("The delete property \"{}\" is failed", propertyName, e);
            }
        }

        serviceManager.getService(GraphQLSchemaUpdater.class).updateSchema();

        return true;
    }

    public static Builder create(final List<String> propertyNames) {
        return new Builder(propertyNames);
    }

    public static class Builder extends BaseCommand.Builder<Builder> {

        private final List<String> propertyNames;

        public Builder(List<String> propertyNames) {
            this.propertyNames = propertyNames;
        }

        @Override
        public void validate() {
            super.validate();

            if (propertyNames == null || propertyNames.isEmpty()) {
                throw new IllegalArgumentException("The \"propertyNames\" variable can not be null or empty");
            }
        }

        public DeleteProfilePropertiesCommand build() {
            validate();

            return new DeleteProfilePropertiesCommand(this);
        }
    }

}
