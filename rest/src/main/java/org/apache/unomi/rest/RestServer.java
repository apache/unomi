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
package org.apache.unomi.rest;

import com.fasterxml.jackson.jaxrs.json.JacksonJaxbJsonProvider;
import org.apache.cxf.Bus;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.openapi.OpenApiCustomizer;
import org.apache.cxf.jaxrs.openapi.OpenApiFeature;
import org.apache.cxf.jaxrs.security.SimpleAuthorizingFilter;
import org.apache.cxf.jaxrs.validation.JAXRSBeanValidationInInterceptor;
import org.apache.cxf.jaxrs.validation.JAXRSBeanValidationOutInterceptor;
import org.apache.cxf.jaxrs.validation.ValidationExceptionMapper;
import org.apache.cxf.validation.BeanValidationProvider;
import org.apache.unomi.rest.validation.HibernateValidationProviderResolver;
import org.apache.unomi.rest.validation.JAXRSBeanValidationInInterceptorOverride;
import org.apache.unomi.rest.authentication.AuthenticationFilter;
import org.apache.unomi.rest.authentication.AuthorizingInterceptor;
import org.apache.unomi.rest.authentication.RestAuthenticationConfig;
import org.hibernate.validator.HibernateValidator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Filter;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.util.tracker.ServiceTrackerCustomizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.ext.ExceptionMapper;
import javax.xml.namespace.QName;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class RestServer {

    private static final Logger logger = LoggerFactory.getLogger(RestServer.class.getName());

    private Server server;
    private BundleContext bundleContext;
    private ServiceTracker jaxRSServiceTracker;
    private Bus serverBus;
    private RestAuthenticationConfig restAuthenticationConfig;
    private List<ExceptionMapper> exceptionMappers = new ArrayList<>();
    private long timeOfLastUpdate = System.currentTimeMillis();
    private Timer refreshTimer = null;
    private long startupDelay = 1000L;
    private BeanValidationProvider beanValidationProvider;

    final List<Object> serviceBeans = new CopyOnWriteArrayList<>();

    private static final QName UNOMI_REST_SERVER_END_POINT_NAME = new QName("http://rest.unomi.apache.org/", "UnomiRestServerEndPoint");

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    public void setServerBus(Bus serverBus) {
        this.serverBus = serverBus;
    }

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    public void setRestAuthenticationConfig(RestAuthenticationConfig restAuthenticationConfig) {
        this.restAuthenticationConfig = restAuthenticationConfig;
    }

    @Reference
    public void addExceptionMapper(ExceptionMapper exceptionMapper) {
        this.exceptionMappers.add(exceptionMapper);
        timeOfLastUpdate = System.currentTimeMillis();
        refreshServer();
    }

    public void removeExceptionMapper(ExceptionMapper exceptionMapper) {
        this.exceptionMappers.remove(exceptionMapper);
        timeOfLastUpdate = System.currentTimeMillis();
        refreshServer();
    }

    @Activate
    public void activate(ComponentContext componentContext) throws Exception {
        this.bundleContext = componentContext.getBundleContext();

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

        Filter filter = bundleContext.createFilter("(osgi.jaxrs.resource=true)");
        jaxRSServiceTracker = new ServiceTracker(bundleContext, filter, new ServiceTrackerCustomizer() {
            @Override
            public Object addingService(ServiceReference reference) {
                Object serviceBean = bundleContext.getService(reference);
                logger.info("Registering JAX RS service " + serviceBean.getClass().getName());
                serviceBeans.add(serviceBean);
                timeOfLastUpdate = System.currentTimeMillis();
                refreshServer();
                return serviceBean;
            }

            @Override
            public void modifiedService(ServiceReference reference, Object service) {
                logger.info("Refreshing JAX RS server because service " + service.getClass().getName() + " was modified.");
                timeOfLastUpdate = System.currentTimeMillis();
                refreshServer();
            }

            @Override
            public void removedService(ServiceReference reference, Object service) {
                Object serviceBean = bundleContext.getService(reference);
                logger.info("Removing JAX RS service " + serviceBean.getClass().getName());
                serviceBeans.remove(serviceBean);
                timeOfLastUpdate = System.currentTimeMillis();
                refreshServer();
            }
        });
        jaxRSServiceTracker.open();
    }

    @Deactivate
    public void deactivate() throws Exception {
        jaxRSServiceTracker.close();
        if (server != null) {
            server.destroy();
        }
    }

    private synchronized void refreshServer() {
        long now = System.currentTimeMillis();
        logger.info("Time (millis) since last update: {}", now - timeOfLastUpdate);
        if (now - timeOfLastUpdate < startupDelay) {
            if (refreshTimer != null) {
                return;
            }
            TimerTask task = new TimerTask() {
                public void run() {
                    refreshTimer = null;
                    refreshServer();
                    logger.info(
                            "Refreshed server task performed on: " + new Date() + " Thread's name: " + Thread.currentThread().getName());
                }
            };
            refreshTimer = new Timer("Timer-Refresh-REST-API");

            refreshTimer.schedule(task, startupDelay);
            return;
        }

        if (server != null) {
            logger.info("JAX RS Server: Shutting down server...");
            server.destroy();
        }

        if (serviceBeans.isEmpty()) {
            logger.info("JAX RS Server: Server not started because no JAX RS EndPoint registered yet");
            return;
        }

        logger.info("JAX RS Server: Configuring server...");

        // Build the server
        JAXRSServerFactoryBean jaxrsServerFactoryBean = new JAXRSServerFactoryBean();
        jaxrsServerFactoryBean.setAddress("/");
        jaxrsServerFactoryBean.setBus(serverBus);
        jaxrsServerFactoryBean.setProvider(new JacksonJaxbJsonProvider(new org.apache.unomi.persistence.spi.CustomObjectMapper(),
                JacksonJaxbJsonProvider.DEFAULT_ANNOTATIONS));
        jaxrsServerFactoryBean.setProvider(new org.apache.cxf.rs.security.cors.CrossOriginResourceSharingFilter());

        // Authentication filter (used for authenticating user from request)
        jaxrsServerFactoryBean.setProvider(new AuthenticationFilter(restAuthenticationConfig));

        // Authorization interceptor (used for checking roles at methods access directly)
        SimpleAuthorizingFilter simpleAuthorizingFilter = new SimpleAuthorizingFilter();
        simpleAuthorizingFilter.setInterceptor(new AuthorizingInterceptor(restAuthenticationConfig));
        jaxrsServerFactoryBean.setProvider(simpleAuthorizingFilter);

        // Exception mappers
        for (ExceptionMapper exceptionMapper : exceptionMappers) {
            jaxrsServerFactoryBean.setProvider(exceptionMapper);
        }

        // Open API config
        final OpenApiFeature openApiFeature = new OpenApiFeature();
        openApiFeature.setContactEmail("dev@unomi.apache.org");
        openApiFeature.setLicense("Apache 2.0 License");
        openApiFeature.setLicenseUrl("http://www.apache.org/licenses/LICENSE-2.0.html");
        openApiFeature.setScan(false);
        openApiFeature.setUseContextBasedConfig(
                true);        //Set<String> resourceClasses = serviceBeans.stream().map(service -> service.getClass().getName()).collect(toSet());
        OpenApiCustomizer customizer = new OpenApiCustomizer();
        customizer.setDynamicBasePath(true);
        openApiFeature.setCustomizer(customizer);
        jaxrsServerFactoryBean.getFeatures().add(openApiFeature);

        // Hibernate validator config
        jaxrsServerFactoryBean.setProvider(new ValidationExceptionMapper());
        JAXRSBeanValidationInInterceptor inInterceptor = new JAXRSBeanValidationInInterceptorOverride();
        inInterceptor.setProvider(beanValidationProvider);
        jaxrsServerFactoryBean.setInInterceptors(Collections.singletonList(inInterceptor));
        JAXRSBeanValidationOutInterceptor outInterceptor = new JAXRSBeanValidationOutInterceptor();
        outInterceptor.setProvider(beanValidationProvider);
        jaxrsServerFactoryBean.setOutInterceptors(Collections.singletonList(outInterceptor));

        // Register service beans (end points)
        jaxrsServerFactoryBean.setServiceBeans(serviceBeans);

        logger.info("JAX RS Server: Starting server with {} JAX RS EndPoints registered", serviceBeans.size());
        server = jaxrsServerFactoryBean.create();
        server.getEndpoint().getEndpointInfo().setName(UNOMI_REST_SERVER_END_POINT_NAME);
    }
}
