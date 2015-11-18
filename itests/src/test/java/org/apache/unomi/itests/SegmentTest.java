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
 * limitations under the License
 */

package org.apache.unomi.itests;

import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.services.SegmentService;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerSuite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.List;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class SegmentTest extends BaseTest{
    private final static Logger LOGGER = LoggerFactory.getLogger(SegmentTest.class);
    @Inject
    protected SegmentService segmentService;

    @Test
    public void testSegments() {
        Assert.assertNotNull("Segment service should be available", segmentService);
        List<Metadata> segmentMetadatas = segmentService.getSegmentMetadatas(0, 50, null).getList();
        Assert.assertNotEquals("Segment metadata list should not be empty", 0, segmentMetadatas.size());
        LOGGER.info("Retrieved " + segmentMetadatas.size() + " segment metadata entries");
    }
}
