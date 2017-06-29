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
package org.apache.unomi.router.core.route;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.rest.RestBindingMode;
import org.apache.unomi.router.api.ExportConfiguration;
import org.apache.unomi.router.api.ImportConfiguration;
import org.apache.unomi.router.core.context.RouterCamelContext;
import org.apache.unomi.router.core.processor.ConfigUpdateProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by amidani on 10/05/2017.
 */
public class ConfigUpdateRouteBuilder extends RouteBuilder {

    private static final Logger logger = LoggerFactory.getLogger(ConfigUpdateRouteBuilder.class.getName());
    private RouterCamelContext routerCamelContext;

    @Override
    public void configure() throws Exception {
        logger.info("Preparing REST Configuration for servlet with context path [/configUpdate]");
        restConfiguration().component("servlet")
                .contextPath("/configUpdate")
                .enableCORS(false)
                .bindingMode(RestBindingMode.json)
                .dataFormatProperty("prettyPrint", "true");

        rest().put("/importConfigAdmin").consumes("application/json").type(ImportConfiguration.class)
                .to("direct:importConfigRestDeposit");

        ConfigUpdateProcessor profileConfigUpdateProcessor = new ConfigUpdateProcessor();
        profileConfigUpdateProcessor.setRouterCamelContext(routerCamelContext);
        from("direct:importConfigRestDeposit")
                .process(profileConfigUpdateProcessor)
                .transform().constant("Success.")
                .onException(Exception.class)
                .transform().constant("Failure!");

        rest().put("/exportConfigAdmin").consumes("application/json").type(ExportConfiguration.class)
                .to("direct:exportConfigRestDeposit");

        from("direct:exportConfigRestDeposit")
                .process(profileConfigUpdateProcessor)
                .transform().constant("Success.")
                .onException(Exception.class)
                .transform().constant("Failure!");


    }

    public void setRouterCamelContext(RouterCamelContext routerCamelContext) {
        this.routerCamelContext = routerCamelContext;
    }

}
