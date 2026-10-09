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

import org.apache.unomi.api.services.ProfileService;
import org.apache.unomi.router.api.ExportConfiguration;
import org.apache.unomi.router.api.ImportConfiguration;
import org.apache.unomi.router.api.RouterConstants;

import java.io.File;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.Collections;

/**
 * What a recurrent configuration looks like, for the tests that build routes out of one. Kept in one
 * place so that the tests of route construction and of the recorded status cannot come to disagree
 * about it.
 */
public final class RouterTestFixtures {

    static final String TENANT = "acme";

    private RouterTestFixtures() {
    }

    static String fileUri(File directory, String suffix) {
        return "file://" + directory.getAbsolutePath() + suffix;
    }

    static ImportConfiguration recurrentImport(String itemId, String source) {
        ImportConfiguration configuration = new ImportConfiguration();
        configuration.setItemId(itemId);
        configuration.setTenantId(TENANT);
        configuration.setConfigType(RouterConstants.IMPORT_EXPORT_CONFIG_TYPE_RECURRENT);
        configuration.setActive(true);
        configuration.getProperties().put("source", source);
        configuration.getProperties().put("mapping", Collections.singletonMap("0", 0));
        return configuration;
    }

    static ExportConfiguration recurrentExport(String itemId, String destination) {
        ExportConfiguration configuration = new ExportConfiguration();
        configuration.setItemId(itemId);
        configuration.setTenantId(TENANT);
        configuration.setConfigType(RouterConstants.IMPORT_EXPORT_CONFIG_TYPE_RECURRENT);
        configuration.setActive(true);
        configuration.getProperties().put("destination", destination);
        configuration.getProperties().put("mapping", Collections.singletonMap("0", "firstName"));
        configuration.getProperties().put("segment", "exportSegment");
        configuration.getProperties().put("period", "1m");
        return configuration;
    }

    /**
     * The route builders ask the profile service for the profile property types while they build.
     * Nothing in these tests depends on what it answers.
     */
    public static ProfileService noOpProfileService() {
        return (ProfileService) Proxy.newProxyInstance(
                ProfileService.class.getClassLoader(),
                new Class<?>[]{ProfileService.class},
                (proxy, method, args) -> Collection.class.isAssignableFrom(method.getReturnType())
                        ? Collections.emptyList() : null);
    }
}
