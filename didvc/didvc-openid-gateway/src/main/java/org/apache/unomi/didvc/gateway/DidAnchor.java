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

package org.apache.unomi.didvc.gateway;

/**
 * One DID-document anchor on a chain: the DID, the hash of the
 * anchored document, the anchoring controller and the anchor time.
 */
public class DidAnchor {

    private final String did;
    private final String documentHash;
    private final String controller;
    private final long timestamp;

    public DidAnchor(String did, String documentHash, String controller, long timestamp) {
        this.did = did;
        this.documentHash = documentHash;
        this.controller = controller;
        this.timestamp = timestamp;
    }

    public String getDid() {
        return did;
    }

    /** Hex-encoded (0x-prefixed) hash of the anchored DID document. */
    public String getDocumentHash() {
        return documentHash;
    }

    public String getController() {
        return controller;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
