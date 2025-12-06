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

package org.apache.unomi.api.exceptions;

/**
 * Exception thrown when a segment condition is invalid or cannot be used.
 * <p>
 * This exception is thrown in the following scenarios:
 * <ul>
 *   <li>When a segment is enabled but has no condition (null condition)</li>
 *   <li>When a condition has structural issues that prevent it from being evaluated or queried:
 *       <ul>
 *         <li>Unresolved condition types (missing plugins, invalid type IDs)</li>
 *         <li>Conditions that fail to build queries or evaluate against items</li>
 *       </ul>
 *   </li>
 *   <li>When a condition has parameter validation errors:
 *       <ul>
 *         <li>Invalid parameter types (e.g., String instead of Integer)</li>
 *         <li>Missing required parameters</li>
 *         <li>Invalid parameter values (e.g., values outside allowed ranges)</li>
 *         <li>Exclusive parameter violations</li>
 *       </ul>
 *   </li>
 * </ul>
 * <p>
 * This is a domain-specific exception that allows callers to distinguish segment condition
 * validation errors from general argument validation errors (which would use
 * {@link IllegalArgumentException}).
 *
 * @see org.apache.unomi.api.services.SegmentService#setSegmentDefinition(org.apache.unomi.api.segments.Segment)
 */
public class BadSegmentConditionException extends RuntimeException {
    
    /**
     * Constructs a new BadSegmentConditionException with no detail message.
     */
    public BadSegmentConditionException() {
        super();
    }
    
    /**
     * Constructs a new BadSegmentConditionException with the specified detail message.
     *
     * @param message the detail message explaining why the condition is invalid
     */
    public BadSegmentConditionException(String message) {
        super(message);
    }
    
    /**
     * Constructs a new BadSegmentConditionException with the specified detail message and cause.
     *
     * @param message the detail message explaining why the condition is invalid
     * @param cause the cause of this exception
     */
    public BadSegmentConditionException(String message, Throwable cause) {
        super(message, cause);
    }
}
