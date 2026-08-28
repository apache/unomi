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
import org.apache.commons.lang3.StringUtils;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.query.Query;
import org.apache.unomi.graphql.types.input.CDPOrderByInput;

import java.util.List;
import java.util.stream.Collectors;

public abstract class BaseConnectionDataFetcher<T> extends BaseDataFetcher<T> {

    protected ConnectionParams parseConnectionParams(final DataFetchingEnvironment environment) {
        return ConnectionParams.create()
                .first(parseParam("first", null, environment))
                .last(parseParam("last", null, environment))
                .after(parseParam("after", null, environment))
                .before(parseParam("before", null, environment))
                .text(parseParam("unomi_text", null, environment))
                .build();
    }

    protected Query buildQuery(final Condition condition, final List<CDPOrderByInput> orderByInputs, final ConnectionParams params) {
        final Query query = new Query();
        query.setCondition(condition);

        if (params != null) {
            query.setOffset(params.getOffset());
            query.setLimit(params.getSize());
            query.setText(params.getText());
        }

        if (orderByInputs != null) {
            final String sortBy = orderByInputs.stream()
                    .map(CDPOrderByInput::asString)
                    .collect(Collectors.joining(","));

            if (StringUtils.isNotEmpty(sortBy)) {
                query.setSortby(sortBy);
            }
        }

        return query;
    }
}
