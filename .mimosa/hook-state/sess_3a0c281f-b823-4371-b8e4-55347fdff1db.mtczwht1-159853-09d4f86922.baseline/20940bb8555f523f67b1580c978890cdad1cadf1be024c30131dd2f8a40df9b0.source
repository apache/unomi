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
package org.apache.unomi.services.common.cache;

import org.apache.unomi.api.services.cache.CacheableTypeConfig;
import org.apache.unomi.api.services.TriFunction;
import org.junit.Test;
import org.osgi.framework.BundleContext;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URL;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class CacheableTypeConfigTest {

    @Test
    public void testStreamProcessor() throws MalformedURLException {
        // Create a test class that implements Serializable
        class TestItem implements Serializable {
            private String id;
            
            public TestItem(String id) {
                this.id = id;
            }
            
            public String getId() {
                return id;
            }
        }
        
        // Create a stream processor
        TriFunction<BundleContext, URL, InputStream, TestItem> processor = 
            (bundleContext, url, inputStream) -> new TestItem("processed-item");
        
        // Create a CacheableTypeConfig with the stream processor
        CacheableTypeConfig<TestItem> config = CacheableTypeConfig.<TestItem>builder(TestItem.class, "test-type", "test-path")
                .withIdExtractor(TestItem::getId)
                .withStreamProcessor(processor)
                .build();
        
        // Verify the stream processor is set and can be retrieved
        assertTrue(config.hasStreamProcessor());
        assertNotNull(config.getStreamProcessor());
        
        // Test the stream processor with mock objects and real URL
        BundleContext mockContext = mock(BundleContext.class);
        URL url = new URL("file:///test.json");
        InputStream mockStream = new ByteArrayInputStream("test".getBytes());
        
        TestItem result = config.getStreamProcessor().apply(mockContext, url, mockStream);
        
        assertNotNull(result);
        assertEquals("processed-item", result.getId());
    }
    
    @Test
    public void testBuilderWithoutStreamProcessor() {
        // Create a test class that implements Serializable
        class TestItem implements Serializable {
            private String id;
            
            public TestItem(String id) {
                this.id = id;
            }
            
            public String getId() {
                return id;
            }
        }
        
        // Create a CacheableTypeConfig without the stream processor
        CacheableTypeConfig<TestItem> config = CacheableTypeConfig.<TestItem>builder(TestItem.class, "test-type", "test-path")
                .withIdExtractor(TestItem::getId)
                .build();
        
        // Verify the stream processor is not set
        assertFalse(config.hasStreamProcessor());
        assertNull(config.getStreamProcessor());
    }
} 