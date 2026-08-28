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
 * Exception thrown when profile import line data cannot be parsed or converted during import processing.
 * Indicates issues with CSV structure, column mapping, or property value conversion on an import line.
 *
 * <p>Common scenarios where this exception is thrown:
 * <ul>
 *   <li>Invalid CSV format or column count mismatch on an import line</li>
 *   <li>Missing required profile fields in the mapping</li>
 *   <li>Property value conversion failures (e.g. unsupported type for a mapped field)</li>
 *   <li>Malformed multi-value fields</li>
 *   <li>Empty lines in import files</li>
 * </ul>
 *
 * <p>Usage in Unomi:
 * <ul>
 *   <li>Thrown by import line processors (e.g. {@code LineSplitProcessor})</li>
 *   <li>Handled by import route error handlers</li>
 * </ul>
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
