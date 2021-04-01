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

import org.apache.cxf.interceptor.security.SimpleAuthorizingInterceptor;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Authorizing interceptor is in charge of testing that current authenticate user have the expected role during method access
 */
public class AuthorizingInterceptor extends SimpleAuthorizingInterceptor {

    public AuthorizingInterceptor(RestAuthenticationConfig restAuthenticationConfig) {
        super();
        setGlobalRoles(restAuthenticationConfig.getGlobalRoles());
        setMethodRolesMap(restAuthenticationConfig.getMethodRolesMap());
    }

    /**
     * Returns a list of expected roles for a given method.
     *
     * This override provide an additional lookup to the default implementation
     * It's now possible resolve role mapping using this syntax: CLASS_NAME.METHOD_NAME
     *
     * @param method Method
     * @return list, empty if no roles are available
     */
    @Override
    protected List<String> getExpectedRoles(Method method) {
        // let super class calculate the roles to see if he is able to find something
        List<String> roles =  super.getExpectedRoles(method);
        if (roles == null || roles == globalRoles) {
            // super class didnt find any specific roles for the method, let's try with our custom ClassName.MethodName lookup
            roles = methodRolesMap.get(method.getDeclaringClass().getName() + "." + method.getName());
        }
        if (roles != null) {
            return roles;
        }
        return globalRoles;
    }
}
