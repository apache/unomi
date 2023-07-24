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

package org.elasticsearch.client;

import org.elasticsearch.client.tasks.TaskSubmissionResponse;
import org.elasticsearch.index.reindex.DeleteByQueryRequest;
import org.elasticsearch.index.reindex.UpdateByQueryRequest;

import java.io.IOException;

import static java.util.Collections.emptySet;

/**
 * A custom Rest high level client that provide a way of using Task system on updateByQuery and deleteByQuery,
 * by returning the response immediately (wait_for_completion set to false)
 * see org.elasticsearch.client.RestHighLevelClient for original code.
 */
public class CustomRestHighLevelClient extends RestHighLevelClient {

    public CustomRestHighLevelClient(RestClientBuilder restClientBuilder) {
        super(restClientBuilder);
    }

    /**
     * Executes a delete by query request.
     * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-delete-by-query.html">
     * Delete By Query API on elastic.co</a>
     *
     * @param deleteByQueryRequest the request
     * @param options              the request options (e.g. headers), use {@link RequestOptions#DEFAULT} if nothing needs to be customized
     * @return the response
     */
    public final TaskSubmissionResponse submitDeleteByQuery(DeleteByQueryRequest deleteByQueryRequest, RequestOptions options) throws IOException {
        return performRequestAndParseEntity(
                deleteByQueryRequest, innerDeleteByQueryRequest -> {
                    Request request = RequestConverters.deleteByQuery(innerDeleteByQueryRequest);
                    request.addParameter("wait_for_completion", "false");
                    return request;
                }, options, TaskSubmissionResponse::fromXContent, emptySet()
        );
    }

    /**
     * Executes a update by query request.
     * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-update-by-query.html">
     * Update By Query API on elastic.co</a>
     *
     * @param updateByQueryRequest the request
     * @param options              the request options (e.g. headers), use {@link RequestOptions#DEFAULT} if nothing needs to be customized
     * @return the response
     */
    public final TaskSubmissionResponse submitUpdateByQuery(UpdateByQueryRequest updateByQueryRequest, RequestOptions options) throws IOException {
        return performRequestAndParseEntity(
                updateByQueryRequest, innerUpdateByQueryRequest -> {
                    Request request = RequestConverters.updateByQuery(updateByQueryRequest);
                    request.addParameter("wait_for_completion", "false");
                    return request;
                }, options, TaskSubmissionResponse::fromXContent, emptySet()
        );
    }
}
