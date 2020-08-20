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
package org.apache.unomi.scripting.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.unomi.scripting.ExpressionFilter;
import org.apache.unomi.scripting.ExpressionFilterFactory;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.regex.Pattern;

public class ExpressionFilterFactoryImpl implements ExpressionFilterFactory,BundleListener {

    private static final Logger logger = LoggerFactory.getLogger(ExpressionFilterFactoryImpl.class.getName());

    private final Map<Bundle,Map<String,Set<Pattern>>> allowedExpressionPatternsByBundle = new HashMap<>();

    private final Map<String,Set<Pattern>> allowedExpressionPatternsByCollection = new HashMap<>();
    private final Map<String,Set<Pattern>> forbiddenExpressionPatternsByCollection = new HashMap<>();

    private BundleContext bundleContext = null;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private boolean expressionFiltersActivated = Boolean.parseBoolean(System.getProperty("org.apache.unomi.scripting.filter.activated", "true"));

    public void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    public ExpressionFilterFactoryImpl() {
    }

    public void init() {
        String initialFilterCollections = System.getProperty("org.apache.unomi.scripting.filter.collections", "mvel,ognl");
        String[] initialFilterCollectionParts = initialFilterCollections.split(",");
        if (initialFilterCollectionParts != null) {
            for (String initialFilterCollection : initialFilterCollectionParts) {
                String systemAllowedPatterns = System.getProperty("org.apache.unomi.scripting.filter."+initialFilterCollection+".allow", null);
                if (systemAllowedPatterns != null) {
                    Set<Pattern> collectionAllowedExpressionPatterns = new HashSet<>();
                    if (!"all".equals(systemAllowedPatterns.trim())) {
                        collectionAllowedExpressionPatterns = null;
                    } else {
                        if (systemAllowedPatterns.trim().length() > 0) {
                            String[] systemAllowedPatternParts = systemAllowedPatterns.split(",");
                            collectionAllowedExpressionPatterns = new HashSet<>();
                            for (String systemAllowedPatternPart : systemAllowedPatternParts) {
                                collectionAllowedExpressionPatterns.add(Pattern.compile(systemAllowedPatternPart));
                            }
                        }
                    }
                    allowedExpressionPatternsByCollection.put(initialFilterCollection, collectionAllowedExpressionPatterns);
                }

                String systemForbiddenPatterns = System.getProperty("org.apache.unomi.scripting.filter."+initialFilterCollection+".forbid", ".*Runtime.*,.*ProcessBuilder.*,.*exec.*,.*invoke.*,.*getClass.*,.*Class.*,.*ClassLoader.*,.*System.*,.*Method.*,.*method.*,.*Compiler.*,.*Thread.*,.*FileWriter.*,.*forName.*,.*Socket.*,.*DriverManager.*,eval");
                if (systemForbiddenPatterns != null) {
                    Set<Pattern> collectionForbiddenExpressionPatterns = new HashSet<>();
                    if (systemForbiddenPatterns.trim().length() > 0) {
                        String[] systemForbiddenPatternParts = systemForbiddenPatterns.split(",");
                        collectionForbiddenExpressionPatterns = new HashSet<>();
                        for (String systemForbiddenPatternPart : systemForbiddenPatternParts) {
                            collectionForbiddenExpressionPatterns.add(Pattern.compile(systemForbiddenPatternPart));
                        }
                    } else {
                        collectionForbiddenExpressionPatterns = null;
                    }
                    forbiddenExpressionPatternsByCollection.put(initialFilterCollection, collectionForbiddenExpressionPatterns);
                }
            }
        }

        if (bundleContext != null) {
            loadPredefinedAllowedPatterns(bundleContext);
            for (Bundle bundle : bundleContext.getBundles()) {
                if (bundle.getBundleContext() != null && bundle.getBundleId() != bundleContext.getBundle().getBundleId()) {
                    loadPredefinedAllowedPatterns(bundle.getBundleContext());
                }
            }

            bundleContext.addBundleListener(this);
        }

    }

    public void destroy() {
        if (bundleContext != null) {
            bundleContext.removeBundleListener(this);
        }
    }

    public void bundleChanged(BundleEvent event) {
        switch (event.getType()) {
            case BundleEvent.STARTED:
                processBundleStartup(event.getBundle().getBundleContext());
                break;
            case BundleEvent.STOPPING:
                processBundleStop(event.getBundle().getBundleContext());
                break;
        }
    }

    private void processBundleStartup(BundleContext bundleContext) {
        if (bundleContext == null) {
            return;
        }
        loadPredefinedAllowedPatterns(bundleContext);
    }

    private void processBundleStop(BundleContext bundleContext) {
        if (bundleContext == null) {
            return;
        }
        removePredefinedAllowedPatterns(bundleContext);
    }

    private void loadPredefinedAllowedPatterns(BundleContext bundleContext) {
        Enumeration<URL> predefinedAllowedExpressions = bundleContext.getBundle().findEntries("META-INF/cxs/expressions", "*.json", true);
        if (predefinedAllowedExpressions == null) {
            return;
        }

        Map<String,Set<Pattern>> predefinedAllowedExpressionsForBundle = new HashMap<>();

        while (predefinedAllowedExpressions.hasMoreElements()) {
            URL predefinedAllowedExpressionsURL = predefinedAllowedExpressions.nextElement();
            logger.debug("Found predefined allowed expressions at " + predefinedAllowedExpressionsURL + ", loading... ");
            try {
                JsonNode predefinedAllowedExpressionsNode = objectMapper.readTree(predefinedAllowedExpressionsURL);
                Set<Pattern> bundleAllowedExpressions = new HashSet<>();
                for (JsonNode predefinedAllowedExpressionNode : predefinedAllowedExpressionsNode) {
                    bundleAllowedExpressions.add(Pattern.compile(predefinedAllowedExpressionNode.asText()));
                }
                String collection = predefinedAllowedExpressionsURL.getFile().substring("/META-INF/cxs/expressions/".length(), predefinedAllowedExpressionsURL.getFile().length() - ".json".length());
                predefinedAllowedExpressionsForBundle.put(collection, bundleAllowedExpressions);
                Set<Pattern> existingAllowedExpressions = allowedExpressionPatternsByCollection.get(collection);
                if (existingAllowedExpressions == null) {
                    existingAllowedExpressions = new HashSet<>();
                }
                existingAllowedExpressions.addAll(bundleAllowedExpressions);
                allowedExpressionPatternsByCollection.put(collection, existingAllowedExpressions);
            } catch (IOException e) {
                logger.error("Error while loading expressions definition " + predefinedAllowedExpressionsURL, e);
            }
        }

        allowedExpressionPatternsByBundle.put(bundleContext.getBundle(), predefinedAllowedExpressionsForBundle);
    }

    private void removePredefinedAllowedPatterns(BundleContext bundleContext) {
        Map<String,Set<Pattern>> allowedExpressionPatternsForBundle = allowedExpressionPatternsByBundle.get(bundleContext.getBundle());
        for (Map.Entry<String,Set<Pattern>> allowedExpressionPatternsEntry : allowedExpressionPatternsForBundle.entrySet()) {
            Set<Pattern> allowedExpressionPatterns = allowedExpressionPatternsByCollection.get(allowedExpressionPatternsEntry.getKey());
            allowedExpressionPatterns.removeAll(allowedExpressionPatternsEntry.getValue());
            allowedExpressionPatternsByCollection.put(allowedExpressionPatternsEntry.getKey(), allowedExpressionPatterns);
        }
    }

    @Override
    public ExpressionFilter getExpressionFilter(String filterCollection) {
        if (expressionFiltersActivated) {
            return new ExpressionFilter(allowedExpressionPatternsByCollection.get(filterCollection), forbiddenExpressionPatternsByCollection.get(filterCollection));
        } else {
            // if expression filtering is turned off we build an expression filter with no filters and that will accept everything.
            return new ExpressionFilter(null, null);
        }
    }
}
