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

package org.apache.unomi.didvc.api;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A W3C DID Core conformant DID document (as published for {@code did:web}).
 * Serialized directly as the DID-document JSON served at the DID's well-known
 * location.
 */
public class DidDocumentData {

    private List<String> context;
    private String id;
    private List<VerificationMethod> verificationMethod;
    private List<String> assertionMethod;
    private List<Service> service;

    @JsonProperty("@context")
    public List<String> getContext() {
        return context;
    }

    @JsonProperty("@context")
    public void setContext(List<String> context) {
        this.context = context;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public List<VerificationMethod> getVerificationMethod() {
        return verificationMethod;
    }

    public void setVerificationMethod(List<VerificationMethod> verificationMethod) {
        this.verificationMethod = verificationMethod;
    }

    public List<String> getAssertionMethod() {
        return assertionMethod;
    }

    public void setAssertionMethod(List<String> assertionMethod) {
        this.assertionMethod = assertionMethod;
    }

    public List<Service> getService() {
        return service;
    }

    public void setService(List<Service> service) {
        this.service = service;
    }

    /**
     * Adds a verification method to the document and lists it as an assertion
     * method. Convenience helper used during creation and rotation.
     *
     * @param method the verification method
     */
    public void addVerificationMethod(VerificationMethod method) {
        if (verificationMethod == null) {
            verificationMethod = new ArrayList<>();
        }
        verificationMethod.add(method);
        if (assertionMethod == null) {
            assertionMethod = new ArrayList<>();
        }
        assertionMethod.add(method.getId());
    }

    /**
     * A DID verification method with an embedded public JWK.
     */
    public static class VerificationMethod {
        private String id;
        private String type;
        private String controller;
        private Map<String, Object> publicKeyJwk;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getController() {
            return controller;
        }

        public void setController(String controller) {
            this.controller = controller;
        }

        public Map<String, Object> getPublicKeyJwk() {
            return publicKeyJwk;
        }

        public void setPublicKeyJwk(Map<String, Object> publicKeyJwk) {
            this.publicKeyJwk = publicKeyJwk;
        }
    }

    /**
     * A DID service endpoint.
     */
    public static class Service {
        private String id;
        private String type;
        private String serviceEndpoint;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getServiceEndpoint() {
            return serviceEndpoint;
        }

        public void setServiceEndpoint(String serviceEndpoint) {
            this.serviceEndpoint = serviceEndpoint;
        }
    }
}
