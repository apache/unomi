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
package org.apache.unomi.graphql.types.output;

import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLID;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import graphql.schema.DataFetchingEnvironment;
import org.apache.unomi.graphql.commands.CreateOrUpdatePersonaCommand;
import org.apache.unomi.graphql.commands.CreateOrUpdateProfilePropertiesCommand;
import org.apache.unomi.graphql.commands.CreateOrUpdateSourceCommand;
import org.apache.unomi.graphql.commands.CreateOrUpdateTopicCommand;
import org.apache.unomi.graphql.commands.CreateOrUpdateViewCommand;
import org.apache.unomi.graphql.commands.DeleteAllPersonalDataCommand;
import org.apache.unomi.graphql.commands.DeleteProfileCommand;
import org.apache.unomi.graphql.commands.DeleteProfilePropertiesCommand;
import org.apache.unomi.graphql.commands.DeleteSourceCommand;
import org.apache.unomi.graphql.commands.DeleteTopicCommand;
import org.apache.unomi.graphql.commands.DeleteViewCommand;
import org.apache.unomi.graphql.commands.ProcessEventsCommand;
import org.apache.unomi.graphql.commands.list.AddProfileToListCommand;
import org.apache.unomi.graphql.commands.list.CreateOrUpdateListCommand;
import org.apache.unomi.graphql.commands.list.DeleteListCommand;
import org.apache.unomi.graphql.commands.list.RemoveProfileFromListCommand;
import org.apache.unomi.graphql.commands.segments.CreateOrUpdateSegmentCommand;
import org.apache.unomi.graphql.commands.segments.CreateOrUpdateUnomiSegmentCommand;
import org.apache.unomi.graphql.commands.segments.DeleteSegmentCommand;
import org.apache.unomi.graphql.types.input.CDPEventInput;
import org.apache.unomi.graphql.types.input.CDPListInput;
import org.apache.unomi.graphql.types.input.CDPPersonaInput;
import org.apache.unomi.graphql.types.input.CDPProfileIDInput;
import org.apache.unomi.graphql.types.input.CDPPropertyInput;
import org.apache.unomi.graphql.types.input.CDPSegmentInput;
import org.apache.unomi.graphql.types.input.CDPSourceInput;
import org.apache.unomi.graphql.types.input.CDPTopicInput;
import org.apache.unomi.graphql.types.input.CDPViewInput;
import org.apache.unomi.graphql.types.input.UnomiSegmentInput;

import java.util.List;

import static org.apache.unomi.graphql.CDPGraphQLConstants.PERSONA_ARGUMENT_NAME;
import static org.apache.unomi.graphql.CDPGraphQLConstants.SEGMENT_ARGUMENT_NAME;
import static org.apache.unomi.graphql.types.output.CDPMutation.TYPE_NAME;

@GraphQLName(TYPE_NAME)
public class CDPMutation {

    public static final String TYPE_NAME = "CDP_Mutation";

    @GraphQLField
    public boolean createOrUpdateProfileProperties(
            final @GraphQLName("properties") List<CDPPropertyInput> properties,
            final DataFetchingEnvironment environment) {

        return CreateOrUpdateProfilePropertiesCommand.create(properties)
                .setEnvironment(environment)
                .build()
                .execute();
    }

    @GraphQLField
    public int processEvents(
            final @GraphQLNonNull @GraphQLName("events") List<CDPEventInput> eventInputs,
            final DataFetchingEnvironment environment
    ) {
        return ProcessEventsCommand.create(eventInputs)
                .setEnvironment(environment)
                .build()
                .execute();
    }

    @GraphQLField
    public boolean deleteProfile(
            final @GraphQLNonNull @GraphQLName("profileID") CDPProfileIDInput profileIDInput,
            final DataFetchingEnvironment environment) {
        return DeleteProfileCommand.create()
                .setEnvironment(environment)
                .build()
                .execute();
    }

    @GraphQLField
    public boolean deleteProfileProperties(
            final @GraphQLNonNull @GraphQLName("propertyNames") List<String> propertyNames,
            final DataFetchingEnvironment environment) {
        return DeleteProfilePropertiesCommand.create(propertyNames)
                .setEnvironment(environment)
                .build()
                .execute();
    }

    @GraphQLField
    public boolean deleteAllPersonalData(
            final @GraphQLNonNull @GraphQLName("profileID") CDPProfileIDInput profileIDInput,
            final DataFetchingEnvironment environment) {
        return DeleteAllPersonalDataCommand.create()
                .setEnvironment(environment)
                .build()
                .execute();
    }

    @GraphQLField
    public CDPSegment createOrUpdateSegment(
            final @GraphQLName(SEGMENT_ARGUMENT_NAME) CDPSegmentInput segmentInput,
            final DataFetchingEnvironment environment
    ) {
        return CreateOrUpdateSegmentCommand.create(segmentInput)
                .setEnvironment(environment)
                .build()
                .execute();
    }

    @GraphQLField
    public boolean deleteSegment(
            final @GraphQLID @GraphQLName("segmentID") String segmentId,
            final DataFetchingEnvironment environment
    ) {
        return DeleteSegmentCommand.create(segmentId)
                .setEnvironment(environment)
                .build()
                .execute();
    }

    @GraphQLField
    public UnomiSegment createOrUpdateUnomiSegment(
            final @GraphQLName(SEGMENT_ARGUMENT_NAME) UnomiSegmentInput segmentInput,
            final DataFetchingEnvironment environment
    ) {
        return CreateOrUpdateUnomiSegmentCommand.create(segmentInput)
                .setEnvironment(environment)
                .build()
                .execute();
    }

    @GraphQLField
    public CDPPersona createOrUpdatePersona(
            final @GraphQLName(PERSONA_ARGUMENT_NAME) CDPPersonaInput personaInput,
            final DataFetchingEnvironment environment) {
        return CreateOrUpdatePersonaCommand.create(personaInput)
                .setEnvironment(environment)
                .build()
                .execute();
    }

    @GraphQLField
    public CDPView createOrUpdateView(
            final @GraphQLName("view") CDPViewInput viewInput,
            final DataFetchingEnvironment environment) {
        return CreateOrUpdateViewCommand.create(viewInput)
                .setEnvironment(environment)
                .build()
                .execute();
    }

    @GraphQLField
    public Boolean deleteView(
            final @GraphQLID @GraphQLNonNull @GraphQLName("viewID") String viewId,
            final DataFetchingEnvironment environment) {
        return DeleteViewCommand.create(viewId)
                .setEnvironment(environment)
                .build()
                .execute();
    }

    @GraphQLField
    public CDPTopic createOrUpdateTopic(
            final @GraphQLName("topic") CDPTopicInput topicInput,
            final DataFetchingEnvironment environment) {
        return CreateOrUpdateTopicCommand.create(topicInput)
                .setEnvironment(environment)
                .build()
                .execute();
    }

    @GraphQLField
    public Boolean deleteTopic(
            final @GraphQLID @GraphQLNonNull @GraphQLName("topicID") String topicId,
            final DataFetchingEnvironment environment) {
        return DeleteTopicCommand.create(topicId)
                .setEnvironment(environment)
                .build()
                .execute();
    }

    @GraphQLField
    public CDPSource createOrUpdateSource(
            final @GraphQLName("source") CDPSourceInput sourceInput,
            final DataFetchingEnvironment environment) {
        return CreateOrUpdateSourceCommand.create(sourceInput)
                .setEnvironment(environment)
                .build()
                .execute();
    }

    @GraphQLField
    public Boolean deleteSource(
            final @GraphQLID @GraphQLNonNull @GraphQLName("sourceID") String sourceId,
            final DataFetchingEnvironment environment) {
        return DeleteSourceCommand.create(sourceId)
                .setEnvironment(environment)
                .build()
                .execute();
    }

    @GraphQLField
    public CDPList createOrUpdateList(
            final @GraphQLName("list") CDPListInput listInput,
            final DataFetchingEnvironment environment) {
        return CreateOrUpdateListCommand.create(listInput)
                .setEnvironment(environment)
                .build()
                .execute();
    }

    @GraphQLField
    public CDPList addProfileToList(
            final @GraphQLName("listID") String listId,
            final @GraphQLName("profileID") CDPProfileIDInput profileIDInput,
            final @GraphQLName("active") Boolean active,
            final DataFetchingEnvironment environment) {
        return AddProfileToListCommand.create()
                .setEnvironment(environment)
                .listId(listId)
                .profileIDInput(profileIDInput)
                .active(active)
                .build()
                .execute();
    }

    @GraphQLField
    public Boolean removeProfileFromList(
            final @GraphQLID @GraphQLName("listID") String listId,
            final @GraphQLName("profileID") CDPProfileIDInput profileIDInput,
            final DataFetchingEnvironment environment) {
        return RemoveProfileFromListCommand.create()
                .setEnvironment(environment)
                .listId(listId)
                .profileIDInput(profileIDInput)
                .build()
                .execute();
    }

    @GraphQLField
    public Boolean deleteList(
            final @GraphQLID @GraphQLName("listID") String listId,
            final DataFetchingEnvironment environment) {
        return DeleteListCommand.create(listId)
                .setEnvironment(environment)
                .build()
                .execute();
    }

}
