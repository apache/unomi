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

import org.mvel2.MVEL;
import org.mvel2.ParserConfiguration;
import org.mvel2.ParserContext;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MVEL script executor implementation
 */
public class MvelScriptExecutor implements ScriptExecutor {

    private final static String INVALID_SCRIPT_MARKER = "--- Invalid Script Marker ---";

    private static final int DEFAULT_EXPRESSIONS_CACHE_MAX_SIZE = 1000;

    private final int expressionsCacheMaxSize = Integer.getInteger(
            "org.apache.unomi.scripting.mvel.expressions.cache.max.size", DEFAULT_EXPRESSIONS_CACHE_MAX_SIZE);

    /**
     * Size-bounded LRU cache keyed by a fixed-size hash of the script text. Rejected scripts can be
     * unique and large, so an unbounded map keyed by the raw script would grow without limit.
     */
    private final Map<String, Serializable> mvelExpressions = Collections.synchronizedMap(
            new LinkedHashMap<String, Serializable>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Serializable> eldest) {
                    return size() > expressionsCacheMaxSize;
                }
            });
    private SecureFilteringClassLoader secureFilteringClassLoader = new SecureFilteringClassLoader(getClass().getClassLoader());
    private ExpressionFilterFactory expressionFilterFactory;

    /**
     * Sets the factory used to obtain expression filters per script language.
     *
     * @param expressionFilterFactory the expression filter factory
     */
    public void setExpressionFilterFactory(ExpressionFilterFactory expressionFilterFactory) {
        this.expressionFilterFactory = expressionFilterFactory;
    }

    @Override
    public Object execute(String script, Map<String, Object> context) {

        final ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(secureFilteringClassLoader);

            String scriptCacheKey = getScriptCacheKey(script);
            Serializable compiledScript = mvelExpressions.get(scriptCacheKey);
            if (compiledScript == null) {

                if (expressionFilterFactory.getExpressionFilter("mvel").filter(script) == null) {
                    compiledScript = INVALID_SCRIPT_MARKER;
                } else {
                    ParserConfiguration parserConfiguration = new ParserConfiguration();
                    parserConfiguration.setClassLoader(secureFilteringClassLoader);
                    ParserContext parserContext = new ParserContext(parserConfiguration);

                    // override hardcoded Class Literals that are inserted by default in MVEL and that may be a security risk
                    parserContext.addImport("Runtime", String.class);
                    parserContext.addImport("System", String.class);
                    parserContext.addImport("ProcessBuilder", String.class);
                    parserContext.addImport("Class", String.class);
                    parserContext.addImport("ClassLoader", String.class);
                    parserContext.addImport("Thread", String.class);
                    parserContext.addImport("Compiler", String.class);
                    parserContext.addImport("ThreadLocal", String.class);
                    parserContext.addImport("SecurityManager", String.class);

                    compiledScript = MVEL.compileExpression(script, parserContext);
                }
                mvelExpressions.put(scriptCacheKey, compiledScript);
            }
            if (compiledScript != INVALID_SCRIPT_MARKER) {
                return MVEL.executeExpression(compiledScript, context);
            } else {
                return null;
            }
        } finally {
            Thread.currentThread().setContextClassLoader(tccl);
        }
    }

    private static String getScriptCacheKey(String script) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(script.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            return script;
        }
    }
}
