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

package org.apache.unomi.itests.tools.httpclient;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.HttpClientBuilder;
import org.eclipse.jetty.http.HttpStatus;

import java.io.IOException;

public class HttpClientThatWaitsForUnomi {

    private static final long TIMER = 1000L;
    private static final int MAX_TRIES = 10;

    public static CloseableHttpResponse doRequest(HttpUriRequest request) throws IOException {
        return doRequest(request, -1);
    }

    public static CloseableHttpResponse doRequest(HttpUriRequest request, int expectedStatusCode) throws IOException {
        int count = 0;
        while (true) {
            CloseableHttpResponse response = HttpClientBuilder.create().build().execute(request);
            final int statusCode = response.getStatusLine().getStatusCode();
            if ((expectedStatusCode > 0 && expectedStatusCode == statusCode) || statusCode < HttpStatus.BAD_REQUEST_400) {
                return response;
            }
            if (count++ > MAX_TRIES || statusCode >= HttpStatus.INTERNAL_SERVER_ERROR_500) {
                throw new RuntimeException(String.format("connecting to the server failed %s times with status %s %s", MAX_TRIES, statusCode, response.getStatusLine().getReasonPhrase()));
            }
            try {
                // We should find another way to avoid that sleep here.
                Thread.sleep(TIMER);
            } catch (InterruptedException e) {
                // exit .. it should not happen
                throw new RuntimeException(e);
            }
        }
    }
}
