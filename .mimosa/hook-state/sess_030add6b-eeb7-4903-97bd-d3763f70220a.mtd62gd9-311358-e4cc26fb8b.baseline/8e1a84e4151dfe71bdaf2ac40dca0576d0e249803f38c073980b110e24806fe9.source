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
package org.apache.unomi.sfdc.services;

/**
 * Model object that stores Salesforce session data
 */
public class SFDCSession {
    private String sessionId;
    private String endPoint;
    private String signature;
    private String id;
    private String tokenType;
    private Long issuedAt;
    private Long timeout;

    public SFDCSession(String sessionId, String endPoint, String signature, String id, String tokenType, String issuedAt, Long timeout) {
        this.sessionId = sessionId;
        this.endPoint = endPoint;
        this.signature = signature;
        this.id = id;
        this.tokenType = tokenType;
        this.issuedAt = Long.parseLong(issuedAt) * 1000; // value in in seconds, we convert it to milliseconds
        this.timeout = timeout;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getEndPoint() {
        return endPoint;
    }

    public String getSignature() {
        return signature;
    }

    public String getId() {
        return id;
    }

    public String getTokenType() {
        return tokenType;
    }

    public Long getIssuedAt() {
        return issuedAt;
    }

    public boolean isExpired() {
        if (System.currentTimeMillis() < this.issuedAt + this.timeout) {
            return false;
        }
        return true;
    }
}
