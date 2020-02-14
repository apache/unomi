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
package org.apache.unomi.graphql.fetchers;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import org.apache.unomi.graphql.types.CDP_Profile;
import org.apache.unomi.graphql.types.output.CDPEvent;

public class CustomerPropertyDataFetcher implements DataFetcher<Object> {

    private final String propertyName;

    public CustomerPropertyDataFetcher(String propertyName) {
        this.propertyName = propertyName;
    }

    @Override
    public Object get(final DataFetchingEnvironment environment) {
        final Object source = environment.getSource();

        if (source instanceof CDP_Profile) {
            return ((CDP_Profile) environment.getSource()).getProfile().getProperty(propertyName);
        } else if (source instanceof CDPEvent) {
            return ((CDPEvent) environment.getSource()).getProperties().getProperties().get(propertyName);
        } else {
            return null;
        }
    }

}
