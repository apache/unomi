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
package org.apache.unomi.rest.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.jaxrs.json.JacksonJaxbJsonProvider;
import org.apache.cxf.Bus;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.interceptor.Interceptor;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.openapi.OpenApiCustomizer;
import org.apache.cxf.jaxrs.openapi.OpenApiFeature;
import org.apache.cxf.jaxrs.security.SimpleAuthorizingFilter;
import org.apache.cxf.message.Message;
import org.apache.cxf.rs.security.cors.CrossOriginResourceSharingFilter;
import org.apache.unomi.api.ContextRequest;
import org.apache.unomi.api.EventsCollectorRequest;
import org.apache.unomi.api.services.ConfigSharingService;
import org.apache.unomi.rest.authentication.AuthenticationFilter;
import org.apache.unomi.rest.authentication.AuthorizingInterceptor;
import org.apache.unomi.rest.authentication.RestAuthenticationConfig;
import org.apache.unomi.rest.deserializers.ContextRequestDeserializer;
import org.apache.unomi.rest.deserializers.EventsCollectorRequestDeserializer;
import org.apache.unomi.rest.server.provider.RetroCompatibilityParamConverterProvider;
import org.apache.unomi.rest.validation.request.RequestValidatorInterceptor;
import org.apache.unomi.schema.api.SchemaService;
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
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@Component
public class RestServer {

    private static final Logger LOGGER = LoggerFactory.getLogger(RestServer.class.getName());

    private Server server;
    private BundleContext bundleContext;
    private ServiceTracker jaxRSServiceTracker;
    final List<Object> serviceBeans = new CopyOnWriteArrayList<>();

    // services
    private Bus serverBus;
    private RestAuthenticationConfig restAuthenticationConfig;
    private List<ExceptionMapper> exceptionMappers = new ArrayList<>();
    private ConfigSharingService configSharingService;
    private SchemaService schemaService;

    // refresh
    private long timeOfLastUpdate = System.currentTimeMillis();
    private Timer refreshTimer = null;
    private long startupDelay = 1000L;

    private static final QName UNOMI_REST_SERVER_END_POINT_NAME = new QName("http://rest.unomi.apache.org/", "UnomiRestServerEndPoint");

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    public void setSchemaService(SchemaService schemaService) {
        this.schemaService = schemaService;
    }

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    public void setServerBus(Bus serverBus) {
        this.serverBus = serverBus;
    }

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    public void setRestAuthenticationConfig(RestAuthenticationConfig restAuthenticationConfig) {
        this.restAuthenticationConfig = restAuthenticationConfig;
    }

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    public void setConfigSharingService(ConfigSharingService configSharingService) {
        this.configSharingService = configSharingService;
    }

    @Reference(cardinality = ReferenceCardinality.MULTIPLE)
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
                while (serviceBean == null) {
                    LOGGER.info("Waiting for service {} to become available...", reference.getProperty("objectClass"));
                    serviceBean = bundleContext.getService(reference);
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        LOGGER.warn("Interrupted thread exception", e);
                    }
                }
                LOGGER.info("Registering JAX RS service {}", serviceBean.getClass().getName());
                serviceBeans.add(serviceBean);
                timeOfLastUpdate = System.currentTimeMillis();
                refreshServer();
                return serviceBean;
            }

            @Override
            public void modifiedService(ServiceReference reference, Object service) {
                LOGGER.info("Refreshing JAX RS server because service {} was modified.", service.getClass().getName());
                timeOfLastUpdate = System.currentTimeMillis();
                refreshServer();
            }

            @Override
            public void removedService(ServiceReference reference, Object service) {
                LOGGER.info("Removing JAX RS service {}", service.getClass().getName());
                serviceBeans.remove(service);
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
        LOGGER.info("Refreshing JAX RS server...");
        long now = System.currentTimeMillis();
        LOGGER.info("Time (millis) since last update: {}", now - timeOfLastUpdate);
        if (now - timeOfLastUpdate < startupDelay) {
            if (refreshTimer != null) {
                return;
            }
            TimerTask task = new TimerTask() {
                public void run() {
                    refreshTimer = null;
                    refreshServer();
                    LOGGER.info("Refreshed server task performed on: {} Thread's name: {}", new Date(), Thread.currentThread().getName());
                }
            };
            refreshTimer = new Timer("Timer-Refresh-REST-API");

            refreshTimer.schedule(task, startupDelay);
            return;
        }

        if (server != null) {
            LOGGER.info("JAX RS Server: Shutting down server...");
            server.destroy();
        }

        if (serviceBeans.isEmpty()) {
            LOGGER.info("JAX RS Server: Server not started because no JAX RS EndPoint registered yet");
            return;
        }

        LOGGER.info("JAX RS Server: Configuring server...");

        List<Interceptor<? extends Message>> inInterceptors = new ArrayList<>();
        List<Interceptor<? extends Message>> outInterceptors = new ArrayList<>();

        Map<Class, StdDeserializer<?>> desers = new HashMap<>();
        desers.put(ContextRequest.class, new ContextRequestDeserializer(schemaService));
        desers.put(EventsCollectorRequest.class, new EventsCollectorRequestDeserializer(schemaService));

        // Build the server
        ObjectMapper objectMapper = new org.apache.unomi.persistence.spi.CustomObjectMapper(desers);
        JAXRSServerFactoryBean jaxrsServerFactoryBean = new JAXRSServerFactoryBean();
        jaxrsServerFactoryBean.setAddress("/");
        jaxrsServerFactoryBean.setBus(serverBus);
        jaxrsServerFactoryBean.setProvider(new JacksonJaxbJsonProvider(objectMapper, JacksonJaxbJsonProvider.DEFAULT_ANNOTATIONS));
        jaxrsServerFactoryBean.setProvider(new CrossOriginResourceSharingFilter());
        jaxrsServerFactoryBean.setProvider(new RetroCompatibilityParamConverterProvider(objectMapper));

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
        openApiFeature.setUseContextBasedConfig(true);
        OpenApiCustomizer customizer = new OpenApiCustomizer();
        customizer.setDynamicBasePath(true);
        openApiFeature.setCustomizer(customizer);
        jaxrsServerFactoryBean.getFeatures().add(openApiFeature);

        // Request validator
        inInterceptors.add(new RequestValidatorInterceptor(configSharingService));

        // Register service beans (end points) and interceptors
        jaxrsServerFactoryBean.setInInterceptors(inInterceptors);
        jaxrsServerFactoryBean.setOutInterceptors(outInterceptors);
        jaxrsServerFactoryBean.setServiceBeans(serviceBeans);

        LOGGER.info("JAX RS Server: Starting server with {} JAX RS EndPoints registered", serviceBeans.size());
        server = jaxrsServerFactoryBean.create();
        server.getEndpoint().getEndpointInfo().setName(UNOMI_REST_SERVER_END_POINT_NAME);
    }
}
