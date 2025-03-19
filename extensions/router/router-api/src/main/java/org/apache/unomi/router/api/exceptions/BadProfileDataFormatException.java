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
package org.apache.unomi.router.api.exceptions;

/**
 * Exception thrown when profile data cannot be properly parsed or formatted during import/export operations.
 * This exception indicates issues with the structure or content of profile data that prevent it from being
 * properly processed by the Unomi router.
 *
 * <p>Common scenarios where this exception is thrown:
 * <ul>
 *   <li>Invalid CSV format in import files</li>
 *   <li>Missing required profile fields</li>
 *   <li>Incorrect data types for profile properties</li>
 *   <li>Malformed multi-value fields</li>
 *   <li>Invalid date formats</li>
 * </ul>
 * </p>
 *
 * <p>Usage in Unomi:
 * <ul>
 *   <li>Thrown by profile import processors</li>
 *   <li>Used in data validation steps</li>
 *   <li>Caught by error handling routes</li>
 * </ul>
 * </p>
 *
 * @see org.apache.unomi.router.api.ProfileToImport
 * @since 1.0
 */
public class BadProfileDataFormatException extends Exception {

    /**
     * Constructs a new exception with {@code null} as its detail message.
     * The cause is not initialized.
     */
    public BadProfileDataFormatException() {
        super();
    }

    /**
     * Constructs a new exception with the specified detail message.
     * The cause is not initialized.
     *
     * @param message the detail message describing the cause of the exception
     */
    public BadProfileDataFormatException(String message) {
        super(message);
    }

    /**
     * Constructs a new exception with the specified detail message and cause.
     *
     * @param message the detail message describing the cause of the exception
     * @param cause the underlying cause of the exception
     */
    public BadProfileDataFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
