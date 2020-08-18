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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MVEL script executor implementation
 */
public class MvelScriptExecutor implements ScriptExecutor {

    private final static String INVALID_SCRIPT_MARKER = "Invalid String Marker";

    private Map<String, Serializable> mvelExpressions = new ConcurrentHashMap<>();
    private SecureFilteringClassLoader secureFilteringClassLoader = new SecureFilteringClassLoader(getClass().getClassLoader());
    private ExpressionFilterFactory expressionFilterFactory;

    public void setExpressionFilterFactory(ExpressionFilterFactory expressionFilterFactory) {
        this.expressionFilterFactory = expressionFilterFactory;
    }

    @Override
    public Object execute(String script, Map<String, Object> context) {

        final ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        try {
            if (!mvelExpressions.containsKey(script)) {

                if (expressionFilterFactory.getExpressionFilter("mvel").filter(script) == null) {
                    mvelExpressions.put(script, INVALID_SCRIPT_MARKER);
                } else {
                    Thread.currentThread().setContextClassLoader(secureFilteringClassLoader);
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

                    mvelExpressions.put(script, MVEL.compileExpression(script, parserContext));
                }
            }
            if (mvelExpressions.containsKey(script) && mvelExpressions.get(script) != INVALID_SCRIPT_MARKER) {
                return MVEL.executeExpression(mvelExpressions.get(script), context);
            } else {
                return script;
            }
        } finally {
            Thread.currentThread().setContextClassLoader(tccl);
        }
    }
}
