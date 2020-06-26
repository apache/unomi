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

package org.apache.unomi.rest;

import org.apache.unomi.api.PluginType;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;

import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.regex.Pattern;

@Component(service=ResourceBundleHelper.class)
public class ResourceBundleHelper {

    private static final Pattern COMMA = Pattern.compile(",", Pattern.LITERAL);

    private static final String RESOURCE_BUNDLE = "messages";

    private BundleContext bundleContext;

    @Activate
    public void activate(ComponentContext componentContext) {
        this.bundleContext = componentContext.getBundleContext();
    }

    private ResourceBundle getBundle(String lang, Bundle bundle, ClassLoader loader) {
        Locale locale = getLocale(lang);
        try {
            ResourceBundle resourceBundle = ResourceBundle.getBundle(RESOURCE_BUNDLE, locale, loader);
            if (resourceBundle != null && locale.equals(resourceBundle.getLocale())) {
                return resourceBundle;
            }
        } catch (MissingResourceException e) {
            // continue with next language
        }

        if (locale.getCountry().length() > 0) {
            // try the locale without the country
            return getBundle(locale.getLanguage(), bundle, loader);
        }

        return null;
    }

    private Locale getLocale(String lang) {
        int i = lang.indexOf(';');
        if (i > -1) {
            lang = lang.substring(0, i);
        }
        return Locale.forLanguageTag(lang);
    }

    public ResourceBundle getResourceBundle(PluginType object, String language) {
        ResourceBundle resourceBundle = null;

        Bundle bundle = bundleContext.getBundle(object.getPluginId());
        ClassLoader loader = bundle.adapt(BundleWiring.class).getClassLoader();

        if (language != null) {
            if (language.indexOf(',') != -1) {
                String[] langs = COMMA.split(language);
                for (String lang : langs) {
                    resourceBundle = getBundle(lang, bundle, loader);
                    if (resourceBundle != null) {
                        break;
                    }
                }
            } else {
                resourceBundle = getBundle(language, bundle, loader);
            }
        }
        if (resourceBundle == null) {
            try {
                return ResourceBundle.getBundle(RESOURCE_BUNDLE, Locale.ENGLISH, loader);
            } catch (MissingResourceException e) {
                // ignore
            }
        }

        return resourceBundle;
    }

    public String getResourceBundleValue(ResourceBundle bundle, String nameKey) {
        try {
            if (bundle != null) {
                return bundle.getString(nameKey);
            }
        } catch (MissingResourceException e) {
            // Continue
        }
        return "???" + nameKey + "???";
    }

    public void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }
}
