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
package org.apache.unomi.rest.validation.cookies;

import org.apache.cxf.validation.BeanValidationProvider;
import org.apache.unomi.rest.validation.HibernateValidationProviderResolver;
import org.hibernate.validator.HibernateValidator;

import javax.servlet.http.Cookie;

public class CookieUtils {

    public static void validate(Cookie[] cookies) {
        CookieWrapper cookieWrapper = new CookieWrapper(cookies);
        HibernateValidationProviderResolver validationProviderResolver = new HibernateValidationProviderResolver();

        BeanValidationProvider beanValidationProvider = new BeanValidationProvider(validationProviderResolver, HibernateValidator.class);

        ClassLoader currentContextClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(CookieUtils.class.getClassLoader());
            beanValidationProvider.validateBean(cookieWrapper);
        } finally {
            Thread.currentThread().setContextClassLoader(currentContextClassLoader);
        }
    }
}
