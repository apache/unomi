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
package org.apache.unomi.shell.completers;

import org.apache.karaf.features.Feature;
import org.apache.karaf.features.FeaturesService;
import org.apache.karaf.features.Repository;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.karaf.shell.api.console.CommandLine;
import org.apache.karaf.shell.api.console.Completer;
import org.apache.karaf.shell.api.console.Session;
import org.apache.karaf.shell.support.completers.StringsCompleter;

import java.util.Arrays;
import java.util.List;

@Service
public class DistributionCompleter implements Completer {

    @Reference
    private FeaturesService featuresService;

    @Override
    public int complete(Session session, CommandLine commandLine, List<String> candidates) {
        StringsCompleter delegate = new StringsCompleter();

        try {
            // Get all repositories and their features
            Repository[] repositories = featuresService.listRepositories();
            for (Repository repository : repositories) {
                try {
                    Feature[] features = repository.getFeatures();
                    for (Feature feature : features) {
                        String featureName = feature.getName();
                        // Filter features starting with unomi-distribution-*
                        if (featureName != null && featureName.startsWith("unomi-distribution-")) {
                            delegate.getStrings().add(featureName);
                        }
                    }
                } catch (Exception e) {
                    // Skip repositories that can't be accessed
                    // This is normal for some repositories
                }
            }

            // If no features found from repositories, try to get known distributions by name
            if (delegate.getStrings().isEmpty()) {
                String[] knownDistributions = {
                    "unomi-distribution-elasticsearch",
                    "unomi-distribution-opensearch"
                };
                for (String dist : knownDistributions) {
                    try {
                        Feature feature = featuresService.getFeature(dist);
                        if (feature != null) {
                            delegate.getStrings().add(dist);
                        }
                    } catch (Exception e) {
                        // Feature doesn't exist, skip it
                    }
                }
            }
        } catch (Exception e) {
            // If we can't list repositories, fall back to common distribution names
            delegate.getStrings().add("unomi-distribution-elasticsearch");
            delegate.getStrings().add("unomi-distribution-opensearch");
        }

        return delegate.complete(session, commandLine, candidates);
    }
}
