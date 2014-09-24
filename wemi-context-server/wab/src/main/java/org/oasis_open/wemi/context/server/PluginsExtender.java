package org.oasis_open.wemi.context.server;

import org.ops4j.pax.cdi.api.OsgiService;
import org.ops4j.pax.cdi.api.OsgiServiceProvider;
import org.ops4j.pax.web.service.WebContainer;
import org.osgi.framework.*;
import org.osgi.service.http.HttpContext;
import org.osgi.service.http.NamespaceException;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Default;
import javax.inject.Inject;
import javax.servlet.Filter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by loom on 18.08.14.
 */
@ApplicationScoped
@Default
@OsgiServiceProvider // we set this annotation just to make sure that the bean will be eagerly instantiated, which is not the cleanest way of doing this
public class PluginsExtender implements SynchronousBundleListener {

    @Inject
    private BundleContext bundleContext;

    @Inject
    @OsgiService
    private WebContainer webContainer;

    private Map<BundleContext, List<String>> registeredAliases = new HashMap<BundleContext, List<String>>();
    private Map<BundleContext, Filter> registeredFilters = new HashMap<BundleContext, Filter>();

    /**
     * Normalize the path for accesing a resource, meaning that will replace
     * consecutive slashes and will remove a leading slash if present.
     *
     * @param path path to normalize
     * @return normalized path or the original path if there is nothing to be
     * replaced.
     */
    public static String normalizeResourcePath(final String path) {
        if (path == null) {
            return null;
        }
        String normalizedPath = replaceSlashes(path.trim());
        if (normalizedPath.startsWith("/") && normalizedPath.length() > 1) {
            normalizedPath = normalizedPath.substring(1);
        }
        return normalizedPath;
    }

    /**
     * Replaces multiple subsequent slashes with one slash. E.g. ////a//path//
     * will becaome /a/path/
     *
     * @param target target sring to be replaced
     * @return a string where the subsequent slashes are replaced with one slash
     */
    static String replaceSlashes(final String target) {
        String replaced = target;
        if (replaced != null) {
            replaced = replaced.replaceAll("/+", "/");
        }
        return replaced;
    }

    @PostConstruct
    public void postConstruct() {
        for (Bundle otherBundle : bundleContext.getBundles()) {
            if (otherBundle.getBundleContext() != null) {
                registerHttpResources(otherBundle.getBundleContext());
            }
        }
        bundleContext.addBundleListener(this);
    }

    @PreDestroy
    public void preDestroy() {
        bundleContext.removeBundleListener(this);
        for (List<String> aliasList : registeredAliases.values()) {
            for (String alias : aliasList) {
                webContainer.unregister(alias);
            }
        }
        for (Filter pluginFilter : registeredFilters.values()) {
            webContainer.unregisterFilter(pluginFilter);
        }
    }

    public void bundleChanged(BundleEvent event) {
        switch (event.getType()) {
            case BundleEvent.STARTED:
                if (event.getBundle().getBundleContext() != null) {
                    registerHttpResources(event.getBundle().getBundleContext());
                }
                break;
            case BundleEvent.STOPPING:
                if (event.getBundle().getBundleContext() != null) {
                    unregisterHttpResources(event.getBundle().getBundleContext());
                }
                break;
        }
    }

    private void registerHttpResources(BundleContext bundleContext) {
        String httpResourcesHeaderValue = bundleContext.getBundle().getHeaders().get("Wemi-Http-Resources");
        if (httpResourcesHeaderValue == null || httpResourcesHeaderValue.length() == 0) {
            return;
        }

        if (registeredAliases.containsKey(bundleContext)) {
            unregisterHttpResources(bundleContext);
        }

        HttpContext httpContext = new CustomHttpContext(bundleContext.getBundle());

        String[] urlPatterns = {
                "/plugins/" + bundleContext.getBundle().getSymbolicName() + "/*"
        };
        Filter pluginFilter = new PluginsCorsFilter();
        webContainer.registerFilter(pluginFilter, urlPatterns, null, null, httpContext);
        registeredFilters.put(bundleContext, pluginFilter);

        List<String> aliasList = new ArrayList<String>();

        String[] httpResourcePairs = httpResourcesHeaderValue.split(",");
        for (String httpResourcePair : httpResourcePairs) {
            httpResourcePair = httpResourcePair.trim();
            String[] httpResourceParts = httpResourcePair.split("=");
            if (httpResourceParts == null || httpResourceParts.length != 2) {
                continue;
            }
            if (bundleContext.getBundle().getEntry(httpResourceParts[1].trim()) != null) {
                try {
                    String alias = "/plugins/" + bundleContext.getBundle().getSymbolicName() + httpResourceParts[0].trim();
                    if (alias.endsWith("/")) {
                        alias = alias.substring(0, alias.length() - 1);
                    }
                    webContainer.registerResources(alias, httpResourceParts[1].trim(), httpContext);
                    aliasList.add(alias);

                } catch (NamespaceException e) {
                    e.printStackTrace();
                }
            }
        }

        if (aliasList.size() > 0) {
            registeredAliases.put(bundleContext, aliasList);
        }
    }

    private void unregisterHttpResources(BundleContext bundleContext) {
        List<String> aliasList = registeredAliases.remove(bundleContext);
        if (aliasList != null) {
            for (String alias : aliasList) {
                webContainer.unregister(alias);
            }
        }
        Filter filter = registeredFilters.remove(bundleContext);
        if (filter != null) {
            webContainer.unregisterFilter(filter);
        }
    }

    public class CustomHttpContext implements HttpContext {

        /**
         * Bundle using the {@link org.osgi.service.http.HttpService}.
         */
        private final Bundle bundle;

        public CustomHttpContext(Bundle bundle) {
            this.bundle = bundle;
        }

        public boolean handleSecurity(HttpServletRequest request, HttpServletResponse response) throws IOException {
            return true;
        }

        public URL getResource(String name) {
            final String normalizedname = normalizeResourcePath(name);
            return bundle.getResource(normalizedname);
        }

        public String getMimeType(String name) {
            return null;
        }


    }

}
