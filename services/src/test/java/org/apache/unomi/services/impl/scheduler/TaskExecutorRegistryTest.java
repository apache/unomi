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

package org.apache.unomi.services.impl.scheduler;

import org.apache.unomi.api.tasks.ScheduledTask;
import org.apache.unomi.api.tasks.TaskExecutor;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for TaskExecutorRegistry
 */
public class TaskExecutorRegistryTest {
    
    private TaskExecutorRegistry registry;
    
    @Before
    public void setUp() {
        registry = new TaskExecutorRegistry();
    }
    
    @Test
    public void testRegisterExecutor() {
        TestTaskExecutor executor = new TestTaskExecutor("test-type");
        
        assertFalse(registry.hasExecutor("test-type"));
        assertEquals(0, registry.getExecutorCount());
        
        registry.registerExecutor(executor);
        
        assertTrue(registry.hasExecutor("test-type"));
        assertEquals(1, registry.getExecutorCount());
        assertEquals(executor, registry.getExecutor("test-type"));
    }
    
    @Test
    public void testUnregisterExecutor() {
        TestTaskExecutor executor = new TestTaskExecutor("test-type");
        
        registry.registerExecutor(executor);
        assertTrue(registry.hasExecutor("test-type"));
        
        registry.unregisterExecutor(executor);
        
        assertFalse(registry.hasExecutor("test-type"));
        assertEquals(0, registry.getExecutorCount());
        assertNull(registry.getExecutor("test-type"));
    }
    
    @Test
    public void testMultipleExecutors() {
        TestTaskExecutor executor1 = new TestTaskExecutor("type1");
        TestTaskExecutor executor2 = new TestTaskExecutor("type2");
        
        registry.registerExecutor(executor1);
        registry.registerExecutor(executor2);
        
        assertEquals(2, registry.getExecutorCount());
        assertEquals(2, registry.getRegisteredTaskTypes().size());
        assertTrue(registry.getRegisteredTaskTypes().contains("type1"));
        assertTrue(registry.getRegisteredTaskTypes().contains("type2"));
        
        assertEquals(executor1, registry.getExecutor("type1"));
        assertEquals(executor2, registry.getExecutor("type2"));
    }
    
    @Test
    public void testReplaceExecutor() {
        TestTaskExecutor executor1 = new TestTaskExecutor("test-type");
        TestTaskExecutor executor2 = new TestTaskExecutor("test-type");
        
        registry.registerExecutor(executor1);
        assertEquals(executor1, registry.getExecutor("test-type"));
        
        // Registering another executor for the same type should replace it
        registry.registerExecutor(executor2);
        assertEquals(executor2, registry.getExecutor("test-type"));
        assertEquals(1, registry.getExecutorCount());
    }
    
    @Test
    public void testClear() {
        registry.registerExecutor(new TestTaskExecutor("type1"));
        registry.registerExecutor(new TestTaskExecutor("type2"));
        
        assertEquals(2, registry.getExecutorCount());
        
        registry.clear();
        
        assertEquals(0, registry.getExecutorCount());
        assertFalse(registry.hasExecutor("type1"));
        assertFalse(registry.hasExecutor("type2"));
    }
    
    @Test
    public void testGetAllExecutors() {
        TestTaskExecutor executor1 = new TestTaskExecutor("type1");
        TestTaskExecutor executor2 = new TestTaskExecutor("type2");
        
        registry.registerExecutor(executor1);
        registry.registerExecutor(executor2);
        
        assertEquals(2, registry.getAllExecutors().size());
        assertTrue(registry.getAllExecutors().containsValue(executor1));
        assertTrue(registry.getAllExecutors().containsValue(executor2));
        
        // Verify returned map is unmodifiable
        try {
            registry.getAllExecutors().put("test", executor1);
            fail("Should throw UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // Expected
        }
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testRegisterNullExecutor() {
        registry.registerExecutor(null);
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testRegisterExecutorWithNullTaskType() {
        TaskExecutor executor = new TaskExecutor() {
            @Override
            public String getTaskType() {
                return null;
            }
            
            @Override
            public void execute(ScheduledTask task, TaskStatusCallback callback) {
                // No-op
            }
        };
        
        registry.registerExecutor(executor);
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testRegisterExecutorWithEmptyTaskType() {
        TaskExecutor executor = new TaskExecutor() {
            @Override
            public String getTaskType() {
                return "";
            }
            
            @Override
            public void execute(ScheduledTask task, TaskStatusCallback callback) {
                // No-op
            }
        };
        
        registry.registerExecutor(executor);
    }
    
    @Test
    public void testUnregisterNullExecutor() {
        // Should not throw exception
        registry.unregisterExecutor(null);
    }
    
    @Test
    public void testGetNonExistentExecutor() {
        assertNull(registry.getExecutor("non-existent"));
        assertNull(registry.getExecutor(null));
        assertFalse(registry.hasExecutor("non-existent"));
        assertFalse(registry.hasExecutor(null));
    }
    
    /**
     * Test helper class
     */
    private static class TestTaskExecutor implements TaskExecutor {
        private final String taskType;
        
        public TestTaskExecutor(String taskType) {
            this.taskType = taskType;
        }
        
        @Override
        public String getTaskType() {
            return taskType;
        }
        
        @Override
        public void execute(ScheduledTask task, TaskStatusCallback callback) {
            // No-op for testing
        }
    }
} 