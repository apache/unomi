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
package org.apache.unomi.persistence.spi.conditions.datemath;

/**
 * Exception thrown by the {@link DateMathParser} when a malformed date math expression is encountered.
 * <p>
 * Part of the Unomi-internal replacement for prior Elasticsearch utilities, allowing us to keep
 * the same semantics without a direct dependency on Elasticsearch.
 */
public class DateMathParseException extends RuntimeException {
    /**
     * Creates an exception with the given message.
     *
     * @param message the detail message
     */
    public DateMathParseException(String message) {
        super(message);
    }

    /**
     * Creates an exception with message and cause.
     *
     * @param message the detail message
     * @param cause the cause
     */
    public DateMathParseException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates an exception with a formatted message.
     *
     * @param message the message format string
     * @param args format arguments
     */
    public DateMathParseException(String message, Object... args) {
        super(String.format(message, args));
    }

    /**
     * Creates an exception with formatted message and cause.
     *
     * @param message the message format string
     * @param cause the cause
     * @param args format arguments
     */
    public DateMathParseException(String message, Throwable cause, Object... args) {
        super(String.format(message, args), cause);
    }
}
