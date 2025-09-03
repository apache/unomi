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
package org.apache.unomi.metrics.commands;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.karaf.shell.commands.Argument;
import org.apache.karaf.shell.commands.Command;
import org.apache.unomi.metrics.Metric;

@Command(scope = "metrics", name = "view", description = "This will display all the data for a single metric ")
public class ViewCommand extends MetricsCommandSupport{

    @Argument(index = 0, name = "metricName", description = "The identifier for the metric", required = true, multiValued = false)
    String metricName;

    @Override
    protected Object doExecute() throws Exception {
        Metric metric = metricsService.getMetrics().get(metricName);
        if (metric == null) {
            System.out.println("Couldn't find a metric with name=" + metricName);
            return null;
        }
        // by default pretty printer will use spaces between array values, we change this to linefeeds to make
        // the caller values easier to read.
        DefaultPrettyPrinter defaultPrettyPrinter = new DefaultPrettyPrinter();
        defaultPrettyPrinter = defaultPrettyPrinter.withArrayIndenter(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE);
        String jsonMetric = new ObjectMapper().writer(defaultPrettyPrinter).writeValueAsString(metric);
        System.out.println(jsonMetric);
        return null;
    }
}
