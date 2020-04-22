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
package org.apache.unomi.graphql.services;

import org.apache.unomi.api.services.DefinitionsService;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.api.services.PrivacyService;
import org.apache.unomi.api.services.ProfileService;
import org.apache.unomi.api.services.SegmentService;
import org.apache.unomi.graphql.schema.CDPEventInterfaceRegister;
import org.apache.unomi.graphql.schema.CDPProfileInterfaceRegister;
import org.apache.unomi.graphql.schema.CDPPropertyInterfaceRegister;
import org.apache.unomi.graphql.schema.GraphQLSchemaUpdater;
import org.apache.unomi.api.services.UserListService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component(service = ServiceManager.class)
public class ServiceManager {

    private ProfileService profileService;
    private SegmentService segmentService;
    private EventService eventService;
    private DefinitionsService definitionsService;
    private GraphQLSchemaUpdater graphQLSchemaUpdater;
    private PrivacyService privacyService;
    private UserListService userListService;
    private org.apache.unomi.services.UserListService userListServiceExt;
    private CDPEventInterfaceRegister eventInterfaceRegister;
    private CDPProfileInterfaceRegister profileInterfaceRegister;
    private CDPPropertyInterfaceRegister propertyInterfaceRegister;

    @Reference
    public void setProfileService(ProfileService profileService) {
        this.profileService = profileService;
    }

    @Reference
    public void setSegmentService(SegmentService segmentService) {
        this.segmentService = segmentService;
    }

    @Reference
    public void setDefinitionsService(DefinitionsService definitionsService) {
        this.definitionsService = definitionsService;
    }

    @Reference
    public void setEventService(EventService eventService) {
        this.eventService = eventService;
    }

    @Reference
    public void setGraphQLSchemaUpdater(GraphQLSchemaUpdater graphQLSchemaUpdater) {
        this.graphQLSchemaUpdater = graphQLSchemaUpdater;
    }

    @Reference
    public void setUserListService(UserListService userListService) {
        this.userListService = userListService;
    }

    @Reference
    public void setPrivacyService(PrivacyService privacyService) {
        this.privacyService = privacyService;
    }

    @Reference
    public void setEventInterfaceRegister(CDPEventInterfaceRegister eventInterfaceRegister) {
        this.eventInterfaceRegister = eventInterfaceRegister;
    }

    @Reference
    public void setProfileInterfaceRegister(CDPProfileInterfaceRegister profileInterfaceRegister) {
        this.profileInterfaceRegister = profileInterfaceRegister;
    }

    @Reference
    public void setPropertyInterfaceRegister(CDPPropertyInterfaceRegister propertyInterfaceRegister) {
        this.propertyInterfaceRegister = propertyInterfaceRegister;
    }

    @Reference
    public void setUserListServiceExt(org.apache.unomi.services.UserListService userListServiceExt) {
        this.userListServiceExt = userListServiceExt;
    }

    public ProfileService getProfileService() {
        return profileService;
    }

    public SegmentService getSegmentService() {
        return segmentService;
    }

    public DefinitionsService getDefinitionsService() {
        return definitionsService;
    }

    public EventService getEventService() {
        return eventService;
    }

    public GraphQLSchemaUpdater getGraphQLSchemaUpdater() {
        return graphQLSchemaUpdater;
    }

    public PrivacyService getPrivacyService() {
        return privacyService;
    }

    public UserListService getUserListService() {
        return userListService;
    }

    public CDPEventInterfaceRegister getEventInterfaceRegister() {
        return eventInterfaceRegister;
    }

    public CDPProfileInterfaceRegister getProfileInterfaceRegister() {
        return profileInterfaceRegister;
    }

    public org.apache.unomi.services.UserListService getUserListServiceExt() {
        return userListServiceExt;
    }

    public CDPPropertyInterfaceRegister getPropertyInterfaceRegister() {
        return propertyInterfaceRegister;
    }
}
