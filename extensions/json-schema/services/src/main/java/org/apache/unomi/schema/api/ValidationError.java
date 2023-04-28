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

import com.networknt.schema.ValidationMessage;

import java.io.Serializable;

/**
 * Just a bean wrapping JSON Schema validation messages to avoid exposing the lib classes to other OSGI bundles.
 * (It allows keeping control on the underlying validation system, but also on share valuable error info)
 */
public class ValidationError implements Serializable {

    private transient final ValidationMessage validationMessage;

    public ValidationError(ValidationMessage validationMessage) {
        this.validationMessage = validationMessage;
    }

    public String getError() {
        return validationMessage.getMessage();
    }

    public String toString() {
        return validationMessage.toString();
    }

    public boolean equals(Object o) {
        ValidationError other = (ValidationError) o;
        return validationMessage.equals(other.validationMessage);
    }

    public int hashCode() {
        return validationMessage.hashCode();
    }
}
