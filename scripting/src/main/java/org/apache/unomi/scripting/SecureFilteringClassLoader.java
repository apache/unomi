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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * A class loader that uses a allow list and a deny list of classes that it will allow to resolve. This is useful for providing proper
 * sandboxing to scripting engine such as MVEL or Groovy.
 */
public class SecureFilteringClassLoader extends ClassLoader {

    /**
     * Public eval / runtime entry points that expressions must never load, even when the configured
     * forbid list is empty or the allow list is {@code all}. This is a safety net for a loosened
     * configuration: the primary control is that {@code org.mvel2} is not on the allow list at all,
     * so expressions cannot name any MVEL class. MVEL's own compiler internals are loaded by the
     * MVEL bundle class loader, not through this filtering loader, so denying these does not affect
     * compilation of allow-listed expressions. Wildcard entries cover whole gadget packages so a
     * future MVEL release cannot reopen the hole by adding a new eval-capable class.
     */
    static final Set<String> ALWAYS_FORBIDDEN_CLASSES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "org.mvel2.MVEL",
            "org.mvel2.MVELRuntime",
            "org.mvel2.MVELInterpretedRuntime",
            "org.mvel2.templates.TemplateRuntime",
            "org.mvel2.templates.TemplateCompiler",
            "org.mvel2.MacroProcessor",
            "org.mvel2.jsr223.*",
            "org.mvel2.sh.*"
    )));

    private Set<String> allowedClasses = null;
    private Set<String> forbiddenClasses = null;

    private static Set<String> defaultAllowedClasses = null;
    private static Set<String> defaultForbiddenClasses = null;

    static {
        String systemAllowedClasses = System.getProperty("org.apache.unomi.scripting.allow",
                "org.apache.unomi.api.Event,org.apache.unomi.api.Profile,org.apache.unomi.api.Session,org.apache.unomi.api.Item,org.apache.unomi.api.CustomItem,java.lang.Object,java.util.Map,java.util.HashMap,java.lang.Integer,java.lang.String");
        if (systemAllowedClasses != null) {
            if ("all".equals(systemAllowedClasses.trim())) {
                defaultAllowedClasses = null;
            } else {
                if (systemAllowedClasses.trim().length() > 0) {
                    defaultAllowedClasses = parseClassList(systemAllowedClasses);
                } else {
                    defaultAllowedClasses = null;
                }
            }
        }

        String systemForbiddenClasses = System.getProperty("org.apache.unomi.scripting.forbid", "org.mvel2.MVEL");
        if (systemForbiddenClasses != null && systemForbiddenClasses.trim().length() > 0) {
            defaultForbiddenClasses = parseClassList(systemForbiddenClasses);
        } else {
            defaultForbiddenClasses = null;
        }
    }

    ClassLoader delegate;

    /**
     * Sets up the securing filtering class loader, using the default allowed and forbidden classes. By default the
     * @param delegate the class loader we delegate to if the filtering was not applied.
     */
    public SecureFilteringClassLoader(ClassLoader delegate) {
        this(defaultAllowedClasses, defaultForbiddenClasses, delegate);
    }

    /**
     * Sets up the secure filtering class loader
     * @param allowedClasses the list of allowed FQN class names, or if this filtering is to be deactivated, pass null.
     *                       if you want to allow no class, pass an empty hashset
     * @param forbiddenClasses the list of forbidden FQN class names, or if this filtering is to be deactivated, pass null or an empty set
     *
     * @param delegate the class loader we delegate to if the filtering was not applied.
     */
    public SecureFilteringClassLoader(Set<String> allowedClasses, Set<String> forbiddenClasses, ClassLoader delegate) {
        super(delegate);
        this.allowedClasses = allowedClasses;
        this.forbiddenClasses = forbiddenClasses;
        this.delegate = delegate;
    }

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        assertPermitted(name);
        return delegate.loadClass(name);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        assertPermitted(name);
        return super.loadClass(name, resolve);
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        assertPermitted(name);
        return super.findClass(name);
    }

    private void assertPermitted(String name) throws ClassNotFoundException {
        if (classNameMatches(ALWAYS_FORBIDDEN_CLASSES, name) ||
                (forbiddenClasses != null && classNameMatches(forbiddenClasses, name))) {
            throw new ClassNotFoundException("Access to class " + name + " not allowed");
        }
        if (allowedClasses != null && !classNameMatches(allowedClasses, name)) {
            throw new ClassNotFoundException("Access to class " + name + " not allowed");
        }
    }

    private static Set<String> parseClassList(String classList) {
        Set<String> classes = new HashSet<>();
        for (String part : classList.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                classes.add(trimmed);
            }
        }
        return classes;
    }

    static boolean classNameMatches(Set<String> classesToTest, String className) {
        String normalized = normalizeClassName(className);
        for (String classToTest : classesToTest) {
            if (classToTest == null) {
                continue;
            }
            String pattern = classToTest.trim();
            if (pattern.isEmpty()) {
                continue;
            }
            if (pattern.endsWith("*")) {
                String prefix = pattern.substring(0, pattern.length() - 1);
                if (normalized.startsWith(prefix)) {
                    return true;
                }
            } else if (normalized.equals(pattern) || normalized.startsWith(pattern + "$")) {
                return true;
            }
        }
        return false;
    }

    static String normalizeClassName(String className) {
        if (className == null) {
            return "";
        }
        String name = stripInvisibleCharacters(className).trim().replace('/', '.');
        while (name.startsWith("[")) {
            if (name.startsWith("[L") && name.endsWith(";")) {
                name = name.substring(2, name.length() - 1);
            } else if (name.length() >= 2) {
                name = name.substring(1);
            } else {
                break;
            }
        }
        return name;
    }

    static String stripInvisibleCharacters(String input) {
        StringBuilder stripped = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); ) {
            int codePoint = input.codePointAt(i);
            i += Character.charCount(codePoint);
            if (codePoint == 0) {
                continue;
            }
            int type = Character.getType(codePoint);
            if (type == Character.FORMAT || type == Character.CONTROL || type == Character.SURROGATE) {
                continue;
            }
            stripped.appendCodePoint(codePoint);
        }
        return stripped.toString();
    }
}
