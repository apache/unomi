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

/**
 * Event types emitted by the DID-VC module. Verification events are emitted on
 * the verifying tenant so that metering and trust-tier segmentation are
 * ordinary Unomi queries.
 */
public final class DidvcEventTypes {

    /**
     * A credential was issued.
     */
    public static final String DIDVC_ISSUED = "didvcIssued";

    /**
     * A credential was revoked.
     */
    public static final String DIDVC_REVOKED = "didvcRevoked";

    /**
     * A verifiable presentation was verified (one event per verification call).
     */
    public static final String DIDVP_VERIFIED = "didvpVerified";

    /**
     * A credential offer was sent to a wallet.
     */
    public static final String DIDVC_OFFER_SENT = "didvcOfferSent";

    private DidvcEventTypes() {
    }
}
