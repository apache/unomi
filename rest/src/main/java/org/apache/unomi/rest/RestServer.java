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
import org.apache.cxf.BusFactory;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.feature.LoggingFeature;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.openapi.OpenApiCustomizer;
import org.apache.cxf.jaxrs.openapi.OpenApiFeature;
import org.apache.cxf.jaxrs.security.JAASAuthenticationFilter;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Filter;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.*;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.util.tracker.ServiceTrackerCustomizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.ext.ExceptionMapper;
import javax.xml.namespace.QName;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class RestServer {

    private static final Logger logger = LoggerFactory.getLogger(RestServer.class.getName());

    private Server server;
    private BundleContext bundleContext;
    private ServiceTracker jaxRSServiceTracker;
    private Bus serverBus;
    private List<ExceptionMapper> exceptionMappers = new ArrayList<>();
    private long timeOfLastUpdate = System.currentTimeMillis();
    private Timer refreshTimer = null;
    private long startupDelay = 1000L;

    final List<Object> serviceBeans = new CopyOnWriteArrayList<>();

    private static final QName UNOMI_REST_SERVER_END_POINT_NAME = new QName("http://rest.unomi.apache.org/", "UnomiRestServerEndPoint");

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    public void setServerBus(Bus serverBus) {
        this.serverBus = serverBus;
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
                    logger.info("Refreshed server task performed on: " + new Date() +
                            " Thread's name: " + Thread.currentThread().getName());
                }
            };
            refreshTimer = new Timer("Timer-Refresh-REST-API");

            refreshTimer.schedule(task, startupDelay);
            return;
        }

        if (server != null) {
            logger.info("Shutting down JAX RS Endpoint... ");
            server.destroy();
        }

        final OpenApiFeature openApiFeature = new OpenApiFeature();
        openApiFeature.setContactEmail("dev@unomi.apache.org");
        openApiFeature.setLicense("Apache 2.0 License");
        openApiFeature.setLicenseUrl("http://www.apache.org/licenses/LICENSE-2.0.html");
        openApiFeature.setScan(false);
        openApiFeature.setUseContextBasedConfig(true);        //Set<String> resourceClasses = serviceBeans.stream().map(service -> service.getClass().getName()).collect(toSet());
        OpenApiCustomizer customizer = new OpenApiCustomizer();
        customizer.setDynamicBasePath(true);
        openApiFeature.setCustomizer(customizer);

        JAXRSServerFactoryBean jaxrsServerFactoryBean = new JAXRSServerFactoryBean();
        jaxrsServerFactoryBean.setAddress("/");
        jaxrsServerFactoryBean.setBus(serverBus);
        jaxrsServerFactoryBean.setProvider(
                new JacksonJaxbJsonProvider(
                        new org.apache.unomi.persistence.spi.CustomObjectMapper(),
                        JacksonJaxbJsonProvider.DEFAULT_ANNOTATIONS));
        jaxrsServerFactoryBean.setProvider(new org.apache.cxf.rs.security.cors.CrossOriginResourceSharingFilter());
        JAASAuthenticationFilter jaasFilter = new org.apache.cxf.jaxrs.security.JAASAuthenticationFilter();
        jaasFilter.setContextName("karaf");
        jaasFilter.setRoleClassifier("ROLE_");
        jaasFilter.setRealmName("cxs");
        jaxrsServerFactoryBean.setProvider(jaasFilter);
        for (ExceptionMapper exceptionMapper : exceptionMappers) {
            jaxrsServerFactoryBean.setProvider(exceptionMapper);
        }
        jaxrsServerFactoryBean.setServiceBeans(serviceBeans);
        jaxrsServerFactoryBean.getFeatures().add(openApiFeature);

        if (serviceBeans.size() > 0) {
            logger.info("Starting JAX RS Endpoint...");
            server = jaxrsServerFactoryBean.create();
            server.getEndpoint().getEndpointInfo().setName(UNOMI_REST_SERVER_END_POINT_NAME);
        }
    }
}