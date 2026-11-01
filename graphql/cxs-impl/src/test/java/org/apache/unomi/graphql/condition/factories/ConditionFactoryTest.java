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

package org.apache.unomi.graphql.condition.factories;

import graphql.schema.DataFetchingEnvironment;
import org.apache.unomi.api.conditions.ConditionType;
import org.apache.unomi.api.services.DefinitionsService;
import org.apache.unomi.api.services.ExecutionContextManager;
import org.apache.unomi.graphql.services.ServiceManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Guards the UNOMI-964 fix: the GraphQL condition factories must resolve condition types
 * live from {@link DefinitionsService} (not from a frozen snapshot) and must not be cached
 * as static singletons. A stale snapshot captured before the condition-type cache was warm
 * produced null condition types, which the ES dispatcher turned into match-none queries.
 */
@ExtendWith(MockitoExtension.class)
class ConditionFactoryTest {

    @Mock
    private DataFetchingEnvironment environment;
    @Mock
    private ServiceManager serviceManager;
    @Mock
    private DefinitionsService definitionsService;
    @Mock
    private ExecutionContextManager contextManager;

    @BeforeEach
    void setUp() {
        doReturn(serviceManager).when(environment).getContext();
        when(serviceManager.getService(DefinitionsService.class)).thenReturn(definitionsService);
    }

    @Test
    void getConditionType_resolvesLiveFromDefinitionsService() {
        final ConditionFactory factory = new ConditionFactory("profilePropertyCondition", environment);

        final ConditionType profilePropertyCondition = mock(ConditionType.class);
        when(serviceManager.getService(ExecutionContextManager.class)).thenReturn(contextManager);
        // Cold cache: tenant and system both miss.
        when(definitionsService.getConditionType("profilePropertyCondition")).thenReturn(null);
        when(contextManager.executeAsSystem(any(Supplier.class))).thenReturn(null);

        assertNull(factory.getConditionType("profilePropertyCondition"),
                "Type should be absent while the definition cache is still cold");

        // Warm cache: live resolution without recreating the factory.
        when(definitionsService.getConditionType("profilePropertyCondition")).thenReturn(profilePropertyCondition);
        assertSame(profilePropertyCondition, factory.getConditionType("profilePropertyCondition"),
                "Type should be visible once the definition is registered (live resolution)");
    }

    @Test
    void getConditionType_fallsBackToSystemTenantWhenTenantLookupMisses() {
        final ConditionFactory factory = new ConditionFactory("profilePropertyCondition", environment);
        final ConditionType profilePropertyCondition = mock(ConditionType.class);

        when(definitionsService.getConditionType("profilePropertyCondition"))
                .thenReturn(null) // tenant miss
                .thenReturn(profilePropertyCondition); // system tenant hit inside executeAsSystem
        when(serviceManager.getService(ExecutionContextManager.class)).thenReturn(contextManager);
        when(contextManager.executeAsSystem(any(Supplier.class))).thenAnswer(invocation -> {
            Supplier<?> supplier = invocation.getArgument(0);
            return supplier.get();
        });

        assertSame(profilePropertyCondition, factory.getConditionType("profilePropertyCondition"),
                "System-tenant fallback should avoid match-none while inheritance is warming (UNOMI-1027)");
    }

    @Test
    void constructor_doesNotSnapshotAllConditionTypes() {
        new ConditionFactory("profilePropertyCondition", environment);

        verify(definitionsService, never()).getAllConditionTypes();
    }

    @Test
    void profileConditionFactory_returnsFreshInstancePerGet() {
        assertNotSame(ProfileConditionFactory.get(environment), ProfileConditionFactory.get(environment));
    }

    @Test
    void eventConditionFactory_returnsFreshInstancePerGet() {
        assertNotSame(EventConditionFactory.get(environment), EventConditionFactory.get(environment));
    }

    @Test
    void profileAliasConditionFactory_returnsFreshInstancePerGet() {
        assertNotSame(ProfileAliasConditionFactory.get(environment), ProfileAliasConditionFactory.get(environment));
    }

    @Test
    void topicConditionFactory_returnsFreshInstancePerGet() {
        assertNotSame(TopicConditionFactory.get(environment), TopicConditionFactory.get(environment));
    }
}
