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
package org.apache.unomi.services.impl;

import org.osgi.framework.*;

import java.io.File;
import java.io.InputStream;
import java.util.Collection;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestBundleContext implements BundleContext {
    private final Map<Long, Bundle> bundles = new HashMap<>();
    private final Bundle bundle;

    public TestBundleContext() {
        this.bundle = mock(Bundle.class);
        when(bundle.getBundleContext()).thenReturn(this);
    }

    public void addBundle(Bundle bundle) {
        bundles.put(bundle.getBundleId(), bundle);
    }

    @Override
    public Bundle getBundle() {
        return bundle;
    }

    @Override
    public Bundle[] getBundles() {
        return bundles.values().toArray(new Bundle[0]);
    }

    @Override
    public Bundle getBundle(long id) {
        return bundles.get(id);
    }

    @Override
    public Bundle getBundle(String location) {
        return null;
    }

    // Unimplemented methods below - we only implement what we need for tests

    @Override
    public String getProperty(String key) {
        return null;
    }

    @Override
    public Bundle installBundle(String location, InputStream input) {
        return null;
    }

    @Override
    public Bundle installBundle(String location) {
        return null;
    }

    @Override
    public void addServiceListener(ServiceListener listener, String filter) {
    }

    @Override
    public void addServiceListener(ServiceListener listener) {
    }

    @Override
    public void removeServiceListener(ServiceListener listener) {
    }

    @Override
    public void addBundleListener(BundleListener listener) {
    }

    @Override
    public void removeBundleListener(BundleListener listener) {
    }

    @Override
    public void addFrameworkListener(FrameworkListener listener) {
    }

    @Override
    public void removeFrameworkListener(FrameworkListener listener) {
    }

    @Override
    public ServiceRegistration<?> registerService(String[] clazzes, Object service, Dictionary<String, ?> properties) {
        return null;
    }

    @Override
    public ServiceRegistration<?> registerService(String clazz, Object service, Dictionary<String, ?> properties) {
        return null;
    }

    @Override
    public <S> ServiceRegistration<S> registerService(Class<S> clazz, S service, Dictionary<String, ?> properties) {
        return null;
    }

    @Override
    public <S> ServiceRegistration<S> registerService(Class<S> clazz, ServiceFactory<S> factory, Dictionary<String, ?> properties) {
        return null;
    }

    @Override
    public ServiceReference<?>[] getServiceReferences(String clazz, String filter) {
        return new ServiceReference[0];
    }

    @Override
    public ServiceReference<?>[] getAllServiceReferences(String clazz, String filter) {
        return new ServiceReference[0];
    }

    @Override
    public ServiceReference<?> getServiceReference(String clazz) {
        return null;
    }

    @Override
    public <S> ServiceReference<S> getServiceReference(Class<S> clazz) {
        return null;
    }

    @Override
    public <S> Collection<ServiceReference<S>> getServiceReferences(Class<S> clazz, String filter) {
        return null;
    }

    @Override
    public <S> S getService(ServiceReference<S> reference) {
        return null;
    }

    @Override
    public boolean ungetService(ServiceReference<?> reference) {
        return false;
    }

    @Override
    public <S> ServiceObjects<S> getServiceObjects(ServiceReference<S> reference) {
        return null;
    }

    @Override
    public File getDataFile(String filename) {
        return null;
    }

    @Override
    public Filter createFilter(String filter) {
        return null;
    }
}
