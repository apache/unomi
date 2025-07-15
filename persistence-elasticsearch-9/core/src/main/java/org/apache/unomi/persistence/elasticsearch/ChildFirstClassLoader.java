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
package org.apache.unomi.persistence.elasticsearch;
import java.net.URL;
import java.net.URLClassLoader;

/**
 * This class loader will always try to load classes first from the child URL class loader and will only resort to the
 * parent class loader if the class coudln't be found.
 */
public class ChildFirstClassLoader extends ClassLoader {

    private ChildFirstURLClassLoader childFirstURLClassLoader;

    private static class ChildFirstURLClassLoader extends URLClassLoader {
        private ClassLoader parentClassLoader;

        public ChildFirstURLClassLoader(URL[] urls, ClassLoader parentClassLoader) {
            super(urls, null);

            this.parentClassLoader = parentClassLoader;
        }

        @Override
        public Class<?> findClass(String name) throws ClassNotFoundException {
            try {
                return super.findClass(name);
            } catch (ClassNotFoundException e) {
                return parentClassLoader.loadClass(name);
            }
        }
    }

    public ChildFirstClassLoader(ClassLoader parent, URL[] urls) {
        super(parent);
        childFirstURLClassLoader = new ChildFirstURLClassLoader(urls, parent);
    }

    @Override
    protected synchronized Class<?> loadClass(String name, boolean resolve)
            throws ClassNotFoundException {
        try {
            return childFirstURLClassLoader.loadClass(name);
        } catch (ClassNotFoundException e) {
            return super.loadClass(name, resolve);
        }
    }

}