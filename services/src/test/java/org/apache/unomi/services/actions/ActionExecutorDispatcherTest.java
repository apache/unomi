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
package org.apache.unomi.services.actions;

import org.apache.unomi.api.CustomItem;
import org.apache.unomi.api.Event;
import org.apache.unomi.common.SecureFilteringClassLoader;
import org.junit.Test;
import org.mvel2.CompileException;
import org.mvel2.MVEL;
import org.mvel2.ParserConfiguration;
import org.mvel2.ParserContext;

import java.io.*;
import java.util.*;

import static org.junit.Assert.assertFalse;

public class ActionExecutorDispatcherTest {

    public static final String MOCK_ITEM_ID = "mockItemId";
    public static final String DIGITALL_SCOPE = "digitall";
    public static final String PAGE_PATH_VALUE = "/site/en/home/aboutus.html";
    public static final String PAGE_URL_VALUE = "http://localhost:8080/site/en/home/aboutus.html";

    @Test
    public void testMVELSecurity() throws IOException {
        Map<String, Object> ctx = new HashMap<>();
        Event mockEvent = generateMockEvent();
        ctx.put("event", mockEvent);
        ctx.put("session", mockEvent.getSession());
        ctx.put("profile", mockEvent.getProfile());
        File vulnFile = new File("target/vuln-file.txt");
        if (vulnFile.exists()) {
            vulnFile.delete();
        }
        Object result = null;
        try {
            result = executeMVEL("java.io.PrintWriter writer = new java.io.PrintWriter(new java.io.BufferedWriter(new java.io.FileWriter(\"" + vulnFile.getCanonicalPath() + "\", true)));\nwriter.println(\"test\");\nwriter.close();", ctx);
        } catch (CompileException ce) {
            // this is expected since access to these classes should not be allowed
        }
        System.out.println("result=" + result);
        try {
            result = executeMVEL("import java.util.*;\nimport java.io.*;\nPrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(\"" + vulnFile.getCanonicalPath() + "\", true)));\nwriter.println(\"test\");\nwriter.close();", ctx);
        } catch (CompileException ce) {
            // this is expected since access to these classes should not be allowed
        }
        System.out.println("result=" + result);
        try {
            result = executeMVEL("import java.util.*;\nimport java.io.*;\nnew Scanner(new File(\"" + vulnFile.getCanonicalPath() + "\")).useDelimiter(\"\\\\Z\").next();", ctx);
        } catch (CompileException ce) {
            // this is expected since access to these classes should not be allowed
        }
        System.out.println("result=" + result);
        assertFalse("Vulnerability successfully executed ! File created at " + vulnFile.getCanonicalPath(), vulnFile.exists());
    }

    private Object executeMVEL(String expression, Object ctx) {
        final ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        try {
            ParserConfiguration parserConfiguration = new ParserConfiguration();
            ClassLoader secureFilteringClassLoader = new SecureFilteringClassLoader(getClass().getClassLoader());
            Thread.currentThread().setContextClassLoader(secureFilteringClassLoader);
            parserConfiguration.setClassLoader(secureFilteringClassLoader);
            ParserContext parserContext = new ParserContext(parserConfiguration);
            Serializable compiledExpression = MVEL.compileExpression(expression, parserContext);
            try {
                return MVEL.executeExpression(compiledExpression, ctx, new HashMap());
            } catch (CompileException ce) {
                // this is expected
            }
            return null;
        } finally {
            Thread.currentThread().setContextClassLoader(tccl);
        }
    }

    private static Event generateMockEvent() {
        Event mockEvent = new Event();
        CustomItem targetItem = new CustomItem();
        targetItem.setItemId(MOCK_ITEM_ID);
        targetItem.setScope(DIGITALL_SCOPE);
        mockEvent.setTarget(targetItem);
        Map<String, Object> pageInfoMap = new HashMap<>();
        pageInfoMap.put("pagePath", PAGE_PATH_VALUE);
        pageInfoMap.put("pageURL", PAGE_URL_VALUE);
        targetItem.getProperties().put("pageInfo", pageInfoMap);
        return mockEvent;
    }

}
