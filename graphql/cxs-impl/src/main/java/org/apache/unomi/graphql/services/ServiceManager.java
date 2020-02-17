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

import org.apache.unomi.api.services.ProfileService;
import org.apache.unomi.api.services.SegmentService;
import org.apache.unomi.graphql.GraphQLSchemaUpdater;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component(service = ServiceManager.class)
public class ServiceManager {

    private ProfileService profileService;
    private SegmentService segmentService;
    private GraphQLSchemaUpdater graphQLSchemaUpdater;

    @Reference
    public void setProfileService(ProfileService profileService) {
        this.profileService = profileService;
    }

    @Reference
    public void setSegmentService(SegmentService segmentService) {
        this.segmentService = segmentService;
    }

    @Reference
    public void setGraphQLSchemaUpdater(GraphQLSchemaUpdater graphQLSchemaUpdater) {
        this.graphQLSchemaUpdater = graphQLSchemaUpdater;
    }

    public ProfileService getProfileService() {
        return profileService;
    }

    public SegmentService getSegmentService() {
        return segmentService;
    }

    public GraphQLSchemaUpdater getGraphQLSchemaUpdater() {
        return graphQLSchemaUpdater;
    }

}
