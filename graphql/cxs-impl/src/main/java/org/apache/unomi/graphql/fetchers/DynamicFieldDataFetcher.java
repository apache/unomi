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
import org.apache.unomi.graphql.schema.PropertyNameTranslator;

public abstract class DynamicFieldDataFetcher<T> implements DataFetcher<T> {

    protected String fieldName;

    protected String valueTypeId;

    public DynamicFieldDataFetcher(String fieldName, String valueTypeId) {
        this.fieldName = PropertyNameTranslator.translateFromGraphQLToUnomi(fieldName);
        this.valueTypeId = valueTypeId;
    }

    public String getFieldName() {
        return fieldName;
    }

    public String getValueTypeId() {
        return valueTypeId;
    }
}
