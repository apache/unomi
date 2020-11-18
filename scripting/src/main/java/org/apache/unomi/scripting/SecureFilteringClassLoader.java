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
import java.util.HashSet;
import java.util.Set;

/**
 * A class loader that uses a allow list and a deny list of classes that it will allow to resolve. This is useful for providing proper
 * sandboxing to scripting engine such as MVEL, OGNL or Groovy.
 */
public class SecureFilteringClassLoader extends ClassLoader {

    private Set<String> allowedClasses = null;
    private Set<String> forbiddenClasses = null;

    private static Set<String> defaultAllowedClasses = null;
    private static Set<String> defaultForbiddenClasses = null;

    static {
        String systemAllowedClasses = System.getProperty("org.apache.unomi.scripting.allow",
                "org.apache.unomi.api.Event,org.apache.unomi.api.Profile,org.apache.unomi.api.Session,org.apache.unomi.api.Item,org.apache.unomi.api.CustomItem,ognl.*,java.lang.Object,java.util.Map,java.util.HashMap,java.lang.Integer,org.mvel2.*,java.lang.String");
        if (systemAllowedClasses != null) {
            if ("all".equals(systemAllowedClasses.trim())) {
                defaultAllowedClasses = null;
            } else {
                if (systemAllowedClasses.trim().length() > 0) {
                    String[] systemAllowedClassesParts = systemAllowedClasses.split(",");
                    defaultAllowedClasses = new HashSet<>();
                    defaultAllowedClasses.addAll(Arrays.asList(systemAllowedClassesParts));
                } else {
                    defaultAllowedClasses = null;
                }
            }
        }

        String systemForbiddenClasses = System.getProperty("org.apache.unomi.scripting.forbid", null);
        if (systemForbiddenClasses != null) {
            if (systemForbiddenClasses.trim().length() > 0) {
                String[] systemForbiddenClassesParts = systemForbiddenClasses.split(",");
                defaultForbiddenClasses = new HashSet<>();
                defaultForbiddenClasses.addAll(Arrays.asList(systemForbiddenClassesParts));
            } else {
                defaultForbiddenClasses = null;
            }
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
        if (forbiddenClasses != null && classNameMatches(forbiddenClasses, name)) {
            throw new ClassNotFoundException("Access to class " + name + " not allowed");
        }
        if (allowedClasses != null && !classNameMatches(allowedClasses, name)) {
            throw new ClassNotFoundException("Access to class " + name + " not allowed");
        }
        return delegate.loadClass(name);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (forbiddenClasses != null && classNameMatches(forbiddenClasses, name)) {
            throw new ClassNotFoundException("Access to class " + name + " not allowed");
        }
        if (allowedClasses != null && !classNameMatches(allowedClasses, name)) {
            throw new ClassNotFoundException("Access to class " + name + " not allowed");
        }
        return super.loadClass(name, resolve);
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        return super.findClass(name);
    }

    private boolean classNameMatches(Set<String> classesToTest, String className) {
        for (String classToTest : classesToTest) {
            if (classToTest.endsWith("*")) {
                if (className.startsWith(classToTest.substring(0, classToTest.length() - 1))) return true;
            } else {
                if (className.equals(classToTest)) return true;
            }
        }
        return false;
    }

}
