package org.oasis_open.contextserver.rest;

import org.oasis_open.contextserver.api.PluginType;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.wiring.BundleWiring;

import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

public class ResourceBundleHelper {

    private BundleContext bundleContext;

    public void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    public ResourceBundle getResourceBundle(PluginType object, String language) {
        Bundle bundle = bundleContext.getBundle(object.getPluginId());
        ClassLoader loader = bundle.adapt(BundleWiring.class).getClassLoader();

        if (language != null) {
            String[] langs = language.split(",");
            for (String lang : langs) {
                int i = lang.indexOf(';');
                if (i > -1) {
                    lang = lang.substring(0, i);
                }
                Locale locale = Locale.forLanguageTag(lang);
                try {
                    ResourceBundle resourceBundle = ResourceBundle.getBundle("messages", locale, loader);
                    if (resourceBundle != null && locale.equals(resourceBundle.getLocale())) {
                        return resourceBundle;
                    }
                } catch (MissingResourceException e) {
                    // continue with next language
                }
            }
        }
        try {
            return ResourceBundle.getBundle("messages", Locale.ENGLISH, loader);
        } catch (MissingResourceException e) {
            return null;
        }
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

}
