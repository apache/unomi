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
package org.apache.unomi.rest.exception;

/**
 * Exception throw when the request exceed the configured limit size
 */
public class InvalidRequestException extends RuntimeException {

    /**
     * This will be the message return to the client in the body request in case
     */
    private final String responseMessage;

    /**
     * Build an invalid request
     * @param message message in the logs. It contains detailed information
     * @param responseMessage message in the response, sent to the browser, must be vague as possible.
     */
    public InvalidRequestException(String message, String responseMessage) {
        super(message);
        this.responseMessage = responseMessage;
    }

    public String getResponseMessage() {
        return responseMessage;
    }
}
