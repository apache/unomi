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

package org.apache.unomi.persistence.elasticsearch;

import org.apache.http.HttpEntity;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.ActionRequest;
import org.elasticsearch.action.ActionRequestValidationException;
import org.elasticsearch.client.*;
import org.elasticsearch.client.tasks.TaskSubmissionResponse;
import org.elasticsearch.common.CheckedFunction;
import org.elasticsearch.common.xcontent.DeprecationHandler;
import org.elasticsearch.common.xcontent.NamedXContentRegistry;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.index.reindex.DeleteByQueryRequest;
import org.elasticsearch.index.reindex.UpdateByQueryRequest;
import org.elasticsearch.rest.BytesRestResponse;
import org.elasticsearch.rest.RestStatus;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Set;

import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;

/**
 * Rest high level client wrapper that returns the response immediately (wait_for_completion set to false)
 * see org.elasticsearch.client.RestHighLevelClient for original code.
 */
public class SubmitRestHighLevelClientWrapper {

    private final RestHighLevelClient client;

    public SubmitRestHighLevelClientWrapper(RestHighLevelClient client) throws NoSuchFieldException, IllegalAccessException {
        this.client = client;
    }

    private NamedXContentRegistry getRegistry() {
        // get registry from client using reflexion
        try {
            Field registryField = RestHighLevelClient.class.getDeclaredField("registry");
            registryField.setAccessible(true);
            return (NamedXContentRegistry) registryField.get(client);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
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
    public final TaskSubmissionResponse deleteByQuery(DeleteByQueryRequest deleteByQueryRequest, RequestOptions options) throws IOException {
        return performRequestAndParseEntity(
                deleteByQueryRequest, CustomRequestConverters::deleteByQuery, options, TaskSubmissionResponse::fromXContent, emptySet()
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
    public final TaskSubmissionResponse updateByQuery(UpdateByQueryRequest updateByQueryRequest, RequestOptions options) throws IOException {
        return performRequestAndParseEntity(
                updateByQueryRequest, CustomRequestConverters::updateByQuery, options, TaskSubmissionResponse::fromXContent, emptySet()
        );
    }

    private <Req extends ActionRequest, Resp> Resp performRequestAndParseEntity(Req request,
                                                                                CheckedFunction<Req, Request, IOException> requestConverter,
                                                                                RequestOptions options,
                                                                                CheckedFunction<XContentParser, Resp, IOException> entityParser,
                                                                                Set<Integer> ignores) throws IOException {
        return performRequest(request, requestConverter, options,
                response -> parseEntity(response.getEntity(), entityParser), ignores);
    }

    private <Req extends ActionRequest, Resp> Resp performRequest(Req request,
                                                                  CheckedFunction<Req, Request, IOException> requestConverter,
                                                                  RequestOptions options,
                                                                  CheckedFunction<Response, Resp, IOException> responseConverter,
                                                                  Set<Integer> ignores) throws IOException {
        ActionRequestValidationException validationException = request.validate();
        if (validationException != null && validationException.validationErrors().isEmpty() == false) {
            throw validationException;
        }
        return internalPerformRequest(request, requestConverter, options, responseConverter, ignores);
    }

    private <Req, Resp> Resp internalPerformRequest(Req request,
                                                    CheckedFunction<Req, Request, IOException> requestConverter,
                                                    RequestOptions options,
                                                    CheckedFunction<Response, Resp, IOException> responseConverter,
                                                    Set<Integer> ignores) throws IOException {
        Request req = requestConverter.apply(request);
        req.setOptions(options);
        Response response;
        try {
            response = client.getLowLevelClient().performRequest(req);
        } catch (ResponseException e) {
            if (ignores.contains(e.getResponse().getStatusLine().getStatusCode())) {
                try {
                    return responseConverter.apply(e.getResponse());
                } catch (Exception innerException) {
                    // the exception is ignored as we now try to parse the response as an error.
                    // this covers cases like get where 404 can either be a valid document not found response,
                    // or an error for which parsing is completely different. We try to consider the 404 response as a valid one
                    // first. If parsing of the response breaks, we fall back to parsing it as an error.
                    throw parseResponseException(e);
                }
            }
            throw parseResponseException(e);
        }

        try {
            return responseConverter.apply(response);
        } catch (Exception e) {
            throw new IOException("Unable to parse response body for " + response, e);
        }
    }

    private ElasticsearchStatusException parseResponseException(ResponseException responseException) {
        Response response = responseException.getResponse();
        HttpEntity entity = response.getEntity();
        ElasticsearchStatusException elasticsearchException;
        RestStatus restStatus = RestStatus.fromCode(response.getStatusLine().getStatusCode());

        if (entity == null) {
            elasticsearchException = new ElasticsearchStatusException(
                    responseException.getMessage(), restStatus, responseException);
        } else {
            try {
                elasticsearchException = parseEntity(entity, BytesRestResponse::errorFromXContent);
                elasticsearchException.addSuppressed(responseException);
            } catch (Exception e) {
                elasticsearchException = new ElasticsearchStatusException("Unable to parse response body", restStatus, responseException);
                elasticsearchException.addSuppressed(e);
            }
        }
        return elasticsearchException;
    }

    private <Resp> Resp parseEntity(final HttpEntity entity,
                                    final CheckedFunction<XContentParser, Resp, IOException> entityParser) throws IOException {
        if (entity == null) {
            throw new IllegalStateException("Response body expected but not returned");
        }
        if (entity.getContentType() == null) {
            throw new IllegalStateException("Elasticsearch didn't return the [Content-Type] header, unable to parse response body");
        }
        XContentType xContentType = XContentType.fromMediaTypeOrFormat(entity.getContentType().getValue());
        if (xContentType == null) {
            throw new IllegalStateException("Unsupported Content-Type: " + entity.getContentType().getValue());
        }
        try (XContentParser parser = xContentType.xContent().createParser(getRegistry(), DEPRECATION_HANDLER, entity.getContent())) {
            return entityParser.apply(parser);
        }
    }

    private static final DeprecationHandler DEPRECATION_HANDLER = new DeprecationHandler() {
        @Override
        public void usedDeprecatedName(String usedName, String modernName) {
        }

        @Override
        public void usedDeprecatedField(String usedName, String replacedWith) {
        }
    };

}
