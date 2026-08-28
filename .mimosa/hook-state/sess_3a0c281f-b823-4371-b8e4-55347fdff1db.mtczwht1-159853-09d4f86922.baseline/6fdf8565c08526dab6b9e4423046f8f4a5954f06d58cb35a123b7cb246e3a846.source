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
package org.apache.unomi.graphql.activator;

import org.apache.unomi.graphql.servlet.GraphQLServlet;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.http.HttpService;
import org.osgi.util.tracker.ServiceTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Dictionary;
import java.util.Hashtable;

/**
 * @author Jerome Blanchard
 */
public class GraphQLServletActivator implements BundleActivator {

    private static final Logger LOGGER = LoggerFactory.getLogger(GraphQLServletActivator.class);

    private ServiceTracker<HttpService, HttpService> httpServiceTracker;
    private ServiceTracker<GraphQLServlet, GraphQLServlet> servletTracker;

    @Override
    public void start(BundleContext context) {
        httpServiceTracker = new ServiceTracker<>(context, HttpService.class, null);
        httpServiceTracker.open();

        servletTracker = new ServiceTracker<GraphQLServlet, GraphQLServlet>(context, GraphQLServlet.class, null) {
            @Override
            public GraphQLServlet addingService(ServiceReference<GraphQLServlet> reference) {
                LOGGER.info("Registering GraphQL servlet");
                GraphQLServlet servlet = context.getService(reference);
                try {
                    HttpService httpService = httpServiceTracker.getService();
                    if (httpService != null && servlet != null) {
                        Dictionary<String, String> initParams = new Hashtable<>();
                        httpService.registerServlet("/graphql", servlet, initParams, null);
                    }
                } catch (Exception e) {
                    LOGGER.error("Failed to register GraphQL servlet", e);
                }
                return servlet;
            }

            @Override
            public void removedService(ServiceReference<GraphQLServlet> reference, GraphQLServlet service) {
                LOGGER.info("Unregistering GraphQL servlet");
                try {
                    HttpService httpService = httpServiceTracker.getService();
                    if (httpService != null) {
                        httpService.unregister("/graphql");
                    }
                } catch (Exception e) {
                    LOGGER.error("Failed to register GraphQL servlet", e);
                }
                context.ungetService(reference);
            }
        };
        servletTracker.open();
    }

    @Override
    public void stop(BundleContext context) {
        if (servletTracker != null) {
            servletTracker.close();
        }

        if (httpServiceTracker != null) {
            httpServiceTracker.close();
        }
    }
}
