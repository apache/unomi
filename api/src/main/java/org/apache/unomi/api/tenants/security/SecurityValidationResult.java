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
package org.apache.unomi.api.tenants.security;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents the result of a security validation operation.
 * This class contains information about whether the validation was successful,
 * and if not, what errors were encountered.
 */
public class SecurityValidationResult {
    private boolean valid;
    private List<String> errors;
    private String message;

    /**
     * Default constructor that initializes a valid result with no errors.
     */
    public SecurityValidationResult() {
        this.valid = true;
        this.errors = new ArrayList<>();
    }

    /**
     * Gets whether the validation was successful.
     * @return true if validation passed, false otherwise
     */
    public boolean isValid() {
        return valid;
    }

    /**
     * Sets the validation status.
     * @param valid true if validation passed, false otherwise
     */
    public void setValid(boolean valid) {
        this.valid = valid;
    }

    /**
     * Gets the list of validation errors.
     * @return list of error messages
     */
    public List<String> getErrors() {
        return errors;
    }

    /**
     * Sets the list of validation errors.
     * @param errors list of error messages
     */
    public void setErrors(List<String> errors) {
        this.errors = errors;
    }

    /**
     * Adds an error message to the result.
     * @param error the error message to add
     */
    public void addError(String error) {
        this.valid = false;
        this.errors.add(error);
    }

    /**
     * Gets the general message associated with the validation result.
     * @return the message
     */
    public String getMessage() {
        return message;
    }

    /**
     * Sets the general message associated with the validation result.
     * @param message the message to set
     */
    public void setMessage(String message) {
        this.message = message;
    }
} 