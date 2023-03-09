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

import org.apache.unomi.api.Item;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.conditions.Condition;
import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerSuite;

import java.util.List;

/**
 * Integration tests for various condition query builder types (elasticsearch).
 *
 * @author Sergiy Shyrkov
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class ConditionESQueryBuilderIT extends ConditionEvaluatorIT {

    @Override
    protected boolean eval(Condition c) {
        @SuppressWarnings("unchecked")
        List<Item> list = persistenceService.query(c,null,(Class<Item>) item.getClass());
        return list.contains(item);
    }

    @Before
    public void setUp() {
        super.setUp();
        persistenceService.save(item);
        persistenceService.refreshIndex(Profile.class, null);
    }

    @After
    public void tearDown() {
        persistenceService.remove(item.getItemId(), item.getClass());
    }

}
