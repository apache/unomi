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

package org.apache.unomi.graphql.commands.list;

import graphql.schema.DataFetchingEnvironment;
import org.apache.unomi.api.Event;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.api.services.ProfileService;
import org.apache.unomi.graphql.services.ServiceManager;
import org.apache.unomi.graphql.types.input.CDPProfileIDInput;
import org.apache.unomi.lists.UserList;
import org.apache.unomi.services.UserListService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Guards the UNOMI-964 bitmask fix in {@link AddProfileToListCommand}. {@link EventService#send}
 * returns a bitmask; the profile must be persisted whenever the PROFILE_UPDATED bit is set,
 * even when it is OR-ed with other change flags. The previous {@code == PROFILE_UPDATED} check
 * silently dropped the save whenever another flag was also set.
 */
@ExtendWith(MockitoExtension.class)
class AddProfileToListCommandTest {

    private static final String LIST_ID = "testListId";
    private static final String PROFILE_ID = "test_profile_id";

    @Mock
    private DataFetchingEnvironment environment;
    @Mock
    private ServiceManager serviceManager;
    @Mock
    private UserListService userListService;
    @Mock
    private ProfileService profileService;
    @Mock
    private EventService eventService;

    private Profile profile;

    @BeforeEach
    void setUp() {
        doReturn(serviceManager).when(environment).getContext();
        when(serviceManager.getService(UserListService.class)).thenReturn(userListService);
        when(serviceManager.getService(ProfileService.class)).thenReturn(profileService);
        when(serviceManager.getService(EventService.class)).thenReturn(eventService);

        final UserList userList = new UserList();
        userList.setItemId(LIST_ID);
        when(userListService.load(LIST_ID)).thenReturn(userList);

        profile = new Profile(PROFILE_ID);
        when(profileService.load(PROFILE_ID)).thenReturn(profile);
    }

    private AddProfileToListCommand command() {
        return AddProfileToListCommand.create()
                .listId(LIST_ID)
                .profileIDInput(new CDPProfileIDInput(PROFILE_ID, null))
                .setEnvironment(environment)
                .build();
    }

    @Test
    void savesProfile_whenEventReturnsProfileUpdatedExactly() {
        when(eventService.send(any(Event.class))).thenReturn(EventService.PROFILE_UPDATED);

        command().execute();

        verify(profileService).save(profile);
    }

    @Test
    void savesProfile_whenProfileUpdatedBitIsCombinedWithOtherFlags() {
        when(eventService.send(any(Event.class)))
                .thenReturn(EventService.PROFILE_UPDATED | EventService.SESSION_UPDATED);

        command().execute();

        verify(profileService).save(profile);
    }

    @Test
    void doesNotSaveProfile_whenEventReportsNoChange() {
        when(eventService.send(any(Event.class))).thenReturn(EventService.NO_CHANGE);

        command().execute();

        verify(profileService, never()).save(any(Profile.class));
    }

    @Test
    void doesNotSaveProfile_whenProfileUpdatedBitIsNotSet() {
        when(eventService.send(any(Event.class))).thenReturn(EventService.SESSION_UPDATED);

        command().execute();

        verify(profileService, never()).save(any(Profile.class));
    }
}
