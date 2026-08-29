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

package org.apache.unomi.didvc.api.services;

/**
 * The two custodian roles of the split-knowledge workflow (FR-G4):
 * the KYC-evidence custodian holds identity-without-linkage, the
 * credential-operator custodian holds linkage-without-identity — only
 * a legal process compelling both can re-identify a subject.
 */
public enum SplitKnowledgeCustodian {

    /** Holds the KYC evidence: identity without linkage. */
    KYC_CUSTODIAN("kyc-custodian"),

    /** Holds the credential linkage: linkage without identity. */
    OPERATOR_CUSTODIAN("operator-custodian");

    private final String roleName;

    SplitKnowledgeCustodian(String roleName) {
        this.roleName = roleName;
    }

    public String getRoleName() {
        return roleName;
    }
}
