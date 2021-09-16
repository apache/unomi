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

import org.apache.commons.io.IOUtils;
import org.apache.unomi.api.actions.ActionType;
import org.apache.unomi.api.services.DefinitionsService;
import org.apache.unomi.groovy.actions.GroovyAction;
import org.apache.unomi.groovy.actions.services.GroovyActionsService;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerSuite;
import org.ops4j.pax.exam.util.Filter;

import javax.inject.Inject;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class GroovyActionsServiceIT extends BaseIT {

    @Inject
    @Filter(timeout = 600000)
    protected GroovyActionsService groovyActionsService;

    @Inject
    @Filter(timeout = 600000)
    protected DefinitionsService definitionsService;

    @Before
    public void setUp() throws InterruptedException {
        refreshPersistence();
    }

    @After
    public void cleanUp() throws InterruptedException {
        refreshPersistence();
    }

    private String loadGroovyAction(String pathname) throws IOException {
        return IOUtils.toString(new FileInputStream(new File(pathname)));
    }

    @Test
    public void testGroovyActionsService_saveActionAndTestSavedValues() throws IOException, InterruptedException {
        groovyActionsService.save("MyAction", loadGroovyAction("data/tmp/groovy/MyAction.groovy"));

        Thread.sleep(2000);

        GroovyAction groovyAction = groovyActionsService.getGroovyAction("MyAction");

        ActionType actionType = definitionsService.getActionType("scriptGroovyAction");

        Assert.assertEquals("MyAction", groovyAction.getItemId());
        Assert.assertEquals("MyAction", groovyAction.getName());
        Assert.assertTrue(groovyAction.getScript().contains("A test Groovy"));

        Assert.assertTrue(actionType.getMetadata().getId().contains("scriptGroovyAction"));
        Assert.assertEquals(2, actionType.getMetadata().getSystemTags().size());
        Assert.assertTrue(actionType.getMetadata().getSystemTags().contains("tag1"));
        Assert.assertEquals(2, actionType.getParameters().size());
        Assert.assertEquals("param1", actionType.getParameters().get(0).getId());

        Assert.assertEquals("groovy:MyAction", actionType.getActionExecutor());
        Assert.assertFalse(actionType.getMetadata().isHidden());
    }

    @Test
    public void testGroovyActionsService_removeGroovyAction() throws IOException, InterruptedException {
        groovyActionsService.save("MyAction", loadGroovyAction("data/tmp/groovy/MyAction.groovy"));

        Thread.sleep(2000);

        GroovyAction groovyAction = groovyActionsService.getGroovyAction("MyAction");

        Assert.assertNotNull(groovyAction);

        groovyActionsService.remove("MyAction");

        Thread.sleep(2000);

        groovyAction = groovyActionsService.getGroovyAction("MyAction");

        Assert.assertNull(groovyAction);

        ActionType actionType = definitionsService.getActionType("scriptGroovyAction");

        Assert.assertNull(actionType);

    }
}
