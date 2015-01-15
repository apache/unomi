package org.oasis_open.contextserver.rest;

import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.regex.Pattern;

import org.oasis_open.contextserver.api.PluginType;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.wiring.BundleWiring;

public class ResourceBundleHelper {

    private static final Pattern COMMA = Pattern.compile(",", Pattern.LITERAL);

    private static final String RESOURCE_BUNDLE = "messages";

    private BundleContext bundleContext;

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
