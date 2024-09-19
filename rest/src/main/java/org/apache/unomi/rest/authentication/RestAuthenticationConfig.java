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
package org.apache.unomi.rest.authentication;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * This interface provide rest authentication configuration for the rest server.
 */
public interface RestAuthenticationConfig {

    /**
     * This provide the patterns to identify public endpoints
     * The patterns will be tested against this format: "HTTP_METHOD HTTP_PATH_WITHOUT_CXS_PREFIX", like: "GET context.json"
     *
     * sample pattern for identify GET, POST and OPTIONS on "/cxs/context.json" as public requests would be:
     * "(GET|POST|OPTIONS) context\\.json"
     *
     * sample pattern for identify GET only on all paths starting by "/cxs/client/" as public requests would be:
     * "GET client/.*"
     *
     * @return the list of public paths patterns
     */
    List<Pattern> getPublicPathPatterns();

    /**
     * This is the roles mapped to endpoints
     * By default all methods are protected by the global roles
     * But you can define more granularity by providing roles for given endpoint methods
     *
     * Multiple format supported for the keys:
     * - Method precise signature:      org.apache.unomi.api.ContextResponse getContextJSON(java.lang.Stringjava.lang.Longjava.lang.String)
     * - Class name + method name:      org.apache.unomi.rest.ContextJsonEndpoint.getContextJSON
     * - Method name only:              getContextJSON
     *
     * @return the list of role mappings &lt;methodKey, roles separated by single white spaces&gt;, like: &lt;"getContextJSON", "ROLE1 ROLE2 ROLE3"&gt;
     */
    Map<String, String> getMethodRolesMap();

    /**
     * Define the global roles required for accessing endpoints methods, in case the method doesnt have specific required roles
     * It will fallback on this global roles
     * @return Global roles separated with single white spaces, like: "ROLE1 ROLE2 ROLE3"
     */
    String getGlobalRoles();
}
