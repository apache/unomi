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
import org.apache.unomi.api.ContextRequest;
import org.apache.unomi.api.EventsCollectorRequest;
import org.apache.unomi.api.security.SecurityService;
import org.apache.unomi.api.services.ConfigSharingService;
import org.apache.unomi.api.services.ExecutionContextManager;
import org.apache.unomi.api.tenants.TenantService;
import org.apache.unomi.rest.authentication.AuthenticationFilter;
import org.apache.unomi.rest.authentication.AuthorizingInterceptor;
import org.apache.unomi.rest.authentication.RestAuthenticationConfig;
import org.apache.unomi.rest.authentication.SecurityContextCleanupFilter;
import org.apache.unomi.rest.deserializers.ContextRequestDeserializer;
import org.apache.unomi.rest.deserializers.EventsCollectorRequestDeserializer;
import org.apache.unomi.rest.security.SecurityFilter;
import org.apache.unomi.rest.server.provider.RetroCompatibilityParamConverterProvider;
import org.apache.unomi.rest.validation.request.RequestValidatorInterceptor;
import org.apache.unomi.schema.api.SchemaService;
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
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class RestServer {

    private static final Logger LOGGER = LoggerFactory.getLogger(RestServer.class.getName());

    private Server server;
    private BundleContext bundleContext;
    private ServiceTracker<Object, Object> jaxRSServiceTracker;
    final List<Object> serviceBeans = new CopyOnWriteArrayList<>();

    // services
    private Bus serverBus;
    private RestAuthenticationConfig restAuthenticationConfig;
    private List<ExceptionMapper> exceptionMappers = new ArrayList<>();
    private ConfigSharingService configSharingService;
    private SchemaService schemaService;
    private TenantService tenantService;
    private SecurityService securityService;
    private SecurityFilter securityFilter;
    private ExecutionContextManager executionContextManager;

    // refresh
    private long timeOfLastUpdate = System.currentTimeMillis();
    private Timer refreshTimer = null;
    private long startupDelay = 1000L;
    private final AtomicBoolean isShuttingDown = new AtomicBoolean(false);

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

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    public void setTenantService(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    public void setSecurityService(SecurityService securityService) {
        this.securityService = securityService;
    }

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    public void setExecutionContextManager(ExecutionContextManager executionContextManager) {
        this.executionContextManager = executionContextManager;
    }

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    public void setSecurityFilter(SecurityFilter securityFilter) {
        this.securityFilter = securityFilter;
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
        this.isShuttingDown.set(false);

        // Create a filter for JAX-RS resources
        Filter filter = bundleContext.createFilter("(osgi.jaxrs.resource=true)");
        
        // Create service tracker with proper generic types and customizer
        jaxRSServiceTracker = new ServiceTracker<>(bundleContext, filter, new JaxRsServiceTrackerCustomizer());
        jaxRSServiceTracker.open();
        
        LOGGER.info("RestServer activated and service tracker opened");
    }

    @Deactivate
    public void deactivate() throws Exception {
        LOGGER.info("RestServer deactivating...");
        isShuttingDown.set(true);
        
        // Cancel any pending refresh timer
        if (refreshTimer != null) {
            refreshTimer.cancel();
            refreshTimer = null;
        }
        
        // Close service tracker
        if (jaxRSServiceTracker != null) {
            jaxRSServiceTracker.close();
            jaxRSServiceTracker = null;
        }
        
        // Destroy server
        if (server != null) {
            server.destroy();
            server = null;
        }
        
        // Clear service beans
        serviceBeans.clear();
        
        LOGGER.info("RestServer deactivated");
    }

    /**
     * Custom service tracker customizer for JAX-RS services
     * This handles the lifecycle of JAX-RS resource services properly
     */
    private class JaxRsServiceTrackerCustomizer implements ServiceTrackerCustomizer<Object, Object> {
        
        @Override
        public Object addingService(ServiceReference<Object> reference) {
            if (isShuttingDown.get()) {
                LOGGER.debug("Shutdown in progress, ignoring new service: {}", 
                    reference.getProperty("objectClass"));
                return null;
            }
            
            Object serviceBean = null;
            try {
                // Get the service - this should not be null if the service is properly registered
                serviceBean = bundleContext.getService(reference);
                
                if (serviceBean == null) {
                    LOGGER.warn("Service reference returned null for: {}", 
                        reference.getProperty("objectClass"));
                    return null;
                }
                
                LOGGER.info("Registering JAX-RS service: {}", serviceBean.getClass().getName());
                
                // Add to service beans list
                serviceBeans.add(serviceBean);
                timeOfLastUpdate = System.currentTimeMillis();
                
                // Refresh server asynchronously to avoid blocking the service tracker
                scheduleServerRefresh();
                
                return serviceBean;
                
            } catch (Exception e) {
                LOGGER.error("Error adding JAX-RS service: {}", 
                    reference.getProperty("objectClass"), e);
                // Unget the service if we couldn't process it
                if (serviceBean != null) {
                    bundleContext.ungetService(reference);
                }
                return null;
            }
        }

        @Override
        public void modifiedService(ServiceReference<Object> reference, Object service) {
            if (isShuttingDown.get()) {
                return;
            }
            
            LOGGER.info("JAX-RS service modified: {}", service.getClass().getName());
            timeOfLastUpdate = System.currentTimeMillis();
            scheduleServerRefresh();
        }

        @Override
        public void removedService(ServiceReference<Object> reference, Object service) {
            if (isShuttingDown.get()) {
                return;
            }
            
            LOGGER.info("Removing JAX-RS service: {}", service.getClass().getName());
            
            // Remove from service beans list
            serviceBeans.remove(service);
            timeOfLastUpdate = System.currentTimeMillis();
            
            // Unget the service
            bundleContext.ungetService(reference);
            
            // Refresh server asynchronously
            scheduleServerRefresh();
        }
    }

    /**
     * Schedules a server refresh with debouncing
     */
    private void scheduleServerRefresh() {
        if (isShuttingDown.get()) {
            return;
        }
        
        long now = System.currentTimeMillis();
        if (now - timeOfLastUpdate < startupDelay) {
            // Debounce rapid changes
            if (refreshTimer == null) {
                refreshTimer = new Timer("RestServer-Refresh-Timer", true);
                refreshTimer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        refreshTimer = null;
                        if (!isShuttingDown.get()) {
                            refreshServer();
                        }
                    }
                }, startupDelay);
            }
            return;
        }
        
        // Refresh immediately if enough time has passed
        refreshServer();
    }

    private synchronized void refreshServer() {
        if (isShuttingDown.get()) {
            return;
        }
        
        long now = System.currentTimeMillis();
        LOGGER.debug("Time since last update: {} ms", now - timeOfLastUpdate);

        // Destroy existing server
        if (server != null) {
            LOGGER.info("JAX-RS Server: Shutting down existing server...");
            server.destroy();
            server = null;
        }

        // Check if we have any services to register
        if (serviceBeans.isEmpty()) {
            LOGGER.info("JAX-RS Server: No JAX-RS endpoints registered, server not started");
            return;
        }

        LOGGER.info("JAX-RS Server: Configuring server with {} endpoints...", serviceBeans.size());

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
        jaxrsServerFactoryBean.setProvider(new org.apache.cxf.rs.security.cors.CrossOriginResourceSharingFilter());
        jaxrsServerFactoryBean.setProvider(new RetroCompatibilityParamConverterProvider(objectMapper));

        // Authentication and Security filters in order of priority
        // 1. Authentication filter (Priorities.AUTHENTICATION = 2000)
        jaxrsServerFactoryBean.setProvider(new AuthenticationFilter(restAuthenticationConfig, tenantService, securityService, executionContextManager));

        // 2. Security filter for role-based access control (Priorities.AUTHORIZATION = 3000)
        jaxrsServerFactoryBean.setProvider(securityFilter);

        // 3. Authorization interceptor for method-level security (after role checks)
        SimpleAuthorizingFilter simpleAuthorizingFilter = new SimpleAuthorizingFilter();
        simpleAuthorizingFilter.setInterceptor(new AuthorizingInterceptor(restAuthenticationConfig));
        jaxrsServerFactoryBean.setProvider(simpleAuthorizingFilter);

        // 4. Security context cleanup filter (same priority as Authentication but runs during response)
        jaxrsServerFactoryBean.setProvider(new SecurityContextCleanupFilter(securityService, executionContextManager));

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

        try {
            LOGGER.info("JAX-RS Server: Starting server with {} endpoints", serviceBeans.size());
            server = jaxrsServerFactoryBean.create();
            server.getEndpoint().getEndpointInfo().setName(UNOMI_REST_SERVER_END_POINT_NAME);
            LOGGER.info("JAX-RS Server: Server started successfully");
        } catch (Exception e) {
            LOGGER.error("JAX-RS Server: Failed to start server", e);
            server = null;
        }
    }
}
