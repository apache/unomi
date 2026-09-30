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
package org.apache.unomi.scripting;

import org.apache.unomi.api.CustomItem;
import org.apache.unomi.api.Event;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class MvelScriptExecutorTest {

    MvelScriptExecutor scriptExecutor = new MvelScriptExecutor();

    public static final String MOCK_ITEM_ID = "mockItemId";
    public static final String DIGITALL_SCOPE = "digitall";
    public static final String PAGE_PATH_VALUE = "/site/en/home/aboutus.html";
    public static final String PAGE_URL_VALUE = "http://localhost:8080/site/en/home/aboutus.html";

    @Before
    public void setup() {
        scriptExecutor.setExpressionFilterFactory(emptyAllowList());
    }

    @Test
    public void testAllowlistedArithmeticStillRuns() {
        scriptExecutor.setExpressionFilterFactory(allowAllExpressions());
        Object result = scriptExecutor.execute("1+1", new HashMap<String, Object>());
        assertEquals(2, ((Number) result).intValue());
    }

    @Test
    public void testTighteningPolicyRejectsAnAlreadyCachedScript() {
        // accept and cache the compiled script under a permissive policy
        scriptExecutor.setExpressionFilterFactory(allowAllExpressions());
        assertEquals(2, ((Number) scriptExecutor.execute("1+1", new HashMap<String, Object>())).intValue());
        // tighten the policy at runtime: the same (cached) script must now be rejected, not re-run from cache
        scriptExecutor.setExpressionFilterFactory(emptyAllowList());
        assertNull(scriptExecutor.execute("1+1", new HashMap<String, Object>()));
    }

    @Test
    public void testNestedPublicEvalDoesNotRun() {
        scriptExecutor.setExpressionFilterFactory(allowAllExpressions());
        assertPublicEvalDoesNotReturnTwo("org.mvel2.MVEL.eval(\"1+1\")");
        assertPublicEvalDoesNotReturnTwo("org.mvel2.MVEL.eval ( \"1+1\" )");
        assertPublicEvalDoesNotReturnTwo("org.mvel2.MVEL.ev\u200Bal(\"1+1\")");
        assertPublicEvalDoesNotReturnTwo("org.mvel2.templates.TemplateRuntime.eval(\"1+1\", new java.util.HashMap())");
    }

    @Test
    public void testJsr223ScriptEngineGadgetIsBlocked() {
        // Even with the expression text filter disabled, naming the JSR-223 MVEL engine (a nested-eval
        // sink whose forbid-regex-evading methods are compiledScript/evaluate) must be refused because
        // org.mvel2 is not on the allow list and the class is on the always-forbidden safety net.
        scriptExecutor.setExpressionFilterFactory(allowAllExpressions());
        assertClassLoaderRefused("new org.mvel2.jsr223.MvelScriptEngine().eval(\"1+1\", null)");
        assertClassLoaderRefused(
                "new org.mvel2.jsr223.MvelScriptEngine().evaluate("
                        + "new org.mvel2.jsr223.MvelScriptEngine().compiledScript(\"1+1\"), null)");
    }

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
            result = scriptExecutor.execute("java.io.PrintWriter writer = new java.io.PrintWriter(new java.io.BufferedWriter(new java.io.FileWriter(\"" + vulnFile.getCanonicalPath() + "\", true)));\nwriter.println(\"test\");\nwriter.close();", ctx);
        } catch (Throwable t) {
            // this is expected since access to these classes should not be allowed
            System.out.println("Expected error : " + t.getMessage());
        }
        System.out.println("result=" + result);
        try {
            result = scriptExecutor.execute("import java.util.*;\nimport java.io.*;\nPrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(\"" + vulnFile.getCanonicalPath() + "\", true)));\nwriter.println(\"test\");\nwriter.close();", ctx);
        } catch (Throwable t) {
            // this is expected since access to these classes should not be allowed
            System.out.println("Expected error : " + t.getMessage());
        }
        System.out.println("result=" + result);
        try {
            result = scriptExecutor.execute("import java.util.*;\nimport java.io.*;\nnew Scanner(new File(\"" + vulnFile.getCanonicalPath() + "\")).useDelimiter(\"\\\\Z\").next();", ctx);
        } catch (Throwable t) {
            // this is expected since access to these classes should not be allowed
            System.out.println("Expected error : " + t.getMessage());
        }
        System.out.println("result=" + result);
        try {
            result = scriptExecutor.execute("Runtime r = Runtime.getRuntime(); r.exec(\"touch "+vulnFile.getCanonicalPath()+"\");", ctx);
        } catch (Throwable t) {
            // this is expected since access to these classes should not be allowed
            System.out.println("Expected error : " + t.getMessage());
        }
        System.out.println("result=" + result);
        try {
            result = scriptExecutor.execute("Runtime r = Runtime.getClass().forName(\"java.lang.Runtime\").getDeclaredMethod(\"getRuntime\", null ).invoke(null, null); r.exec(\"touch "+vulnFile.getCanonicalPath()+"\");", ctx);
        } catch (Throwable t) {
            // this is expected since access to these classes should not be allowed
            System.out.println("Expected error : " + t.getMessage());
        }
        System.out.println("result=" + result);

        try {
            ctx.put("goalId", "d; " +
                    "Runtime r = Runtime.getClass().forName(\"java.lang.Runtime\").getDeclaredMethod(\"getRuntime\", null ).invoke(null, null); r.exec(\"touch " +
                    vulnFile.getCanonicalPath() +
                    "\")" +
                    " ; ");
            result = scriptExecutor.execute("'systemProperties\\.goals\\.'+goalId+'TargetReached'", ctx);
        } catch (Throwable t) {
            // this is expected since access to these classes should not be allowed
            System.out.println("Expected error : " + t.getMessage());
        }
        System.out.println("result=" + result);
        assertFalse("Vulnerability successfully executed ! File created at " + vulnFile.getCanonicalPath(), vulnFile.exists());
    }

    @Test
    public void testExpressionCacheIsBoundedAndSkipsRejectedScripts() throws Exception {
        System.setProperty("org.apache.unomi.scripting.mvel.expressions.cache.max.size", "10");
        try {
            MvelScriptExecutor boundedExecutor = new MvelScriptExecutor();
            boundedExecutor.setExpressionFilterFactory(emptyAllowList());
            String padding = "x".repeat(4096);
            Map<String, Object> ctx = new HashMap<>();
            for (int i = 0; i < 50; i++) {
                boundedExecutor.execute("rejected-script-" + i + "-" + padding, ctx);
            }
            Field mvelExpressionsField = MvelScriptExecutor.class.getDeclaredField("mvelExpressions");
            mvelExpressionsField.setAccessible(true);
            Map<?, ?> cache = (Map<?, ?>) mvelExpressionsField.get(boundedExecutor);
            assertEquals("Rejected scripts must not be stored", 0, cache.size());

            boundedExecutor.setExpressionFilterFactory(allowAllExpressions());
            for (int i = 0; i < 30; i++) {
                boundedExecutor.execute(i + "+" + i, ctx);
            }
            assertTrue("Accepted-script cache must be size-bounded but grew to " + cache.size(),
                    cache.size() <= 10);
            for (Object cacheKey : cache.keySet()) {
                assertTrue("Cache keys must be fixed-size hashes, not the raw script text",
                        ((String) cacheKey).length() <= 64);
            }
        } finally {
            System.clearProperty("org.apache.unomi.scripting.mvel.expressions.cache.max.size");
        }
    }

    private void assertPublicEvalDoesNotReturnTwo(String expression) {
        Object result = null;
        try {
            result = scriptExecutor.execute(expression, new HashMap<String, Object>());
        } catch (Throwable t) {
            // expected: class-loader or parser refuses the public eval API
        }
        if (result instanceof Number) {
            assertNotEquals(2, ((Number) result).intValue());
        } else {
            assertNotEquals(2, result);
            assertNotEquals(Integer.valueOf(2), result);
        }
    }

    /**
     * Stronger oracle than {@link #assertPublicEvalDoesNotReturnTwo}: it proves the class-loader deny
     * actually fired (a {@link ClassNotFoundException} for a disallowed class somewhere in the cause
     * chain) rather than the expression merely failing to compile for an unrelated reason.
     */
    private void assertClassLoaderRefused(String expression) {
        Object result = null;
        Throwable caught = null;
        try {
            result = scriptExecutor.execute(expression, new HashMap<String, Object>());
        } catch (Throwable t) {
            caught = t;
        }
        assertNull("expression must not have produced a value: " + expression, result);
        boolean refusedByClassLoader = false;
        for (Throwable t = caught; t != null; t = t.getCause()) {
            if (t instanceof ClassNotFoundException && String.valueOf(t.getMessage()).contains("not allowed")) {
                refusedByClassLoader = true;
                break;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        assertTrue("expected the filtering class loader to refuse a class in: " + expression
                + " but got: " + caught, refusedByClassLoader);
    }

    private static ExpressionFilterFactory emptyAllowList() {
        return new ExpressionFilterFactory() {
            @Override
            public ExpressionFilter getExpressionFilter(String filterCollection) {
                Set<Pattern> allowedExpressions = new HashSet<>();
                Set<Pattern> forbiddenExpressions = new HashSet<>();
                return new ExpressionFilter(allowedExpressions, forbiddenExpressions);
            }
        };
    }

    private static ExpressionFilterFactory allowAllExpressions() {
        return new ExpressionFilterFactory() {
            @Override
            public ExpressionFilter getExpressionFilter(String filterCollection) {
                return new ExpressionFilter(null, null);
            }
        };
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
