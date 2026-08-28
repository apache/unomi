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
package org.apache.unomi.router.rest;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

public class PartialContentException extends WebApplicationException {

    private static final long serialVersionUID = -6820343767511628388L;

    /**
     * Construct a new "partial content" exception.
     */
    public PartialContentException() {
        super((Throwable) null, Response.Status.PARTIAL_CONTENT);
    }

    /**
     * Construct a new "partial content" exception.
     *
     * @param message the detail message (which is saved for later retrieval
     *                by the {@link #getMessage()} method).
     */
    public PartialContentException(String message) {
        super(message, (Throwable) null, Response.Status.PARTIAL_CONTENT);
    }

    public PartialContentException(String message, Response response) {
        super(message, (Throwable) null, Response.Status.PARTIAL_CONTENT);
    }

    /**
     * Construct a new "partial content" exception.
     *
     * @param cause the underlying cause of the exception.
     */
    public PartialContentException(Throwable cause) {
        super(cause, Response.Status.PARTIAL_CONTENT);
    }

    /**
     * Construct a new "partial content" exception.
     *
     * @param message the detail message (which is saved for later retrieval
     *                by the {@link #getMessage()} method).
     * @param cause   the underlying cause of the exception.
     */
    public PartialContentException(String message, Throwable cause) {
        super(message, cause, Response.Status.PARTIAL_CONTENT);
    }

    public PartialContentException(Response response, Throwable cause) {
        super(cause, Response.Status.PARTIAL_CONTENT);
    }

    public PartialContentException(String message, Response response, Throwable cause) {
        super(message, cause, Response.Status.PARTIAL_CONTENT);
    }
}
