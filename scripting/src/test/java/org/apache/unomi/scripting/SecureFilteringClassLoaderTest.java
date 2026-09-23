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

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class SecureFilteringClassLoaderTest {

    private final ClassLoader parent = SecureFilteringClassLoaderTest.class.getClassLoader();

    @Test
    public void loadClass_allowsListedExactName() throws ClassNotFoundException {
        Set<String> allowed = new HashSet<>(Collections.singletonList("java.lang.String"));
        SecureFilteringClassLoader loader = new SecureFilteringClassLoader(allowed, null, parent);
        assertEquals(String.class, loader.loadClass("java.lang.String"));
    }

    @Test
    public void loadClass_allowsWildcardPrefix() throws ClassNotFoundException {
        Set<String> allowed = new HashSet<>(Collections.singletonList("org.mvel2.*"));
        SecureFilteringClassLoader loader = new SecureFilteringClassLoader(allowed, null, parent);
        assertEquals(org.mvel2.compiler.CompiledExpression.class,
                loader.loadClass("org.mvel2.compiler.CompiledExpression"));
    }

    @Test
    public void loadClass_alwaysForbidsPublicEvalApiEvenWhenForbidListIsEmpty() {
        Set<String> allowed = new HashSet<>(Collections.singletonList("org.mvel2.*"));
        SecureFilteringClassLoader loader = new SecureFilteringClassLoader(allowed, Collections.emptySet(), parent);
        ClassNotFoundException thrown = assertThrows(ClassNotFoundException.class,
                () -> loader.loadClass("org.mvel2.MVEL"));
        assertEquals("Access to class org.mvel2.MVEL not allowed", thrown.getMessage());
    }

    @Test
    public void loadClass_forbidsInnerClassAndArrayAndSlashForms() {
        Set<String> allowed = new HashSet<>(Collections.singletonList("org.mvel2.*"));
        SecureFilteringClassLoader loader = new SecureFilteringClassLoader(allowed, null, parent);
        assertEquals("Access to class org.mvel2.MVEL$Foo not allowed",
                assertThrows(ClassNotFoundException.class, () -> loader.loadClass("org.mvel2.MVEL$Foo")).getMessage());
        assertEquals("Access to class [Lorg.mvel2.MVEL; not allowed",
                assertThrows(ClassNotFoundException.class, () -> loader.loadClass("[Lorg.mvel2.MVEL;")).getMessage());
        assertEquals("Access to class org/mvel2/MVEL not allowed",
                assertThrows(ClassNotFoundException.class, () -> loader.loadClass("org/mvel2/MVEL")).getMessage());
    }

    @Test
    public void loadClass_forbidsEvalApiWhenInvisibleCharactersAreInserted() {
        Set<String> allowed = new HashSet<>(Collections.singletonList("org.mvel2.*"));
        SecureFilteringClassLoader loader = new SecureFilteringClassLoader(allowed, null, parent);
        assertThrows(ClassNotFoundException.class, () -> loader.loadClass("org.mvel2.MV\u200BEL"));
    }

    @Test
    public void loadClass_forbidsTemplateRuntime() {
        Set<String> allowed = new HashSet<>(Collections.singletonList("org.mvel2.*"));
        SecureFilteringClassLoader loader = new SecureFilteringClassLoader(allowed, null, parent);
        assertThrows(ClassNotFoundException.class,
                () -> loader.loadClass("org.mvel2.templates.TemplateRuntime"));
    }

    @Test
    public void loadClass_forbidsJsr223AndShellGadgetsEvenWhenAllowAll() {
        // allowedClasses == null models "allow=all"; the always-forbidden safety net must still deny
        // the JSR-223 script engine and the MVEL shell, whole packages, not just the six named classes.
        SecureFilteringClassLoader loader = new SecureFilteringClassLoader(null, Collections.emptySet(), parent);
        assertThrows(ClassNotFoundException.class, () -> loader.loadClass("org.mvel2.jsr223.MvelScriptEngine"));
        assertThrows(ClassNotFoundException.class, () -> loader.loadClass("org.mvel2.jsr223.MvelCompiledScript"));
        assertThrows(ClassNotFoundException.class, () -> loader.loadClass("org.mvel2.jsr223.MvelScriptEngineFactory"));
        assertThrows(ClassNotFoundException.class, () -> loader.loadClass("org.mvel2.sh.ShellSession"));
        assertThrows(ClassNotFoundException.class, () -> loader.loadClass("org.mvel2.sh.command.basic.ObjectInspector"));
    }

    @Test
    public void loadClass_deniesAllMvelClassesUnderTheDefaultNarrowedAllowList() {
        // The shipped default allow list contains no org.mvel2 entry, so no MVEL class is loadable from
        // an expression, while a legitimately allow-listed JDK class still resolves.
        Set<String> allowed = new HashSet<>(Arrays.asList(
                "org.apache.unomi.api.Event", "java.lang.Object", "java.util.Map",
                "java.util.HashMap", "java.lang.Integer", "java.lang.String"));
        SecureFilteringClassLoader loader = new SecureFilteringClassLoader(allowed, null, parent);
        assertThrows(ClassNotFoundException.class, () -> loader.loadClass("org.mvel2.MVEL"));
        assertThrows(ClassNotFoundException.class, () -> loader.loadClass("org.mvel2.jsr223.MvelScriptEngine"));
        assertThrows(ClassNotFoundException.class, () -> loader.loadClass("org.mvel2.compiler.ExpressionCompiler"));
        assertThrows(ClassNotFoundException.class, () -> loader.loadClass("org.mvel2.PropertyAccessor"));
    }

    @Test
    public void classNameMatches_trimsConfiguredPatterns() {
        Set<String> forbidden = new HashSet<>(Collections.singletonList(" org.mvel2.MVEL "));
        assertTrue(SecureFilteringClassLoader.classNameMatches(forbidden, "org.mvel2.MVEL"));
        assertFalse(SecureFilteringClassLoader.classNameMatches(forbidden, "org.mvel2.compiler.CompiledExpression"));
    }
}
