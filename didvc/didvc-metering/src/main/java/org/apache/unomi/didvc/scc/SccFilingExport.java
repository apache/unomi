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

package org.apache.unomi.didvc.scc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A GBA SCC (Standard Contract) filing export for one data-flow
 * counterparty: the exporter, the importer, the contract reference and
 * purpose, the personal-data-element categories (claim types only —
 * never values) transferred, and the verification records backing each
 * transfer. The field set matches the filing template subset the
 * operator submits with the bilateral filing obligations (FR-D4); the
 * export deliberately carries no personal data.
 */
public class SccFilingExport {

    private String filingDate;
    private String exporter;
    private String importer;
    private String contractReference;
    private String purpose;
    private List<String> dataElements;
    private List<VerificationRecord> verificationRecords;

    /**
     * One verification backing the filing: when the counterparty
     * verified which credential type, and the outcome. No claim values.
     */
    public static class VerificationRecord {
        private String verificationDate;
        private String credentialType;
        private String outcome;

        public String getVerificationDate() {
            return verificationDate;
        }

        public void setVerificationDate(String verificationDate) {
            this.verificationDate = verificationDate;
        }

        public String getCredentialType() {
            return credentialType;
        }

        public void setCredentialType(String credentialType) {
            this.credentialType = credentialType;
        }

        public String getOutcome() {
            return outcome;
        }

        public void setOutcome(String outcome) {
            this.outcome = outcome;
        }
    }

    public String getFilingDate() {
        return filingDate;
    }

    public void setFilingDate(String filingDate) {
        this.filingDate = filingDate;
    }

    public String getExporter() {
        return exporter;
    }

    public void setExporter(String exporter) {
        this.exporter = exporter;
    }

    public String getImporter() {
        return importer;
    }

    public void setImporter(String importer) {
        this.importer = importer;
    }

    public String getContractReference() {
        return contractReference;
    }

    public void setContractReference(String contractReference) {
        this.contractReference = contractReference;
    }

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }

    /**
     * Personal-data-element categories transferred: the claim types the
     * counterparty requested and verified, never the claim values.
     */
    public List<String> getDataElements() {
        return dataElements;
    }

    public void setDataElements(List<String> dataElements) {
        this.dataElements = dataElements;
    }

    public List<VerificationRecord> getVerificationRecords() {
        return verificationRecords;
    }

    public void setVerificationRecords(List<VerificationRecord> verificationRecords) {
        this.verificationRecords = verificationRecords;
    }
}
