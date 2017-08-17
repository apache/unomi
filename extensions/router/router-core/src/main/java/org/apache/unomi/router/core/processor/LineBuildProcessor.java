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
package org.apache.unomi.router.core.processor;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.PropertyType;
import org.apache.unomi.router.api.ExportConfiguration;
import org.apache.unomi.router.api.services.ProfileExportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

/**
 * Created by amidani on 28/06/2017.
 */
public class LineBuildProcessor implements Processor {

    private static final Logger logger = LoggerFactory.getLogger(LineBuildProcessor.class);

    private ProfileExportService profileExportService;

    public LineBuildProcessor(ProfileExportService profileExportService) {
        this.profileExportService = profileExportService;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        ExportConfiguration exportConfiguration = (ExportConfiguration) exchange.getIn().getHeader("exportConfig");
        Profile profile = exchange.getIn().getBody(Profile.class);

        String lineToWrite = profileExportService.convertProfileToCSVLine(profile, exportConfiguration );

        exchange.getIn().setBody(lineToWrite, String.class);
    }

}
