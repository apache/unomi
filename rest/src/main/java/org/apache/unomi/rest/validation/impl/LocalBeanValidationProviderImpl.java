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

package org.apache.unomi.rest.validation.impl;

import org.apache.cxf.validation.BeanValidationProvider;
import org.apache.unomi.rest.validation.HibernateValidationProviderResolver;
import org.apache.unomi.rest.validation.LocalBeanValidationProvider;
import org.hibernate.validator.HibernateValidator;
import org.osgi.service.component.annotations.Component;

@Component(service = LocalBeanValidationProvider.class)
public class LocalBeanValidationProviderImpl implements LocalBeanValidationProvider {
    private BeanValidationProvider beanValidationProvider;

    public LocalBeanValidationProviderImpl() {
        // This is a TCCL (Thread context class loader) hack to for the javax.el.FactoryFinder to use Class.forName(className)
        // instead of tccl.loadClass(className) to load the class "com.sun.el.ExpressionFactoryImpl".
        ClassLoader currentContextClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(null);
            HibernateValidationProviderResolver validationProviderResolver = new HibernateValidationProviderResolver();
            this.beanValidationProvider = new BeanValidationProvider(validationProviderResolver, HibernateValidator.class);
        } finally {
            Thread.currentThread().setContextClassLoader(currentContextClassLoader);
        }
    }

    public BeanValidationProvider get() {
        return beanValidationProvider;
    }
}
