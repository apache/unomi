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

import com.google.common.base.Strings;
import graphql.schema.DataFetchingEnvironment;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.query.Query;
import org.apache.unomi.graphql.types.input.CDPOrderByInput;

import java.util.List;
import java.util.stream.Collectors;

public abstract class BaseConnectionDataFetcher<T> extends BaseDataFetcher<T> {

    protected ConnectionParams parseConnectionParams(final DataFetchingEnvironment environment) {
        return ConnectionParams.create()
                .first(parseParam("first", 0, environment))
                .last(parseParam("last", DEFAULT_PAGE_SIZE, environment))
                .after(parseDateParam("after", environment))
                .before(parseDateParam("before", environment))
                .build();
    }

    protected Query buildQuery(final Condition condition, final List<CDPOrderByInput> orderByInputs, final ConnectionParams params) {
        final Query query = new Query();
        query.setCondition(condition);

        if (params != null) {
            query.setOffset(params.getFirst());
            query.setLimit(params.getSize());
        }

        if (orderByInputs != null) {
            final String sortBy = orderByInputs.stream()
                    .map(CDPOrderByInput::asString)
                    .collect(Collectors.joining(","));

            if (!Strings.isNullOrEmpty(sortBy)) {
                query.setSortby(sortBy);
            }
        }

        return query;
    }
}
