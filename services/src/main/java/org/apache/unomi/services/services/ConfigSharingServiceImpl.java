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
package org.apache.unomi.services.services;

import org.apache.unomi.api.services.ConfigSharingService;
import org.osgi.framework.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An implementation of the ConfigSharingService that supports listeners that will be called when a property is added,
 * updated or removed. The properties are stored in a ConcurrentHashMap so access should be thread-safe.
 */
public class ConfigSharingServiceImpl implements ConfigSharingService, SynchronousBundleListener {

    private static final Logger logger = LoggerFactory.getLogger(ConfigSharingServiceImpl.class);

    private BundleContext bundleContext;
    private Map<String,Object> configProperties = new ConcurrentHashMap<String,Object>();

    public void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    public void setConfigProperties(Map<String, Object> configProperties) {
        this.configProperties = configProperties;
    }

    @Override
    public Object getProperty(String name) {
        return configProperties.get(name);
    }

    @Override
    public Object setProperty(String name, Object newValue) {
        boolean existed = false;
        if (configProperties.containsKey(name)) {
            existed = true;
        }
        Object oldValue = configProperties.put(name, newValue);
        if (existed) {
            firePropertyUpdatedEvent(name, oldValue, newValue);
        } else {
            firePropertyAddedEvent(name, newValue);
        }
        return oldValue;
    }

    @Override
    public boolean hasProperty(String name) {
        return configProperties.containsKey(name);
    }

    @Override
    public Object removeProperty(String name) {
        boolean existed = false;
        if (configProperties.containsKey(name)) {
            existed = true;
        }
        Object oldValue = configProperties.remove(name);
        if (existed) {
            firePropertyRemovedEvent(name, oldValue);
        }
        return oldValue;
    }

    @Override
    public Set<String> getPropertyNames() {
        return configProperties.keySet();
    }

    public void preDestroy() throws Exception {
        bundleContext.removeBundleListener(this);
        logger.info("Config sharing service for Service is shutdown.");
    }

    private void processBundleStartup(BundleContext bundleContext) {
        if (bundleContext == null) {
            return;
        }
    }

    @Override
    public void bundleChanged(BundleEvent bundleEvent) {

    }

    private void firePropertyAddedEvent(String name, Object newValue) {
        fireConfigChangeEvent(new ConfigChangeEvent(ConfigChangeEvent.ConfigChangeEventType.ADDED, name, null, newValue));
    }

    private void firePropertyUpdatedEvent(String name, Object oldValue, Object newValue) {
        fireConfigChangeEvent(new ConfigChangeEvent(ConfigChangeEvent.ConfigChangeEventType.UPDATED, name, oldValue, newValue));
    }

    private void firePropertyRemovedEvent(String name, Object oldValue) {
        fireConfigChangeEvent(new ConfigChangeEvent(ConfigChangeEvent.ConfigChangeEventType.REMOVED, name, oldValue, null));
    }

    private void fireConfigChangeEvent(ConfigChangeEvent configChangeEvent) {
        List<ConfigChangeListener> listeners = getListeners();
        for (ConfigChangeListener configChangeListener : listeners) {
            configChangeListener.configChanged(configChangeEvent);
        }
    }

    /**
     * This method is called a lot because this list may change at any time as listeners may come and go in OSGi
     * @return a list of ConfigChangeListeners that will be used to listen to property changes
     */
    private List<ConfigChangeListener> getListeners() {
        List<ConfigChangeListener> listeners = new ArrayList<>();
        try {
            ServiceReference<?>[] allListenerReferences = bundleContext.getAllServiceReferences(ConfigChangeListener.class.getName(), null);
            if (allListenerReferences == null) {
                return listeners;
            }
            for (ServiceReference<?> listenerReference : allListenerReferences) {
                ConfigChangeListener configChangeListener = (ConfigChangeListener) bundleContext.getService(listenerReference);
                if (configChangeListener != null) {
                    listeners.add(configChangeListener);
                }
            }
        } catch (InvalidSyntaxException e) {
            logger.error("Error retrieving listeners", e);
            return listeners;
        }
        return listeners;
    }

}
