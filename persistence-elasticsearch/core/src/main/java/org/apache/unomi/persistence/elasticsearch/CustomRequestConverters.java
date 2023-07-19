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
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.nio.entity.NByteArrayEntity;
import org.apache.lucene.util.BytesRef;
import org.elasticsearch.action.support.ActiveShardCount;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.security.RefreshPolicy;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.SuppressForbidden;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentHelper;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.reindex.AbstractBulkByScrollRequest;
import org.elasticsearch.index.reindex.DeleteByQueryRequest;
import org.elasticsearch.index.reindex.UpdateByQueryRequest;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;

/**
 * This is a custom implementation of org.elasticsearch.client.RequestConverters that adds wait_for_completion
 * for update query.
 */
final class CustomRequestConverters {
    static final XContentType REQUEST_BODY_CONTENT_TYPE = XContentType.JSON;
    static final String REQUEST_PER_SECOND_PARAMETER = "requests_per_second";

    private CustomRequestConverters() {
        // Contains only status utility methods
    }

    static Request updateByQuery(UpdateByQueryRequest updateByQueryRequest) throws IOException {
        String endpoint =
                endpoint(updateByQueryRequest.indices(), updateByQueryRequest.getDocTypes(), "_update_by_query");
        Request request = new Request(HttpPost.METHOD_NAME, endpoint);
        Params params = new Params()
                .withRouting(updateByQueryRequest.getRouting())
                .withPipeline(updateByQueryRequest.getPipeline())
                .withRefresh(updateByQueryRequest.isRefresh())
                .withTimeout(updateByQueryRequest.getTimeout())
                .withWaitForActiveShards(updateByQueryRequest.getWaitForActiveShards())
                .withRequestsPerSecond(updateByQueryRequest.getRequestsPerSecond())
                .withIndicesOptions(updateByQueryRequest.indicesOptions())
                .withWaitForCompletion(false);
        if (updateByQueryRequest.isAbortOnVersionConflict() == false) {
            params.putParam("conflicts", "proceed");
        }
        if (updateByQueryRequest.getBatchSize() != AbstractBulkByScrollRequest.DEFAULT_SCROLL_SIZE) {
            params.putParam("scroll_size", Integer.toString(updateByQueryRequest.getBatchSize()));
        }
        if (updateByQueryRequest.getScrollTime() != AbstractBulkByScrollRequest.DEFAULT_SCROLL_TIMEOUT) {
            params.putParam("scroll", updateByQueryRequest.getScrollTime());
        }
        if (updateByQueryRequest.getMaxDocs() > 0) {
            params.putParam("max_docs", Integer.toString(updateByQueryRequest.getMaxDocs()));
        }
        request.addParameters(params.asMap());
        request.setEntity(createEntity(updateByQueryRequest, REQUEST_BODY_CONTENT_TYPE));
        return request;
    }

    static Request deleteByQuery(DeleteByQueryRequest deleteByQueryRequest) throws IOException {
        String endpoint =
                endpoint(deleteByQueryRequest.indices(), deleteByQueryRequest.getDocTypes(), "_delete_by_query");
        Request request = new Request(HttpPost.METHOD_NAME, endpoint);
        Params params = new Params()
                .withRouting(deleteByQueryRequest.getRouting())
                .withRefresh(deleteByQueryRequest.isRefresh())
                .withTimeout(deleteByQueryRequest.getTimeout())
                .withWaitForActiveShards(deleteByQueryRequest.getWaitForActiveShards())
                .withRequestsPerSecond(deleteByQueryRequest.getRequestsPerSecond())
                .withIndicesOptions(deleteByQueryRequest.indicesOptions())
                .withWaitForCompletion(false);
        if (deleteByQueryRequest.isAbortOnVersionConflict() == false) {
            params.putParam("conflicts", "proceed");
        }
        if (deleteByQueryRequest.getBatchSize() != AbstractBulkByScrollRequest.DEFAULT_SCROLL_SIZE) {
            params.putParam("scroll_size", Integer.toString(deleteByQueryRequest.getBatchSize()));
        }
        if (deleteByQueryRequest.getScrollTime() != AbstractBulkByScrollRequest.DEFAULT_SCROLL_TIMEOUT) {
            params.putParam("scroll", deleteByQueryRequest.getScrollTime());
        }
        if (deleteByQueryRequest.getMaxDocs() > 0) {
            params.putParam("max_docs", Integer.toString(deleteByQueryRequest.getMaxDocs()));
        }
        request.addParameters(params.asMap());
        request.setEntity(createEntity(deleteByQueryRequest, REQUEST_BODY_CONTENT_TYPE));
        return request;
    }

    static HttpEntity createEntity(ToXContent toXContent, XContentType xContentType) throws IOException {
        return createEntity(toXContent, xContentType, ToXContent.EMPTY_PARAMS);
    }

    static HttpEntity createEntity(ToXContent toXContent, XContentType xContentType, ToXContent.Params toXContentParams)
            throws IOException {
        BytesRef source = XContentHelper.toXContent(toXContent, xContentType, toXContentParams, false).toBytesRef();
        return new NByteArrayEntity(source.bytes, source.offset, source.length, createContentType(xContentType));
    }

    static String endpoint(String[] indices, String[] types, String endpoint) {
        return new EndpointBuilder().addCommaSeparatedPathParts(indices).addCommaSeparatedPathParts(types)
                .addPathPartAsIs(endpoint).build();
    }

    /**
     * Returns a {@link ContentType} from a given {@link XContentType}.
     *
     * @param xContentType the {@link XContentType}
     * @return the {@link ContentType}
     */
    @SuppressForbidden(reason = "Only allowed place to convert a XContentType to a ContentType")
    public static ContentType createContentType(final XContentType xContentType) {
        return ContentType.create(xContentType.mediaTypeWithoutParameters(), (Charset) null);
    }

    /**
     * Utility class to help with common parameter names and patterns. Wraps
     * a {@link Request} and adds the parameters to it directly.
     */
    static class Params {
        private final Map<String, String> parameters = new HashMap<>();

        Params() {
        }

        Params putParam(String name, String value) {
            if (Strings.hasLength(value)) {
                parameters.put(name, value);
            }
            return this;
        }

        Params putParam(String key, TimeValue value) {
            if (value != null) {
                return putParam(key, value.getStringRep());
            }
            return this;
        }

        Map<String, String> asMap() {
            return parameters;
        }

        Params withPipeline(String pipeline) {
            return putParam("pipeline", pipeline);
        }

        Params withRefresh(boolean refresh) {
            if (refresh) {
                return withRefreshPolicy(RefreshPolicy.IMMEDIATE);
            }
            return this;
        }

        /**
         * @deprecated If creating a new HLRC ReST API call, use {@link RefreshPolicy}
         * instead of {@link WriteRequest.RefreshPolicy} from the server project
         */
        @Deprecated
        Params withRefreshPolicy(WriteRequest.RefreshPolicy refreshPolicy) {
            if (refreshPolicy != WriteRequest.RefreshPolicy.NONE) {
                return putParam("refresh", refreshPolicy.getValue());
            }
            return this;
        }

        Params withRefreshPolicy(RefreshPolicy refreshPolicy) {
            if (refreshPolicy != RefreshPolicy.NONE) {
                return putParam("refresh", refreshPolicy.getValue());
            }
            return this;
        }

        Params withRequestsPerSecond(float requestsPerSecond) {
            // the default in AbstractBulkByScrollRequest is Float.POSITIVE_INFINITY,
            // but we don't want to add that to the URL parameters, instead we use -1
            if (Float.isFinite(requestsPerSecond)) {
                return putParam(REQUEST_PER_SECOND_PARAMETER, Float.toString(requestsPerSecond));
            } else {
                return putParam(REQUEST_PER_SECOND_PARAMETER, "-1");
            }
        }

        Params withRouting(String routing) {
            return putParam("routing", routing);
        }

        Params withTimeout(TimeValue timeout) {
            return putParam("timeout", timeout);
        }

        Params withWaitForActiveShards(ActiveShardCount activeShardCount) {
            return withWaitForActiveShards(activeShardCount, ActiveShardCount.DEFAULT);
        }

        Params withWaitForActiveShards(ActiveShardCount activeShardCount, ActiveShardCount defaultActiveShardCount) {
            if (activeShardCount != null && activeShardCount != defaultActiveShardCount) {
                return putParam("wait_for_active_shards", activeShardCount.toString().toLowerCase(Locale.ROOT));
            }
            return this;
        }

        Params withIndicesOptions(IndicesOptions indicesOptions) {
            if (indicesOptions != null) {
                withIgnoreUnavailable(indicesOptions.ignoreUnavailable());
                putParam("allow_no_indices", Boolean.toString(indicesOptions.allowNoIndices()));
                String expandWildcards;
                if (indicesOptions.expandWildcardsOpen() == false && indicesOptions.expandWildcardsClosed() == false) {
                    expandWildcards = "none";
                } else {
                    StringJoiner joiner = new StringJoiner(",");
                    if (indicesOptions.expandWildcardsOpen()) {
                        joiner.add("open");
                    }
                    if (indicesOptions.expandWildcardsClosed()) {
                        joiner.add("closed");
                    }
                    expandWildcards = joiner.toString();
                }
                putParam("expand_wildcards", expandWildcards);
                putParam("ignore_throttled", Boolean.toString(indicesOptions.ignoreThrottled()));
            }
            return this;
        }

        Params withIgnoreUnavailable(boolean ignoreUnavailable) {
            // Always explicitly place the ignore_unavailable value.
            putParam("ignore_unavailable", Boolean.toString(ignoreUnavailable));
            return this;
        }

        Params withWaitForCompletion(Boolean waitForCompletion) {
            return putParam("wait_for_completion", waitForCompletion.toString());
        }
    }

    /**
     * Utility class to build request's endpoint given its parts as strings
     */
    static class EndpointBuilder {

        private final StringJoiner joiner = new StringJoiner("/", "/", "");

        EndpointBuilder addPathPart(String... parts) {
            for (String part : parts) {
                if (Strings.hasLength(part)) {
                    joiner.add(encodePart(part));
                }
            }
            return this;
        }

        EndpointBuilder addCommaSeparatedPathParts(String[] parts) {
            addPathPart(String.join(",", parts));
            return this;
        }

        EndpointBuilder addPathPartAsIs(String... parts) {
            for (String part : parts) {
                if (Strings.hasLength(part)) {
                    joiner.add(part);
                }
            }
            return this;
        }

        String build() {
            return joiner.toString();
        }

        private static String encodePart(String pathPart) {
            try {
                //encode each part (e.g. index, type and id) separately before merging them into the path
                //we prepend "/" to the path part to make this path absolute, otherwise there can be issues with
                //paths that start with `-` or contain `:`
                //the authority must be an empty string and not null, else paths that being with slashes could have them
                //misinterpreted as part of the authority.
                URI uri = new URI(null, "", "/" + pathPart, null, null);
                //manually encode any slash that each part may contain
                return uri.getRawPath().substring(1).replaceAll("/", "%2F");
            } catch (URISyntaxException e) {
                throw new IllegalArgumentException("Path part [" + pathPart + "] couldn't be encoded", e);
            }
        }
    }
}

