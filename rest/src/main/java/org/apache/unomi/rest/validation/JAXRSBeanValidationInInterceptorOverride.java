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
package org.apache.unomi.rest.validation;

import org.apache.cxf.jaxrs.validation.JAXRSBeanValidationInInterceptor;
import org.apache.cxf.message.Message;

import java.lang.reflect.Method;
import java.util.List;

/**
 * This class allows to replace the class loader use by
 * javax.el.FactoryFinder.newInstance(FactoryFinder.java:53)
 * to allow to retrieve com.sun.el.ExpressionFactoryImpl which is present in the class loader of this module
 */
public class JAXRSBeanValidationInInterceptorOverride extends JAXRSBeanValidationInInterceptor {
    @Override
    protected void handleValidation(final Message message, final Object resourceInstance, final Method method,
            final List<Object> arguments) {
        ClassLoader currentContextClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(JAXRSBeanValidationInInterceptorOverride.class.getClassLoader());
            super.handleValidation(message, resourceInstance, method, arguments);
        } finally {
            Thread.currentThread().setContextClassLoader(currentContextClassLoader);
        }
    }
}
