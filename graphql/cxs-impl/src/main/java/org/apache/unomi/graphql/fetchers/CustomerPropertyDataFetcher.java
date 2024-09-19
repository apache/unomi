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

import graphql.schema.DataFetchingEnvironment;
import org.apache.unomi.graphql.types.output.CDPEventInterface;
import org.apache.unomi.graphql.types.output.CDPProfileInterface;

public class CustomerPropertyDataFetcher extends DynamicFieldDataFetcher<Object> {

    public CustomerPropertyDataFetcher(String propertyName, String valueTypeId) {
        super(propertyName, valueTypeId);
    }

    @Override
    public Object get(final DataFetchingEnvironment environment) {
        final Object source = environment.getSource();

        Object propertyValue = null;
        if (source instanceof CDPProfileInterface) {
            propertyValue = ((CDPProfileInterface) source).getProperty(fieldName);
        } else if (source instanceof CDPEventInterface) {
            propertyValue = ((CDPEventInterface) source).getProperty(fieldName);
        }

        return inflateType(propertyValue);
    }

}
