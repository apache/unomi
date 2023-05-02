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

package org.apache.unomi.schema.api;

/**
 * This Exception is throw only when a validation process failed due to unexpected error
 * Or when we can't perform the validation due to missing data or invalid required data
 */
public class ValidationException extends Exception {

    private String eventType;

    public ValidationException(String message) {
        super(message);
    }

    public ValidationException(String message, String eventType) {
        super(message);
        this.eventType = eventType;
    }

    public ValidationException(Throwable throwable) {
        super(throwable);
    }

    public ValidationException(String message, Throwable throwable) {
        super(message, throwable);
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getEventType() {
        return eventType;
    }
}
