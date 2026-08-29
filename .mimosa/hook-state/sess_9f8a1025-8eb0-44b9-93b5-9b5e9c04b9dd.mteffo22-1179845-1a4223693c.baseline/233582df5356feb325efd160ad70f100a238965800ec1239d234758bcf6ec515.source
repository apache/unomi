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

package org.apache.unomi.didvc.services;

import org.apache.unomi.api.Item;
import org.apache.unomi.persistence.spi.PersistenceService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test helper: a Mockito-backed {@link PersistenceService} with an in-memory
 * store, covering the operations the DID-VC services use (save, load,
 * remove, getAllItems).
 */
public final class MockPersistence {

    private MockPersistence() {
    }

    public static PersistenceService create() {
        PersistenceService persistenceService = mock(PersistenceService.class);
        Map<Class<?>, Map<String, Item>> store = new ConcurrentHashMap<>();

        when(persistenceService.save(any(Item.class))).thenAnswer(invocation -> {
            Item item = invocation.getArgument(0);
            store.computeIfAbsent(item.getClass(), c -> new ConcurrentHashMap<>()).put(item.getItemId(), item);
            return true;
        });
        when(persistenceService.load(anyString(), any())).thenAnswer(invocation -> {
            String id = invocation.getArgument(0);
            Class<?> clazz = invocation.getArgument(1);
            Map<String, Item> items = store.get(clazz);
            return items == null ? null : items.get(id);
        });
        when(persistenceService.remove(anyString(), any())).thenAnswer(invocation -> {
            String id = invocation.getArgument(0);
            Class<?> clazz = invocation.getArgument(1);
            Map<String, Item> items = store.get(clazz);
            return items != null && items.remove(id) != null;
        });
        when(persistenceService.getAllItems(any())).thenAnswer(invocation -> {
            Class<?> clazz = invocation.getArgument(0);
            Map<String, Item> items = store.get(clazz);
            return new ArrayList<>(items == null ? List.of() : items.values());
        });
        return persistenceService;
    }
}
