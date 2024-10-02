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

package org.apache.unomi.healthcheck.servlet;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.unomi.healthcheck.HealthCheckService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * @author Jerome Blanchard
 */
@Component(
        service = {javax.servlet.http.HttpServlet.class, javax.servlet.Servlet.class},
        property = {"alias=/health"}
)
public class HealthCheckServlet extends HttpServlet {

    private static final Logger LOGGER = LoggerFactory.getLogger(HealthCheckServlet.class.getName());

    @Reference
    private HealthCheckService service;
    private ObjectMapper mapper;

    public HealthCheckServlet() {
        LOGGER.info("Building healthcheck servlet...");
        mapper = new ObjectMapper();
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    public void setHealthCheckService(HealthCheckService service) {
        this.service = service;
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        LOGGER.info("Initializing healthcheck servlet...");
        super.init(config);
    }

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.getWriter().println(mapper.writeValueAsString(service.check()));
        response.setContentType("application/json");
        response.setStatus(HttpServletResponse.SC_OK);
    }
}
